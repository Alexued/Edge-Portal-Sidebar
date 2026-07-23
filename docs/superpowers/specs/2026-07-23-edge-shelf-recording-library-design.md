# Edge Shelf Recording Library Design

## Status

- Date: 2026-07-23
- Status: Approved through the user's standing instruction to use the recommended option
- Scope: Browse and play completed recordings from the existing settings activity

## User Experience

The settings screen gains a `Recordings` section below local launch history. It is a normal
part of the existing scrolling settings surface, not a separate screen.

- Each completed EdgeShelf recording shows a concise timestamp title, date, duration, and file
  size.
- A play button starts the selected item. The same button pauses or resumes it.
- Starting another item releases the previous player and prepares the new item.
- The active row shows a progress bar and elapsed/total time.
- The card composes the newest 20 rows first and offers `Show more` in 20-row batches, keeping
  playback updates bounded without hiding older recordings.
- A refresh action re-queries MediaStore after a recording has been stopped.
- While microphone recording is starting, active, or being saved, playback controls are disabled
  and any current player is released so the microphone cannot capture speaker playback.
- Loading, empty, read failure, preparing, playback failure, and playback completion have explicit
  states. No destructive delete action is added in this scope.

## Options Considered

1. Query and instantiate `MediaPlayer` directly from Composables. This is small, but makes
   lifecycle cleanup and switching players fragile.
2. Use a repository plus a ViewModel-owned playback controller. Selected because the UI receives
   immutable state, player resources have one owner, and the same controller handles pause,
   switching, completion, and errors.
3. Add Media3/ExoPlayer. This offers streaming and advanced controls, but adds a dependency and
   APK weight for a local AAC/M4A-only use case.

## Data Layer

`RecordingRepository` queries `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` on `Dispatchers.IO`.
It accepts only finalized rows (`IS_PENDING=0`) in `Recordings/EdgeShelf/` whose names use the
`EdgeShelf_*.m4a` convention. Results are sorted newest first and expose the content URI, stable
display label, creation time, duration, and byte size. Missing or malformed rows are skipped.

The repository does not request broad audio-library permission. The app owns the rows it creates,
and the query remains scoped to its recording path and filename prefix.

## Playback Layer

`RecordingPlaybackController` owns at most one `MediaPlayer`. It uses a content URI, asynchronous
prepare, AAC-compatible audio attributes, Android audio focus, a becoming-noisy receiver, and a
main-thread ticker for progress updates. Its
state includes the active recording id, preparing/playing/paused status, position, duration, and
an optional transient error. Completion releases the player and returns to idle. ViewModel cleanup
always releases the player and removes callbacks.

Playback is intentionally foreground-only. Leaving the settings Activity releases the player;
background playback would require a MediaSession, playback service, and notification and is not
part of this feature.

## UI And State Flow

`EdgeShelfViewModel` exposes a recording library state alongside existing shelf settings. It
refreshes on screen resume and through the visible refresh action. The settings Composable renders
one un-nested settings card with dividers between rows, reusing existing typography, spacing,
colors, and button shapes. Playback callbacks never rebuild the app rail or alter shelf settings.

## Failure Behavior

- MediaStore unavailable or query failure: retain the previous list when possible and show a
  retry action.
- A pending or deleted URI: skip it during refresh; playback failure returns to idle and exposes a
  short error message.
- A second play request: release the old player before preparing the new URI.
- Recording starts while a player is active: release playback immediately and keep the controls
  disabled until recording returns to idle.
- Activity/ViewModel destruction: stop progress callbacks and release the player.

## Verification

- JVM tests cover duration, byte-size, and timestamp formatting plus playback state transitions
  that do not require Android media hardware.
- Build gates remain unit tests, lint, assemble, and `git diff --check`.
- ADB verification on the Xiaomi Pad 7S Pro covers: completed recordings appearing in newest-first
  order, empty/loading state, play and pause state, switching between two recordings, progress
  updates, and returning to idle after completion.
