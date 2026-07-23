# Edge Shelf Recording Deletion Design

## Status

- Date: 2026-07-23
- Status: Approved through the user's standing instruction to use the recommended option
- Scope: Permanently delete one completed Edge Shelf recording from the settings library

## Options Considered

1. Put a visible delete icon beside each play control and confirm before deleting. Selected because
   the action is discoverable, accessible, and protected from accidental taps.
2. Put delete inside a per-row overflow menu. This reduces visible controls but adds an unnecessary
   step for the only secondary row action.
3. Swipe to delete with undo. Rejected because a MediaStore deletion is permanent and cannot offer
   a reliable undo after the underlying audio has been removed.

## User Experience

Each recording row gains a low-emphasis delete icon after the play control. Tapping it opens a
Material confirmation dialog containing the recording time, duration, file size, and a clear
warning that deletion cannot be undone. Cancel closes the dialog without changing data.

Confirming keeps the dialog visible while the delete is running. The confirm action becomes a
progress indicator, duplicate actions are disabled, and the row remains visible until MediaStore
reports success. Success removes the row and closes the dialog. Failure keeps the row and dialog,
shows a concise retry message, and allows another attempt or cancellation.

## Data And State Flow

`RecordingRepository.deleteRecording` receives a `RecordingEntry` that the ViewModel resolved from
its current list. It calls `ContentResolver.delete` on that entry's MediaStore content URI and never
constructs a filesystem path or accepts an arbitrary URI from the UI. A positive row count and an
already-missing row both satisfy the user's requested end state. Exceptions are reported as a
failure and never remove the row optimistically.

`EdgeShelfViewModel` exposes one deletion state containing `deletingId` and `deleteFailedId`.
Before deletion starts it invalidates and cancels any older recording query so a late query cannot
restore the deleted row. If the target is playing or preparing, the playback controller is released
before MediaStore deletion. Work runs on `Dispatchers.IO`; success removes only the exact stable ID,
then triggers a fresh query. Only one deletion can run at a time.

The screen owns the pending confirmation ID because dialog presentation is ephemeral UI state. It
passes only the stable ID back to the ViewModel. If a refresh removes the target externally, the
derived dialog target disappears automatically.

## Permissions And Failure Behavior

No broad storage permission is added. These recordings are normally rows created by Edge Shelf and
can be deleted directly by their owner on Android 10 through Android 16. If ownership changed after
an uninstall, restore, or device migration, Android 10's recoverable permission action or Android
11+'s `MediaStore.createDeleteRequest` system dialog asks the user to authorize that exact URI.
Approval completes or retries the same deletion; rejection keeps the recording visible. Other
provider failures also keep the row and expose retry feedback.

## Verification

- JVM tests cover exact stable-ID removal, order preservation, unknown IDs, and playback-release
  decisions.
- Unit tests, lint, APK assembly, and `git diff --check` remain required gates.
- ADB testing creates a dedicated short recording, verifies cancel leaves it intact, then confirms
  deletion removes both its MediaStore row and file. It also verifies deleting an active item
  releases playback and audio focus, and reopening the settings screen does not restore the row.
- The system-consent branch is verified through unit-level state decisions and code review because
  recordings created by the current installation do not trigger ownership recovery.
- The existing user recordings are not used as destructive test targets.
