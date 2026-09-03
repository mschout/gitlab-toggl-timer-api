# Split Time Entry

## Summary

Add a **Split** action to the right-side menu for completed recent time entries. It opens a
native dialog containing a one-second-step slider, initially positioned at the floor of the
entry's midpoint. Users can drag the slider or enter either an elapsed `HH:MM:SS` duration or an
absolute `HH:MM:SS` clock time. The dialog shows the selected clock time in the user's configured
application time zone and both resulting durations. Confirming the dialog replaces the original
Toggl entry with two new stopped entries and then replaces the original Postgres row with the two
returned entries.

Only completed entries lasting at least two seconds are splittable. Both the rendered controls
and the server enforce that the split leaves at least one second on each side.

## Agreed Product Behavior

- Put **Split** after **Copy description** and before the divider and destructive **Delete** item.
- Hide Split for entries shorter than two seconds. Do not merely disable it.
- Use a native `<dialog>` consistent with the existing project and delete dialogs.
- Use a native range input with `step="1"`, `min="1"`, and
  `max="floor(actual duration in seconds) - 1"`.
- Default `splitOffsetSeconds` to `floor(actual duration in seconds / 2)`. For an odd duration,
  the first half is one second shorter.
- Show the selected wall-clock split time and both child durations live. Format the clock time in
  the user's configured application time zone rather than the browser's zone.
- Support the range input's native mouse, touch, and keyboard controls; keep the live values tied
  to accessible output elements.
- Put a restrained **Enter a time** disclosure below the slider. Opening it reveals an explicit
  **Elapsed** / **Clock time** mode switch and one `HH:MM:SS` input; do not guess which meaning the
  user intended from the value.
- In Elapsed mode, interpret `00:29:00` as 29 minutes after the entry start. Permit durations with
  more than 23 hours for long entries, while requiring two-digit minutes and seconds in `00..59`.
- In Clock time mode, interpret `09:42:00` as 9:42 AM and `21:42:00` as 9:42 PM in the configured
  application time zone. Require the result to fall strictly inside the entry interval. Reject
  clock input for entries spanning 24 hours or a UTC-offset change, where a wall-clock value can
  be missing or ambiguous, and direct the user to Elapsed mode or the slider.
- Keep the slider, manual input, selected clock label, and both duration labels synchronized in
  both directions. Give the slider itself the `splitOffsetSeconds` form name so it remains the
  single submitted source of truth. Switching modes reformats the current
  split point without moving it. An incomplete or invalid manual value does not move the slider
  and disables Split until corrected or the manual editor is closed.
- Label the footer buttons **Cancel** and primary **Split**. Cancel, close, Escape, and backdrop
  dismissal make no changes.
- Submit immediately from Split without another confirmation. Disable the controls and show the
  existing spinner convention while the request is active.
- On success, refresh the initial recent-entry window and totals, matching Delete even when older
  Load More pages had been expanded.
- Continue grouping each child under the date of its own start in the configured time zone. A
  second half beginning after midnight therefore moves to the next day under existing history and
  totals semantics.

## Data Contract

### Dialog view

Extend the recent-entry actions model with a dedicated split view rather than overloading the
delete dialog's `error` and `open` fields. The split view supplies:

- Toggl entry ID.
- Original start and stop instants, submitted back as the interval version shown to the user.
- Duration in whole seconds, legal minimum/maximum offsets, and midpoint offset.
- The configured time-zone ID and interval data needed to translate between an offset and a unique
  wall-clock occurrence without using the browser's local time zone.
- Current offset, status message, and whether the dialog should reopen after an HTMX swap. The
  manual mode, text, and validation state remain browser-local UI state.

The browser submits the named range control's numeric `splitOffsetSeconds` plus the expected
original start and stop. It does not submit the manual text, its mode, or a browser-parsed split timestamp. The server
therefore receives and validates exactly the same split command regardless of whether the offset
came from dragging the slider, typing an elapsed duration, or typing a clock time.

### Toggl client

- Add `GET /me/time_entries/{timeEntryId}` to load the authoritative entry immediately before a
  split and for recovery checks.
- Introduce a dedicated stopped-entry create request instead of using the broad response DTO as a
  write model.
- Each child request copies the current remote workspace, project, task, description, billable,
  tags, and nonblank `createdWith`. Use the existing `Gitlab Toggl Timer` fallback when
  `createdWith` is absent.
- Do not copy the original ID, Toggl user identity, response/audit timestamps, deletion markers,
  or unsupported Toggl metadata such as expenses, sharing, and integration data.
- Set each child's exact start, stop, and duration so `start + duration == stop`.

## Validation and Concurrency

The split service, not the HTML, owns all validation:

1. Resolve the authenticated user and load the original through
   `findByTogglIdAndUserId`; inaccessible entries are 404.
