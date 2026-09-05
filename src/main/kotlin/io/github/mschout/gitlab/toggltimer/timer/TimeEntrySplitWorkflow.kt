/*
 * Copyright 2026 Michael Schout
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.project.TimeEntry
import io.github.mschout.gitlab.toggltimer.project.TimeEntryRepository
import io.github.mschout.gitlab.toggltimer.toggl.CreateStoppedTimeEntryRequest
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglTimeEntry
import io.github.mschout.gitlab.toggltimer.toggl.UpdateTimeEntryStartRequest
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException

data class SplitTimeEntryCommand(
    val togglId: Long,
    val expectedStart: Instant,
    val expectedStop: Instant,
    val splitOffsetSeconds: Long,
)

data class SplitRunningTimeEntryCommand(
    val togglId: Long,
    val expectedStart: Instant,
    val snapshotEnd: Instant,
    val splitOffsetSeconds: Long,
)

data class RunningTimeEntrySplitSnapshot(
    val togglId: Long,
    val start: Instant,
    val snapshotEnd: Instant,
)

sealed interface RunningTimeEntrySplitPreparation {
  data class Ready(val snapshot: RunningTimeEntrySplitSnapshot) : RunningTimeEntrySplitPreparation

  data class Rejected(val message: String, val currentEntry: TogglTimeEntry?) :
      RunningTimeEntrySplitPreparation
}

data class TimeEntrySplitOperationStatus(
    val pending: Boolean,
    val needsReview: Boolean,
    val message: String,
)

sealed interface SplitTimeEntryOutcome {
  data object Completed : SplitTimeEntryOutcome

  data class Rejected(val message: String) : SplitTimeEntryOutcome

  data class RecoveryPending(val operationId: UUID, val message: String) : SplitTimeEntryOutcome

  data class NeedsReview(val operationId: UUID, val message: String) : SplitTimeEntryOutcome
}

@Service
class TimeEntrySplitWorkflow(
    private val timeEntryRepository: TimeEntryRepository,
    private val operationRepository: TimeEntrySplitOperationRepository,
    private val togglClientFactory: TogglClientFactory,
    private val credentialsService: CurrentUserCredentialsService,
    private val persistenceService: TimeEntrySplitPersistenceService,
    private val clock: Clock,
) {
  fun split(command: SplitTimeEntryCommand): SplitTimeEntryOutcome {
    val userId = credentialsService.currentUserId()
    val entry =
        timeEntryRepository.findByTogglIdAndUserId(command.togglId, userId)
            ?: throw TimeEntryNotFoundException(command.togglId)
    validateDisplayedEntry(entry, command)

    val existing = operationRepository.findByUserIdAndOriginalTogglId(userId, command.togglId)
    if (existing != null) {
      if (
          existing.originalStart != command.expectedStart ||
              existing.originalStop != command.expectedStop ||
              existing.splitAt != command.expectedStart.plusSeconds(command.splitOffsetSeconds)
      ) {
        return SplitTimeEntryOutcome.Rejected(
            "A different split is already being recovered for this entry."
        )
      }
      return resume(existing.id ?: error("Persisted split operation has no ID"), apiKey())
    }

    val client = togglClientFactory.forApiKey(apiKey())
    val remote =
        getTimeEntry(client, command.togglId)
            ?: return SplitTimeEntryOutcome.Rejected("This entry no longer exists in Toggl.")
    val remoteStop =
        remote.stop ?: return SplitTimeEntryOutcome.Rejected("A running entry cannot be split.")
    if (remote.start != command.expectedStart || remoteStop != command.expectedStop) {
      return SplitTimeEntryOutcome.Rejected(
          "This entry changed in Toggl. Close the dialog and try again with the refreshed entry."
      )
    }
    val splitAt = requireValidOffset(remote.start, remoteStop, command.splitOffsetSeconds)
    val operation = createOperation(userId, remote, splitAt)
    return resume(operation.id ?: error("Persisted split operation has no ID"), apiKey())
  }

  fun prepareRunning(togglId: Long): RunningTimeEntrySplitPreparation {
    val userId = credentialsService.currentUserId()
    val localEntry =
        timeEntryRepository.findByTogglIdAndUserId(togglId, userId)
            ?: throw TimeEntryNotFoundException(togglId)
    val current = togglClientFactory.forApiKey(apiKey()).getCurrentTimeEntry()
    if (operationRepository.findByUserIdAndOriginalTogglId(userId, togglId) != null) {
      return RunningTimeEntrySplitPreparation.Rejected(
          "This split is already being reconciled.",
          current,
      )
    }
    if (
        localEntry.stop != null ||
            localEntry.duration >= 0 ||
            current == null ||
            current.id != togglId ||
            current.start != localEntry.start ||
            current.workspaceId != localEntry.workspaceId ||
            current.duration >= 0
    ) {
      return RunningTimeEntrySplitPreparation.Rejected(
          "The running timer changed. Reopen Split from the current timer.",
          current,
      )
    }
    val snapshotEnd = clock.instant()
    if (Duration.between(localEntry.start, snapshotEnd).seconds < 2) {
      return RunningTimeEntrySplitPreparation.Rejected(
          "The timer must run for at least two seconds before it can be split.",
          current,
      )
    }
    return RunningTimeEntrySplitPreparation.Ready(
        RunningTimeEntrySplitSnapshot(togglId, localEntry.start, snapshotEnd)
    )
  }

  fun splitRunning(command: SplitRunningTimeEntryCommand): SplitTimeEntryOutcome {
    val userId = credentialsService.currentUserId()
    val entry =
        timeEntryRepository.findByTogglIdAndUserId(command.togglId, userId)
            ?: throw TimeEntryNotFoundException(command.togglId)
    require(entry.stop == null && entry.duration < 0) { "This entry is no longer running." }
    require(entry.start == command.expectedStart) {
      "The running timer changed. Reopen Split from the current timer."
    }
    require(!command.snapshotEnd.isAfter(clock.instant())) { "The split window is invalid." }
    val splitAt =
        requireValidOffset(command.expectedStart, command.snapshotEnd, command.splitOffsetSeconds)

    val existing = operationRepository.findByUserIdAndOriginalTogglId(userId, command.togglId)
    if (existing != null) {
      if (
          existing.kind != TimeEntrySplitKind.RUNNING ||
              existing.originalStart != command.expectedStart ||
              existing.splitAt != splitAt
      ) {
        return SplitTimeEntryOutcome.Rejected(
            "A different split is already being recovered for this entry."
        )
      }
      return resume(existing.id ?: error("Persisted split operation has no ID"), apiKey())
    }

    val client = togglClientFactory.forApiKey(apiKey())
    val current = client.getCurrentTimeEntry()
    if (
        current == null ||
            current.id != command.togglId ||
            current.start != command.expectedStart ||
            current.workspaceId != entry.workspaceId ||
            current.duration >= 0
    ) {
      return SplitTimeEntryOutcome.Rejected(
          "The running timer changed. Reopen Split from the current timer."
      )
    }
    val operation = createRunningOperation(userId, current, splitAt)
    if (
        operation.kind != TimeEntrySplitKind.RUNNING ||
            operation.originalStart != command.expectedStart ||
            operation.splitAt != splitAt
    ) {
      return SplitTimeEntryOutcome.Rejected(
          "A different split is already being recovered for this entry."
      )
    }
    return resume(operation.id ?: error("Persisted split operation has no ID"), apiKey())
  }

  fun operationStatus(togglId: Long): TimeEntrySplitOperationStatus? {
    val operation =
        operationRepository.findByUserIdAndOriginalTogglId(
            credentialsService.currentUserId(),
            togglId,
        ) ?: return null
    val needsReview = operation.phase == TimeEntrySplitPhase.NEEDS_REVIEW
    return TimeEntrySplitOperationStatus(
        pending = !needsReview,
        needsReview = needsReview,
        message =
            if (needsReview) {
              operation.lastError ?: "This split needs review before it can continue safely."
            } else {
              "Split recovery is in progress."
            },
    )
  }

  fun resume(operationId: UUID, apiKey: String): SplitTimeEntryOutcome {
    val now = clock.instant()
    if (operationRepository.claim(operationId, now, now.plus(LEASE_DURATION)) == 0) {
      return SplitTimeEntryOutcome.RecoveryPending(
          operationId,
          "This split is already being processed. The entry will refresh when it completes.",
      )
    }
    val client = togglClientFactory.forApiKey(apiKey)

    repeat(MAX_TRANSITIONS_PER_RUN) {
      val operation =
          operationRepository.findById(operationId).orElse(null)
              ?: return SplitTimeEntryOutcome.Completed
      when (operation.phase) {
        TimeEntrySplitPhase.READY -> transition(operation, TimeEntrySplitPhase.CREATING_FIRST)
        TimeEntrySplitPhase.CREATING_FIRST -> {
          val outcome = createOrRecoverChild(operation, client, first = true)
          if (outcome != null) return outcome
        }
        TimeEntrySplitPhase.FIRST_CREATED ->
            transition(
                operation,
                if (operation.kind == TimeEntrySplitKind.RUNNING) {
                  TimeEntrySplitPhase.UPDATING_ORIGINAL_START
                } else {
                  TimeEntrySplitPhase.CREATING_SECOND
                },
            )
        TimeEntrySplitPhase.CREATING_SECOND -> {
          val outcome = createOrRecoverChild(operation, client, first = false)
          if (outcome != null) return outcome
        }
        TimeEntrySplitPhase.CHILDREN_CREATED -> {
          val originalStop = requireNotNull(operation.originalStop)
          val original = getTimeEntry(client, operation.originalTogglId)
          if (original == null) {
            transition(operation, TimeEntrySplitPhase.ORIGINAL_DELETED)
          } else if (original.start != operation.originalStart || original.stop != originalStop) {
            return compensate(
                operation,
                client,
                "This entry changed in Toggl while it was being split. The original was preserved.",
            )
          } else {
            transition(operation, TimeEntrySplitPhase.DELETING_ORIGINAL)
          }
        }
        TimeEntrySplitPhase.DELETING_ORIGINAL -> {
          val originalStop = requireNotNull(operation.originalStop)
          val original = getTimeEntry(client, operation.originalTogglId)
          if (
              original != null &&
                  (original.start != operation.originalStart || original.stop != originalStop)
          ) {
            return compensate(
                operation,
                client,
                "This entry changed in Toggl while it was being split. The original was preserved.",
            )
          }
          if (original != null) {
            try {
              client.deleteTimeEntry(operation.workspaceId, operation.originalTogglId)
            } catch (exception: HttpClientErrorException) {
              if (exception.statusCode != HttpStatus.NOT_FOUND) {
                if (exception.statusCode.is4xxClientError) {
                  return compensate(
                      operation,
                      client,
                      "Toggl rejected the split. The original entry was preserved.",
                  )
                }
                return pending(operation, exception)
              }
            } catch (exception: Exception) {
              return pending(operation, exception)
            }
          }
          transition(operation, TimeEntrySplitPhase.ORIGINAL_DELETED)
        }
        TimeEntrySplitPhase.ORIGINAL_DELETED -> {
          val first = operation.firstChildTogglId?.let { getTimeEntry(client, it) }
          val second = operation.secondChildTogglId?.let { getTimeEntry(client, it) }
          if (first == null || second == null) {
            return needsReview(
                operation,
                "The original was deleted, but a replacement entry could not be found in Toggl.",
            )
          }
          return try {
            persistenceService.complete(operationId, first, second)
            SplitTimeEntryOutcome.Completed
          } catch (exception: Exception) {
            pending(operation, exception)
          }
        }
        TimeEntrySplitPhase.UPDATING_ORIGINAL_START -> {
          val outcome = updateOrRecoverRunningOriginal(operation, client)
          if (outcome != null) return outcome
        }
        TimeEntrySplitPhase.ORIGINAL_START_UPDATED -> {
          val reconciled =
              try {
                Pair(
                    operation.firstChildTogglId?.let { getTimeEntry(client, it) },
                    getTimeEntry(client, operation.originalTogglId),
                )
              } catch (exception: Exception) {
                return pending(operation, exception)
              }
          val (first, original) = reconciled
          if (first == null || original == null || original.start != operation.splitAt) {
            return needsReview(
                operation,
                "The running timer start changed, but its split segment could not be reconciled.",
            )
          }
          return try {
            persistenceService.completeRunning(requireNotNull(operation.id), first, original)
            SplitTimeEntryOutcome.Completed
          } catch (exception: Exception) {
            pending(operation, exception)
          }
        }
        TimeEntrySplitPhase.CLEANING_SECOND,
        TimeEntrySplitPhase.CLEANING_FIRST ->
            return compensate(operation, client, operation.lastError ?: "The split was cancelled.")
        TimeEntrySplitPhase.NEEDS_REVIEW ->
            return SplitTimeEntryOutcome.NeedsReview(
                operationId,
                operation.lastError ?: "This split needs review before it can continue safely.",
            )
      }
    }

    val operation = operationRepository.findById(operationId).orElseThrow()
    return pending(operation, IllegalStateException("Split transition limit reached"))
  }

  fun dueOperations(limit: Int): List<TimeEntrySplitOperation> =
      operationRepository.findDue(clock.instant(), PageRequest.of(0, limit))

  private fun validateDisplayedEntry(entry: TimeEntry, command: SplitTimeEntryCommand) {
    val stop = entry.stop ?: throw IllegalArgumentException("A running entry cannot be split")
    require(entry.serverDeletedAt == null) { "A deleted entry cannot be split" }
    require(entry.start == command.expectedStart && stop == command.expectedStop) {
      "This entry changed. Close the dialog and try again."
    }
    requireValidOffset(entry.start, stop, command.splitOffsetSeconds)
  }

  private fun requireValidOffset(start: Instant?, stop: Instant, offset: Long): Instant {
    val requiredStart = requireNotNull(start)
    val durationSeconds = Duration.between(requiredStart, stop).seconds
    require(durationSeconds >= 2) { "This entry is too short to split" }
    require(offset in 1 until durationSeconds) {
      "The split must leave at least one second on each side"
    }
    return requiredStart.plusSeconds(offset)
  }

  private fun createOperation(
      userId: Long,
      remote: TogglTimeEntry,
      splitAt: Instant,
  ): TimeEntrySplitOperation {
    val originalStart = requireNotNull(remote.start)
    val originalStop = requireNotNull(remote.stop)
    val operation =
        TimeEntrySplitOperation(
            userId = userId,
            originalTogglId = requireNotNull(remote.id),
            workspaceId = requireNotNull(remote.workspaceId),
            projectId = remote.projectId,
            taskId = remote.taskId,
            description = remote.description,
            originalStart = originalStart,
            originalStop = originalStop,
            splitAt = splitAt,
            billable = remote.billable ?: false,
            tags = remote.tags.orEmpty(),
            createdWith = remote.createdWith?.takeIf(String::isNotBlank) ?: CREATED_WITH,
            nextAttemptAt = clock.instant(),
        )
    return try {
      operationRepository.saveAndFlush(operation)
    } catch (exception: DataIntegrityViolationException) {
      operationRepository.findByUserIdAndOriginalTogglId(userId, requireNotNull(remote.id))
          ?: throw exception
    }
  }

  private fun createRunningOperation(
      userId: Long,
      remote: TogglTimeEntry,
      splitAt: Instant,
  ): TimeEntrySplitOperation {
    val operation =
        TimeEntrySplitOperation(
            userId = userId,
            originalTogglId = requireNotNull(remote.id),
            workspaceId = requireNotNull(remote.workspaceId),
            projectId = remote.projectId,
            taskId = remote.taskId,
            description = remote.description,
            originalStart = requireNotNull(remote.start),
            originalStop = null,
            splitAt = splitAt,
            billable = remote.billable ?: false,
            tags = remote.tags.orEmpty(),
            createdWith = remote.createdWith?.takeIf(String::isNotBlank) ?: CREATED_WITH,
            kind = TimeEntrySplitKind.RUNNING,
            nextAttemptAt = clock.instant(),
        )
    return try {
      operationRepository.saveAndFlush(operation)
    } catch (exception: DataIntegrityViolationException) {
      operationRepository.findByUserIdAndOriginalTogglId(userId, requireNotNull(remote.id))
          ?: throw exception
    }
  }

  private fun createOrRecoverChild(
      operation: TimeEntrySplitOperation,
      client: TogglClient,
      first: Boolean,
  ): SplitTimeEntryOutcome? {
    val request = childRequest(operation, first)
    val matches =
        try {
          findMatchingChildren(operation, client, request)
        } catch (exception: Exception) {
          return pending(operation, exception)
        }
    if (matches.size > 1) {
      return needsReview(
          operation,
          "Multiple matching replacement entries were found in Toggl. Review them before retrying.",
      )
    }
    val child =
        matches.singleOrNull()
            ?: try {
              client.createStoppedTimeEntry(operation.workspaceId, request)
            } catch (exception: HttpClientErrorException) {
              if (exception.statusCode.is4xxClientError) {
                return compensate(
                    operation,
                    client,
                    "Toggl rejected the split. The original entry was preserved.",
                )
              }
              return pending(operation, exception)
            } catch (exception: Exception) {
              return pending(operation, exception)
            }
    val childId =
        child.id
            ?: return needsReview(operation, "Toggl created an entry without returning its ID.")
    if (first) operation.firstChildTogglId = childId else operation.secondChildTogglId = childId
    transition(
        operation,
        if (first) TimeEntrySplitPhase.FIRST_CREATED else TimeEntrySplitPhase.CHILDREN_CREATED,
    )
    return null
  }

  private fun childRequest(
      operation: TimeEntrySplitOperation,
      first: Boolean,
  ): CreateStoppedTimeEntryRequest {
    val start = if (first) operation.originalStart else operation.splitAt
    val stop = if (first) operation.splitAt else requireNotNull(operation.originalStop)
    return CreateStoppedTimeEntryRequest(
        workspaceId = operation.workspaceId,
        projectId = operation.projectId,
        taskId = operation.taskId,
        start = start,
        stop = stop,
        description = operation.description,
        duration = Duration.between(start, stop).seconds,
        billable = operation.billable,
        tags = operation.tags,
        createdWith = operation.createdWith,
    )
  }

  private fun findMatchingChildren(
      operation: TimeEntrySplitOperation,
      client: TogglClient,
      request: CreateStoppedTimeEntryRequest,
  ): List<TogglTimeEntry> =
      client
          .getTimeEntries(
              startDate = operation.originalStart.minusSeconds(1).toString(),
              endDate = (operation.originalStop ?: operation.splitAt).plusSeconds(1).toString(),
              meta = true,
          )
          .filter { it.matches(request) }

  private fun TogglTimeEntry.matches(request: CreateStoppedTimeEntryRequest): Boolean =
      workspaceId == request.workspaceId &&
          projectId == request.projectId &&
          taskId == request.taskId &&
          start == request.start &&
          stop == request.stop &&
          description == request.description &&
          duration == request.duration &&
          (billable ?: false) == request.billable &&
          tags.orEmpty().toSet() == request.tags.toSet()

  private fun updateOrRecoverRunningOriginal(
      operation: TimeEntrySplitOperation,
      client: TogglClient,
  ): SplitTimeEntryOutcome? {
    val original =
        try {
          getTimeEntry(client, operation.originalTogglId)
        } catch (exception: Exception) {
          return pending(operation, exception)
        } ?: return needsReview(operation, "The running timer no longer exists in Toggl.")
    if (original.start == operation.splitAt) {
      transition(operation, TimeEntrySplitPhase.ORIGINAL_START_UPDATED)
      return null
    }
    if (original.start != operation.originalStart) {
      return needsReview(operation, "The running timer start changed unexpectedly in Toggl.")
    }
    if (original.stop != null || original.duration >= 0) {
      return compensate(
          operation,
          client,
          "The timer was stopped before the split could be applied. The original was preserved.",
      )
    }
    val current =
        try {
          client.getCurrentTimeEntry()
        } catch (exception: Exception) {
          return pending(operation, exception)
        }
    if (current?.id != operation.originalTogglId || current.start != operation.originalStart) {
      return needsReview(operation, "A different timer is now running in Toggl.")
    }
    try {
      val updated =
          client.updateTimeEntryStart(
              workspaceId = operation.workspaceId,
              timeEntryId = operation.originalTogglId,
              request =
                  UpdateTimeEntryStartRequest(
                      workspaceId = operation.workspaceId,
                      start = operation.splitAt,
                  ),
          )
      if (updated.start != operation.splitAt) {
        return needsReview(operation, "Toggl returned an unexpected running timer start.")
      }
      transition(operation, TimeEntrySplitPhase.ORIGINAL_START_UPDATED)
      return null
    } catch (exception: HttpClientErrorException) {
      if (exception.statusCode.is4xxClientError) {
        return compensate(
            operation,
            client,
            "Toggl rejected the running timer split. The original was preserved.",
        )
      }
      return pending(operation, exception)
    } catch (exception: Exception) {
      return pending(operation, exception)
    }
  }

  private fun compensate(
      operation: TimeEntrySplitOperation,
      client: TogglClient,
      message: String,
  ): SplitTimeEntryOutcome {
    operation.lastError = message
    val children = listOfNotNull(operation.secondChildTogglId, operation.firstChildTogglId)
    for ((index, childId) in children.withIndex()) {
      operation.phase =
          if (index == 0 && operation.secondChildTogglId != null)
              TimeEntrySplitPhase.CLEANING_SECOND
          else TimeEntrySplitPhase.CLEANING_FIRST
      operationRepository.saveAndFlush(operation)
      try {
        client.deleteTimeEntry(operation.workspaceId, childId)
      } catch (exception: HttpClientErrorException) {
        if (exception.statusCode != HttpStatus.NOT_FOUND) return pending(operation, exception)
      } catch (exception: Exception) {
        return pending(operation, exception)
      }
    }
    operationRepository.delete(operation)
    return SplitTimeEntryOutcome.Rejected(message)
  }

  private fun transition(operation: TimeEntrySplitOperation, phase: TimeEntrySplitPhase) {
    operation.phase = phase
    operation.lastError = null
    operation.nextAttemptAt = clock.instant()
    operationRepository.saveAndFlush(operation)
  }

  private fun pending(
      operation: TimeEntrySplitOperation,
      exception: Exception,
  ): SplitTimeEntryOutcome.RecoveryPending {
    operation.lastError = exception.message
    operation.nextAttemptAt = clock.instant().plus(RETRY_DELAY)
    operation.leaseUntil = null
    operationRepository.saveAndFlush(operation)
    return SplitTimeEntryOutcome.RecoveryPending(
        requireNotNull(operation.id),
        "Toggl did not confirm the full split. Recovery will continue automatically.",
    )
  }

  private fun needsReview(
      operation: TimeEntrySplitOperation,
      message: String,
  ): SplitTimeEntryOutcome.NeedsReview {
    operation.phase = TimeEntrySplitPhase.NEEDS_REVIEW
    operation.lastError = message
    operation.leaseUntil = null
    operationRepository.saveAndFlush(operation)
    return SplitTimeEntryOutcome.NeedsReview(requireNotNull(operation.id), message)
  }

  private fun getTimeEntry(client: TogglClient, togglId: Long): TogglTimeEntry? =
      try {
        client.getTimeEntry(togglId)
      } catch (exception: HttpClientErrorException) {
        if (exception.statusCode == HttpStatus.NOT_FOUND) null else throw exception
      }

  private fun apiKey(): String = credentialsService.requireTogglApiKey()

  companion object {
    private const val CREATED_WITH = "Gitlab Toggl Timer"
    private const val MAX_TRANSITIONS_PER_RUN = 12
    private val LEASE_DURATION = Duration.ofMinutes(2)
    private val RETRY_DELAY = Duration.ofMinutes(1)
  }
}
