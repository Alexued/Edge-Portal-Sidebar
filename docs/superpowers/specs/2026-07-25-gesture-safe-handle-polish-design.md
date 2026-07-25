# Gesture-safe handle polish design

## Problem

The first edge-gesture compatibility fallback works, but its collapsed overlay is too intrusive. On the connected phone, the system Back window is 54 px wide while the sidebar overlay is 92 px wide. The transparent 38 px beyond the system region still belongs to the overlay and can block controls in the underlying application.

The visible grip is also farther inward than necessary because it uses a conservative 24 dp system inset plus a 2 dp margin.

## Approaches

### 1. Custom touchable region — rejected after SDK validation

Keep the existing overlay geometry and expansion animation, but publish a custom touchable region while the rail is collapsed. Android's framework contains an internal-insets mechanism for this, but it is not part of the public application SDK. Using it would require hidden-API reflection and is therefore rejected for a production overlay.

The geometry targets remain useful: move the grip closer using the measured vendor gesture boundary, with a 20 dp compatibility offset, a 0.5 dp visual margin, a 4 dp grip width, and a 48 dp grip height.

### 2. Offset a narrow collapsed window — selected

Use a `6dp × 64dp` collapsed window positioned immediately inside the Back region, then interpolate its edge offset, width, and height into the expanded panel. This uses only public `WindowManager.LayoutParams` fields. The small frame itself becomes the touch target, so there is no transparent corridor that can block the underlying application.

### 3. Visual-only reduction

Move and shrink the painted grip without changing input routing. This improves appearance but leaves the underlying application blocked, so it does not solve the reported problem.

## Behavior

- The compatibility grip sits approximately 20.5 dp from the physical edge instead of 26 dp.
- The collapsed overlay frame is about 6 dp wide and 64 dp tall instead of 33 dp by 116 dp.
- There is no overlay window between the grip and physical edge; that region remains owned by the operating system.
- Normal edge-mode devices keep the existing 28 dp edge touch target.
- Peeking and settle animations continuously move the window offset toward zero while expanding its width and height, so the full panel finishes attached to the physical edge.

## Structure and testing

Add pure geometry helpers for centered grip bounds and edge-offset interpolation. Extend `RailWindowGeometry` with an edge offset consumed by `EdgeShelfService`, keeping window placement explicit and testable.

Unit tests cover left/right mirroring, clipping, invalid sizes, and edge-offset interpolation. Device testing verifies visual distance, application clicks in the area formerly covered by the large overlay, pulling still expands in an application and on the launcher, and Back still works outside the grip.
