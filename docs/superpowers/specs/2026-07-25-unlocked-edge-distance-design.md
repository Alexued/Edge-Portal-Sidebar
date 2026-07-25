# Unlocked edge-distance design

## Context

Version 1.7.0 exposes an adjustable edge distance, but on affected gesture-navigation devices
the slider starts at a forced 20 dp safety minimum. The user wants explicit control in the other
direction: from 20 dp toward 0 dp at the physical edge.

Android gives the system Back gesture priority in approximately the outermost 20 dp on the
connected phone. A third-party application overlay cannot reliably override that priority with
public APIs. The product should therefore permit the requested position while clearly showing
the interaction trade-off instead of silently clamping the user's value.

## Approaches

### 1. Unlocked 0-40 dp range with a live conflict warning — selected

Make the persisted user distance authoritative. The system safety floor remains available to
the UI only as a warning threshold. Values below it are applied and saved normally. While the
slider is in that region, the settings card explains that the system may interpret a pull as
Back and that reopening the main application is the recovery path.

### 2. Edge visual with a separate safe touch target

Draw at 0 dp but leave input at 20 dp. This makes visual and touch positions disagree and is
rejected as misleading.

### 3. Launcher-only physical-edge mode

Use 0 dp on the launcher and 20 dp in applications. This requires unreliable foreground-app
detection, extra permission dependencies, and visible task-switch jumps, so it remains rejected.

## Behavior

- The slider range is always 0-40 dp in 1 dp steps.
- Existing stored values are preserved. The connected phone therefore remains at 20 dp after
  upgrade until the user moves it.
- Moving left from 20 dp toward 0 dp shifts the right-side grip toward the physical right edge.
  The left side mirrors this behavior.
- Slider movement continues to preview synchronously; release persists the final value.
- Values below the device safety threshold show a warning but are never blocked or rewritten.
- At 0 dp on affected gesture-navigation devices, the overlay remains the compact 10 x 64 dp
  window with a 4 x 48 dp painted grip. It must not revert to the old 28 x 116 dp window.
- Standard devices at 0 dp retain the established edge-attached target.
- Expansion still eases from the selected distance to 0 dp, and collapse eases back to the
  selected value.
- If a selected unsafe position cannot receive a pull, the user can launch the main application
  from the launcher and return the slider to 20 dp or above.

## Components

`RailGeometry` changes the collapsed offset calculation so requested distance is authoritative;
system gesture inset is no longer a minimum. `EdgeRailView` separately decides whether compact
compatibility geometry is required, using affected gesture navigation or a positive requested
distance.

`EdgeShelfScreen` exposes the full range, displays the device safety threshold in supporting
copy, and switches to warning copy and warning color while the previewed value is below that
threshold. Persistence and the process-local preview channel remain unchanged.

## Error handling and compatibility

- Stored and preview values still normalize to finite 0-40 dp values.
- Navigation-mode changes update the warning threshold but never mutate the stored distance.
- DataStore failures clear preview and restore the persisted value as in version 1.7.0.
- No hidden API, accessibility gesture injection, foreground-app monitoring, or large transparent
  touch bridge is added.

## Verification

Unit tests cover authoritative requested offsets, zero distance in affected mode, interpolation,
and existing normalization. Build verification runs all debug unit tests, Lint, debug assembly,
and whitespace checks.

Connected-device ADB verification checks live frames at 20, 10, and 0 dp; warning text below
20 dp; persistence across service and application restart; compact dimensions at 0 dp; launcher
and application pull behavior at each tested distance; recovery through the main application;
physical-edge Back outside the grip; animation progression; and crash/error logs.

