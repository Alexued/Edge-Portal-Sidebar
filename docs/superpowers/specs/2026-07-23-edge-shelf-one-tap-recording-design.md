# Edge Shelf One-Tap Recording Design

## Status

- Date: 2026-07-23
- Status: Approved through the user's standing instruction to use the recommended option
  without pausing for confirmation
- Scope: A fixed recording control at the top of the expanded edge rail

## User Experience

The expanded rail gains one fixed utility slot above the scrollable app list.

- Idle: a quiet microphone glyph.
- Starting or stopping: the control is visually subdued and ignores repeated taps.
- Recording: a red stop-square and red status dot make the active microphone state explicit.
- Tapping idle starts recording. Tapping recording stops and saves it.
- The application rows scroll underneath the fixed control; the recording control never scrolls
  away.
- Collapsing the rail, opening another app, or locking the display does not stop an active
  recording. The recording notification always offers a Stop action.

The first tap without microphone permission opens Android's runtime permission flow. Granting
permission starts recording immediately. Denial leaves the recorder idle and does not create a
file.

## Options Considered

1. Add a `RecordRow` to the existing application rows. This is the smallest code change, but the
   button scrolls away and state refreshes can interrupt an active drag or fling.
2. Add recording directly to `EdgeShelfService`. This avoids another service, but permanently
   couples the special-use overlay FGS to microphone lifetime and cannot reliably start microphone
   capture from the background on Android 14 and later.
3. Use a fixed header and an independent microphone foreground service. Selected because the
   action stays reachable, application scrolling remains isolated, and Android's microphone FGS
   lifecycle is explicit.

## Components

### Rail Header

`EdgeRailView` owns a fixed one-row recording rectangle and a separate rows viewport. Recording
state updates only invalidate drawing; they do not rebuild rows, reset scroll offset, or update
window geometry.

The expanded height is:

`top padding + recording row + visible application rows + bottom padding`

Visible row capacity is calculated after reserving one item height for the recording header. The
preferred application-row caps remain unchanged, so a large tablet can still show its existing
application capacity when vertical space permits. Hit testing treats the recording header and app
rows as separate targets. A vertical move past touch slop cancels the header tap and hands control
to list scrolling.

### Recording State

An in-process state store exposes `Idle`, `Starting`, `Recording`, `Stopping`, and `Error` through a
`StateFlow`. `RecordingService` is the source of truth. `EdgeShelfService` observes the flow and
forwards it to the current `EdgeRailView` without replacing content rows.

Transitions are serialized on the main thread. Starting and stopping states reject duplicate
actions. Failure publishes `Error`, cleans up, then returns to `Idle`.

### Permission And Android 16 Launch Flow

The manifest declares `RECORD_AUDIO` and `FOREGROUND_SERVICE_MICROPHONE`. `RecordingService` is
non-exported and declares `foregroundServiceType="microphone"`.

Microphone permission is a while-in-use permission. A visible application overlay is not a
complete substitute for a visible Activity when starting a microphone FGS on Android 14 and later.
For an already granted permission, the rail therefore opens a translucent, no-animation
`RecordingLaunchActivity`; its `onPostResume` starts `RecordingService` and immediately finishes.

When permission is missing, `MainActivity` requests it. A successful grant starts
`RecordingService` while the Activity is visible. No recording is ever started from boot, a
broadcast receiver, or a hidden background-only path.

### Recording And Storage

`RecordingService` immediately enters the foreground with the microphone service type and a low
importance ongoing notification. The notification includes an explicit Stop action and elapsed
time.

Audio uses `MediaRecorder` with microphone input, MPEG-4 output, AAC encoding, 44.1 kHz sampling,
and a 128 kbps target bit rate. Files are inserted through `MediaStore.Audio` with:

- name `EdgeShelf_yyyyMMdd_HHmmss_SSS.m4a`;
- MIME type `audio/mp4`;
- relative path `Recordings/EdgeShelf`;
- `IS_PENDING=1` while recording.

After a successful `MediaRecorder.stop()`, the descriptor is closed and `IS_PENDING` becomes zero.
Any prepare, start, stop, permission, storage, or process-shutdown failure releases the recorder,
closes the descriptor, and deletes the pending MediaStore entry so no corrupt or empty recording is
published.

## Visual Direction

- Visual thesis: retain the existing pale utility rail and use red only as a truthful live-recording
  signal.
- Content plan: fixed recording action first, scrollable applications second, existing scrollbar
  scoped to the application viewport.
- Interaction thesis: the header shares the rail's existing scale and fade entrance; state changes
  are immediate and stable, with no endless pulse or continuous redraw.

The idle control uses the same dark neutral ink as existing rail tools. Active recording uses a
small solid red stop-square plus a red ring, not a large colored card, so it is unmistakable without
dominating the application list.

## Failure Behavior

- Permission denied or revoked: remain idle, create no MediaStore row, and show a short user-facing
  message from the visible permission host.
- Microphone unavailable, privacy switch enabled, or another recorder blocks access: clean up the
  pending item, remove the recording notification, and return to idle.
- Recording too short for `MediaRecorder.stop()`: delete the incomplete item.
- Overlay hidden or detached: recording continues; state is restored when the rail returns.
- Process killed: `onDestroy` performs best-effort abort and deletion. The service is
  `START_NOT_STICKY` and never silently resumes capture.

## Verification

JVM tests cover file-name validity, state/action eligibility, header capacity, and the separation
between recording-header and row hit regions.

Build verification runs unit tests, lint, assembly, and `git diff --check`. ADB-only verification on
the Android 16 Xiaomi Pad 7S Pro covers:

- permission denied, granted, and revoked flows;
- microphone FGS type and active notification;
- six start/stop cycles with non-empty, finalized MediaStore audio;
- valid audio duration and decoding;
- rapid repeated taps without duplicate recorders;
- scrolling to the end while the recording header remains fixed;
- collapsing/reopening and launching another app while recording;
- stop cleanup with no remaining microphone client;
- rail framestats while recording and scrolling.