2. Require a completed, nondeleted local entry whose actual `Duration.between(start, stop)` is at
   least two seconds.
3. Require the submitted expected start and stop to match the local interval displayed by the
   dialog.
4. Fetch the entry from Toggl before any mutation. Abort as stale if the remote entry is missing,
   running, or has a different start or stop. Metadata-only changes are allowed and become the
   source copied into the children.
5. Compute the split instant from the authoritative remote start plus the integer offset. Require
   it to fall inclusively between `start + 1 second` and `stop - 1 second`.
6. Fetch the original again immediately before deleting it. If its interval changed during child
   creation, preserve the original, remove known children where safe, and abort as stale.

Never trust the stored `duration` or a client-supplied timestamp when computing child boundaries.

## Durable Replacement Workflow

Toggl's create/create/delete calls cannot be part of a Postgres transaction and Toggl does not
offer a create idempotency key. Use a small durable split-operation state machine to minimize
duplicates and make known states resumable.

Do not add Spring `ApplicationEvent`s or Spring Modulith for this workflow. Plain application
events are not durable, while Modulith's durable publication registry tracks listener delivery
rather than child IDs, ambiguous remote outcomes, compensation, or human-review state. The split
operation row is the single recovery authority. The synchronous request and scheduled recovery
job invoke the same coordinator directly rather than maintaining a second event lifecycle.

### Persistence

Add a Flyway migration and JPA entity/repository for `time_entry_split_operations`. Store:

- A generated operation ID and a unique `(user_id, original_toggl_id)` key.
- The original interval/version, authoritative semantic snapshot, and chosen split instant.
- The exact expected request data for both children.
- Nullable first and second child Toggl IDs.
- A phase that records intent before and observed results after each Toggl mutation, including
  `READY`, `CREATING_FIRST`, `FIRST_CREATED`, `CREATING_SECOND`, `CHILDREN_CREATED`,
  `DELETING_ORIGINAL`, `ORIGINAL_DELETED`, compensation phases, and `NEEDS_REVIEW`.
- Last error/status details, timestamps, and a short processing lease so the request path and
  recovery job cannot execute the same operation concurrently.

Create or find the operation before the first remote mutation. Repeated submissions for the same
original resume the matching operation; a different split point is rejected while an unfinished
operation exists. Remove the operation in the same local transaction that completes the Postgres
replacement. Completed retries are then harmless because the owned original row no longer exists.

### Coordinator sequence

1. Claim the operation with a bounded database lease.
2. Commit `CREATING_FIRST`, create the first child, and commit `FIRST_CREATED` with its returned
   Toggl ID.
3. Commit `CREATING_SECOND`, create the second child, and commit `CHILDREN_CREATED` with its
   returned Toggl ID.
4. Re-fetch and verify the original interval.
5. Commit `DELETING_ORIGINAL`, delete the original from Toggl, and commit
   `ORIGINAL_DELETED`. Treat a verified 404 as already deleted.
6. In one Postgres transaction, upsert both full Toggl responses, delete the owned original row,
   and remove the completed operation.

The original is never deleted until both children are known to exist.

### Failure and recovery rules

- On a definite failure before original deletion, retain the original and compensate by deleting
  known children. Persist each compensation intent/result just like forward progress. Keep the
  operation until cleanup is confirmed; then it can safely restart.
- Do not perform destructive compensation after an ambiguous timeout. Reconcile first.
- Treat `CREATING_FIRST`, `CREATING_SECOND`, and `DELETING_ORIGINAL` as unknown-outcome states on
  recovery. Reconcile the corresponding Toggl resource before repeating the mutation. A crash
  after Toggl accepts a request but before Postgres records its result must not become a blind
  duplicate create or delete.
- Resolve an uncertain original deletion with the new GET endpoint: 404 advances to local
  replacement; an unchanged existing original retries deletion.
- Resolve an uncertain child creation by searching the narrow original interval and matching the
  exact expected child boundaries and supported metadata. Adopt exactly one match, retry creation
  only when no match exists, and move to `NEEDS_REVIEW` when multiple matches make the result
  ambiguous.
- If Toggl replacement succeeds but the local transaction fails, keep the operation at
  `ORIGINAL_DELETED`; recovery re-fetches the known children and retries only the Postgres
  replacement. The ordinary Toggl synchronization job also remains a convergence backstop.
- Never invite a blind Split retry after a partial remote result.

Run the coordinator synchronously for the normal path. Add a scheduled recovery component,
guarded by the existing Toggl-sync scheduling property, that loads unfinished operations and each
owner's Toggl API key, claims operations whose lease is free or expired, and applies the same state
machine. Isolate failures per operation/user as the existing sync job does.

## HTTP and HTMX Behavior

Add `POST /timer/entries/{togglId}/split` with CSRF protection. The controller delegates all
ownership, validation, orchestration, and recovery decisions to a focused split service.

