# Compact Timer Panel

## Summary

Replace the current multi-row card with a Toggl-inspired compact toolbar that remains visible in
both running and stopped states:

`[editable description] [editable project • client] [elapsed time] [red stop button]`

`[draft description] [project selector] [00:00:00] [primary play button]`

Keep the existing Bootstrap visual language, using the project color and circular red stop control as the distinctive accents. On narrow screens, the description occupies the first row while project, elapsed time, and stop remain grouped below.

## Key Changes

- Extend the running-timer result with its Toggl entry ID and project display metadata. Shadow-sync newly discovered or externally started running entries and their project metadata so the existing editing services can operate on them.
- Build a unified running-timer view containing the reusable description editor and project picker models. Use it for initial page loads and both GET/POST start-timer responses.
- Replace the current heading, explanatory copy, labeled fields, and delayed description-on-stop behavior with the compact toolbar. The stop control remains an HTMX `POST /timer/stop`, is keyboard accessible, and stays at the far right.
- Reuse the recent-entry interactions unchanged: click/F2 to edit the description, Enter to save, Escape or blur to cancel; click the project to open the searchable project dialog and save the selection immediately.
- Update the shared JavaScript so project-color changes apply to either a history row or the running toolbar, while preserving elapsed-time updates across HTMX swaps.

## Interfaces and Failure Behavior

- No new HTTP endpoints; reuse the existing description, project-search, project-update, and stop routes.
- Add the Toggl ID and required project presentation fields to the internal running-timer result/view types.
- Continue hiding the panel when Toggl cannot return a valid running entry. Existing inline retry errors remain for description/project update failures, and stopping still refreshes recent entries.

## Test Plan

- Expand Toggl service tests for entry IDs, shadow synchronization, project metadata, externally started timers, project-less timers, and project-fetch failures.
- Update controller tests for the unified running-timer model on page load and start responses, plus absent/invalid timer behavior.
- Add rendered MVC assertions for the compact toolbar, reusable editor URLs, project dialog, elapsed-time data, and clickable stop control.
- Run the focused timer tests, then `./gradlew test`.
- Verify desktop and mobile layouts plus keyboard editing, project selection, elapsed-time ticking, HTMX swaps, and stop behavior.

## Assumptions

- “Same way the recent entries can” means Enter saves descriptions immediately; stopping no longer implicitly submits an unsaved description.
- Toggl’s tag, billable, and overflow-menu controls are not added.
- The start form and recent-entry ledger remain otherwise unchanged.
