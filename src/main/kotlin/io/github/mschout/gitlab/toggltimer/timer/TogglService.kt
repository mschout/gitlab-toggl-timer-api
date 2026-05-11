package io.github.mschout.gitlab.toggltimer.timer

import io.github.mschout.gitlab.toggltimer.toggl.CreateProjectRequest as CreateTogglProjectRequest
import io.github.mschout.gitlab.toggltimer.toggl.TogglClient
import io.github.mschout.gitlab.toggltimer.toggl.TogglClientFactory
import io.github.mschout.gitlab.toggltimer.toggl.TogglProject
import io.github.mschout.gitlab.toggltimer.user.CurrentUserCredentialsService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class TogglService(
    private val togglClientFactory: TogglClientFactory,
    private val credentialsService: CurrentUserCredentialsService,
) {

  fun findOrCreateProject(
      workspaceId: Long,
      clientId: Long,
      issueNumber: Long,
      issueTitle: String,
  ): TogglProject {
    val client = togglClient()
    val projects = client.getProjects(workspaceId, name = issueNumber.toString())

    logger.info { "Found projects: $projects" }

    return projects
        .firstOrNull { it.name?.startsWith("$issueNumber -") == true }
        ?.also { logger.info { "Found project: $it" } }
        ?: run {
          logger.info { "Project not found in Toggl, creating project" }

          val createProjectRequest =
              CreateTogglProjectRequest(name = "$issueNumber - $issueTitle", clientId = clientId)

          client.createProject(workspaceId, createProjectRequest)
        }
  }

  fun startTimer(project: TogglProject, startTimerRequest: StartTimerRequest): Instant {
    TODO("Not yet implemented")
  }

  private fun togglClient(): TogglClient =
      togglClientFactory.forApiKey(credentialsService.requireTogglApiKey())

  companion object {
    private val PROJECT_COLOR_PALETTE =
        listOf(
            "#ef4444", // red-500
            "#f97316", // orange-500
            "#f59e0b", // amber-500
            "#eab308", // yellow-500
            "#84cc16", // lime-500
            "#22c55e", // green-500
            "#10b981", // emerald-500
            "#14b8a6", // teal-500
            "#06b6d4", // cyan-500
            "#0ea5e9", // sky-500
            "#3b82f6", // blue-500
            "#6366f1", // indigo-500
            "#8b5cf6", // violet-500
            "#a855f7", // purple-500
            "#d946ef", // fuchsia-500
            "#ec4899", // pink-500
            "#f43f5e", // rose-500
        )
  }
}