- Success reloads history, returns `timer-index :: recent-entries`, retargets
  `#recent-time-entries`, uses `outerHTML`, and triggers `timeTotalsChanged`.
- Validation or stale-interval failures return the entry-actions fragment with the Split dialog
  reopened at the submitted offset and a specific inline message.
- A determinately unfinished operation says recovery is pending and leaves the dialog open; page
  polling refreshes the history after background completion.
- `NEEDS_REVIEW` explains that overlapping entries must be reviewed before reconciliation is
  retried. The endpoint resumes reconciliation instead of starting another split.
- Unexpected transport failures retain a generic inline fallback in the existing
  `htmx:after:request` handler.

Use delegated JavaScript because recent-entry fragments are replaced dynamically. Mirror the
existing menu-hide, `showModal()`, close/backdrop, loading, and post-swap reopen patterns. Add one
split-dialog state updater that owns slider/manual parsing, conversion, validation, and live
labels. The named slider is both the visual control and submitted offset, avoiding a second hidden
source of truth. Keep Split-specific state and CSS names separate from Delete.

## Implementation Areas

- `toggl/TogglClient.kt` and a dedicated stopped-entry request DTO for fetch/create contracts.
- `timer/TimeEntrySplitOperation.kt`, repository, transactional state persistence, coordinator,
  recovery job, result/error types, and focused unit tests.
- A new Flyway migration after `V9` for durable operation state and indexes.
- `project/TogglSyncService.kt` or a focused transactional replacement service for the atomic
  two-upsert/one-delete local step.
- `timer/TimeEntryHistoryService.kt` view mapping for eligibility, interval, midpoint, and
  configured-zone display data.
- `timer/TimerWebController.kt` for the Split endpoint and HTMX response contract.
- `templates/fragments/time-entry-actions.html`, `templates/timer-index.html`, and
  `static/css/app.css` for the menu action, dialog, slider, live outputs, and delegated behavior.

## Test Plan

### Service and persistence

- Ownership is checked before credentials or Toggl calls; inaccessible IDs return not found.
- Running, deleted, sub-two-second, out-of-range, and stale local/remote intervals fail before
  mutation.
- Exact two-second entries have a single legal split at one second; odd durations use the floor
  midpoint; fractional instants still leave at least one full second on each side.
- Elapsed manual values parse strictly, support hours above 23, enforce the one-second boundaries,
  and update the named slider and labels without changing the submitted contract.
- Clock-time manual values use 24-hour `HH:MM:SS`, resolve in the configured application time
  zone, accept exactly one occurrence inside the interval, and reject missing or ambiguous
  occurrences including daylight-saving repetition and multi-day duplicates.
- Slider movement and mode changes round-trip through both manual representations without moving
  the chosen instant; incomplete/invalid text cannot submit a stale offset.
- Child requests copy every supported field and have exact, consistent start/stop/duration values.
- Call order is create first, create second, re-fetch original, delete original, local replace.
- Each remote failure/timeout point exercises its compensation or reconciliation path.
- Retries reuse recorded child IDs; concurrent request/recovery claims do not duplicate calls.
- Zero, one, and multiple exact-match recovery candidates produce retry, adoption, and
  `NEEDS_REVIEW` respectively.
- A Postgres integration test proves both children plus original deletion commit atomically and
  roll back together on failure.
- Recovery is isolated per operation/user and handles missing credentials without blocking other
  users.

### Web and rendering

- Controller tests cover success retarget/reswap/total headers and dialog-preserving error states.
- Authenticated MVC tests cover CSRF, ownership/not-found behavior, Split visibility, exact
  two-second limits, hidden sub-two-second actions, form values, manual-entry controls, configured
  time-zone data, labels, and endpoint URLs.
- Update exact `RecentTimeEntryView` expectations and controller/security constructor mocks.

### Verification

- Run Spotless, focused split/client/history/controller/security tests, the Postgres integration
  tests, full `./gradlew test`, and `git diff --check`.
- In the live application, verify desktop and mobile menu placement; mouse, touch, keyboard, and
  screen-reader slider behavior; elapsed and clock-time entry; two-way synchronization;
  configured-zone, midnight, multi-day, and daylight-saving validation; Cancel/Escape/backdrop
  behavior; double-submit prevention; successful history/totals refresh; cross-midnight grouping;
  and each recoverable inline status.

## Non-Goals

- Preserving Toggl fields the application does not currently model.
- Changing the existing start-date-based history/totals semantics.
- Preserving expanded Load More pages after a mutation.
- Making Toggl and Postgres globally transactional; the durable state machine provides safe
  resumability within the limits of Toggl's API.
- Adding Spring ApplicationEvents or an event-publication registry for a single workflow and
  consumer. Reconsider that infrastructure if several independent durable workflows or event
  consumers emerge later.
