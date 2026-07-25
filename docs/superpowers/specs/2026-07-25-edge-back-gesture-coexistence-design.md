# Edge rail and back gesture coexistence design

## Problem

On a vendor-customized Android phone using gesture navigation, the collapsed right-side rail opens correctly on the launcher but not inside an application. The same inward swipe is consumed as Android's back gesture.

ADB reproduction confirms the cause:

- gesture navigation is enabled (`navigation_mode=2`);
- the vendor system owns a 54 px `GestureStubRight` window above third-party application overlays;
- the rail's collapsed touch window is 77 px wide and occupies the same physical edge;
- an identical injected swipe expands the rail on the launcher, but inside Settings it performs Back while the rail stays collapsed.

## Approaches considered

### 1. Scoped system gesture exclusion — selected

Declare only the collapsed rail handle as a system gesture exclusion rectangle through Android's public `View.systemGestureExclusionRects` API. The handle is 116 dp tall, below Android's 200 dp per-edge exclusion limit. The remainder of the left or right edge continues to provide the normal Back gesture.

Benefits: preserves the existing edge-attached visual design and pull interaction, uses a public API, and limits behavioral impact to the exact rail segment.

Risk: a vendor gesture implementation may ignore exclusions from an application-overlay window. This must be verified on the connected device rather than assumed.

### 2. Move the handle inward

Position the visible handle beyond the system gesture inset so it receives touches without an exclusion. This is a robust fallback but makes the handle appear detached and increases interference with application content.

Connected-device verification showed that this vendor system ignores the standard exclusion from a third-party `APPLICATION_OVERLAY`. The compatibility fallback is therefore enabled only for the affected device family using gesture navigation: retain a faint rail at the physical edge, place a narrow primary grip immediately inside the vendor Back region, and connect the two visually with a hairline. The overlay grows only enough to contain this grip, with a strict width cap. Other devices keep the original edge handle and standard exclusion behavior.

### 3. Tap or long-press entry

Add a non-swipe method to expand the rail. This avoids horizontal gesture arbitration but changes the primary interaction and does not satisfy the requested pull behavior.

## Behavior

- While the rail is collapsed, peeking, settling, or being vertically dragged, publish one exclusion rectangle covering its current local bounds, capped at 200 dp high.
- Once the panel is fully expanded, publish no exclusion rectangle. A normal Back swipe remains available outside the panel.
- Publish no exclusion while the rail is hidden for the lock screen or screenshot capture.
- Refresh the rectangle after attachment, size changes, visibility changes, and state transitions so moving the rail or rotating the device cannot leave stale coordinates.
- The implementation applies equally to the selected left or right edge because the overlay window itself is anchored to that edge.
- On the affected vendor device family in gesture-navigation mode, derive the safe grip offset from system-gesture insets when available and use a 24 dp fallback when the system does not report them to an overlay window.

## Structure

Keep platform interaction in `EdgeRailView`. Add a small pure geometry helper that decides whether a valid bounded exclusion region exists; unit-test that helper without Android framework dependencies. `EdgeShelfService` and the gesture state machine require no behavior changes.

## Verification

1. Unit-test disabled, invalid-size, normal-height, and 200 dp capped exclusion bounds.
2. Run the complete unit-test, Lint, and debug-build tasks.
3. Install the debug APK over the current app without clearing data.
4. With gesture navigation enabled, inject the same inward swipe at the rail height on the launcher and inside Android Settings.
5. Confirm both cases expand the overlay window and that Settings remains the resumed activity.
6. Inject a right-edge swipe outside the rail's vertical segment and confirm Android Back still works.
7. If step 5 fails on the target vendor system, implement the inward-handle fallback as a separate compatibility change rather than silently widening the exclusion.
