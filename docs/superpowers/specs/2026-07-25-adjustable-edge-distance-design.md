# Adjustable edge distance and reliable collapsed grip design

## Context

Version 1.6.2 reduced the gesture-navigation compatibility overlay from approximately
`92 x 319 px` to `16 x 176 px`. This removed the transparent corridor that blocked controls
in underlying applications, but the resulting 6 dp-wide touch window is too difficult to hit
reliably with a finger. Connected-device testing confirms that the rail still opens on the
launcher when an ADB gesture starts exactly inside the window, so the launcher failure is a
hit-target regression rather than a gesture-state or service-lifecycle failure.

The user also needs control over the horizontal distance between the visible collapsed grip
and the physical screen edge, with immediate visual feedback while adjusting it.

## Approaches

### 1. Compact wider hit window with an in-process preview channel — selected

Increase only the collapsed compatibility window from 6 dp to 10 dp while keeping the visible
grip 4 dp wide and 48 dp tall. Add a persisted edge-distance preference and a process-local
preview flow. Slider movement updates the preview flow synchronously, while releasing the
slider persists the final value to DataStore.

This makes the grip materially easier to acquire without restoring the former large transparent
window. It also keeps slider motion independent of DataStore write latency.

### 2. Persist every slider sample

Write every `onValueChange` value directly to DataStore and let the service observe the settings
flow. This has fewer components, but a single drag can enqueue many serialized writes and make
the promised real-time response lag or stutter.

### 3. Separate launcher and application offsets

Place the grip at the physical edge on the launcher and at a safe inset inside applications.
This requires continuous foreground-application detection, depends on usage access or an
optional accessibility service, and causes visible jumps during task transitions. It is rejected
as unreliable and unnecessarily invasive.

## Behavior

- The distance from the physical edge to the outer edge of the visible grip is user-adjustable
  in 1 dp steps.
- The nominal supported range is 0-40 dp.
- On gesture-navigation systems with a known edge conflict, the effective minimum equals the
  system-safe edge inset. On the connected phone this is approximately 20 dp, so the presented
  range is 20-40 dp.
- On devices without the conflict, the presented range is 0-40 dp.
- A missing preference means 0 dp requested distance. Effective geometry still applies the
  current system-safe minimum, so upgrades preserve the existing safe position.
- Dragging the slider moves the collapsed overlay immediately. Releasing it persists the final
  requested distance.
- Changing navigation mode later re-evaluates the safety floor. A saved value is never deleted;
  it is clamped only when computing current effective geometry.
- While the rail expands, its edge offset eases to zero so the full panel finishes attached to
  the physical edge. Collapse eases back to the latest effective distance.
- The compact compatibility window is 10 dp by 64 dp. The painted grip remains 4 dp by 48 dp,
  aligned with the outer side of that window.
- Standard devices keep their existing collapsed edge interaction unless the user explicitly
  selects an inward distance. Any positive effective offset uses the compact 10 dp by 64 dp
  window; a zero offset on a standard device retains the original edge-attached target.

## Components and data flow

### Persisted settings

`ShelfSettings` gains `edgeDistanceDp`. `ShelfStore` owns its preference key, normalization,
read mapping, and final persistence. Normalization accepts finite values in 0-40 dp and falls
back to 0 dp for invalid values.

### Live preview

A small process-local preview holder exposes a nullable distance as a `StateFlow`. Null means
"use persisted settings." The settings screen writes slider samples to this flow and commits
only the final sample. The service combines persisted settings with the preview and sends the
effective settings to `EdgeRailView`.

When a commit completes, the preview is cleared after the persisted value is available. Leaving
the settings screen during an unfinished drag clears the preview, preventing a non-persisted
position from surviving accidentally. Process death also clears preview state by construction.

### Geometry

Pure geometry helpers calculate:

1. the effective collapsed offset as the maximum of the system safety inset and the normalized
   requested distance;
2. the eased offset during panel progress; and
3. the compact grip bounds inside the wider touch window.

`EdgeRailView` publishes the resulting offset in `RailWindowGeometry`. `EdgeShelfService`
continues to apply the geometry only through public `WindowManager.LayoutParams` fields.

### Settings UI

The Behavior card places an "Edge distance" control directly below the left/right selector.
It contains a title, a short explanation, the current effective dp value, and a Material slider.
The displayed lower bound reflects the current device safety floor. If the shelf is disabled,
the control remains editable and persisted but the explanation states that the preview appears
when the shelf is enabled.

## Failure and lifecycle handling

- Non-finite, negative, and over-limit stored or preview values are normalized before use.
- A persistence failure clears the temporary preview so runtime state cannot silently diverge
  from the stored setting.
- If system insets are unavailable to the overlay, the existing affected-device fallback supplies
  the safety floor.
- The service removes preview collection with its existing coroutine scope during shutdown.
- Expanded-panel content, list scrolling, recording, screenshots, launch behavior, side switching,
  and vertical dragging are unchanged.

## Verification

JVM tests cover preference defaults and clamping, effective distance calculation, safety-floor
clamping, interpolation, and left/right compact grip bounds.

Build verification runs all debug unit tests, Android Lint, debug assembly, and `git diff --check`.

Connected-device ADB verification covers:

1. launcher expansion with multiple start points across the wider hit window;
2. application expansion without triggering Back;
3. physical-edge Back outside the grip;
4. click-through outside the 10 dp by 64 dp compact frame;
5. live slider movement at its minimum, middle, and maximum values by checking window frames;
6. persistence after reopening the app and after restarting the overlay service;
7. nonlinear expand and outside-collapse frame progression; and
8. crash and error-log inspection.
