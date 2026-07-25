# Gesture-safe handle polish design

## Problem

The first edge-gesture compatibility fallback works, but its collapsed overlay is too intrusive. On the connected phone, the system Back window is 54 px wide while the sidebar overlay is 92 px wide. The transparent 38 px beyond the system region still belongs to the overlay and can block controls in the underlying application.

The visible grip is also farther inward than necessary because it uses a conservative 24 dp system inset plus a 2 dp margin.

## Approaches

### 1. Custom touchable region — selected

Keep the existing overlay geometry and expansion animation, but publish a custom touchable region while the rail is collapsed. Only a compact rectangle around the visible safe grip receives touch; the transparent bridge and remaining overlay bounds pass through to the application below. Once the panel starts opening, restore the full window as touchable so scrolling, buttons, and outside-collapse behavior remain unchanged.

Move the grip closer to the edge using the measured vendor gesture boundary: use a 21 dp compatibility inset, a 0.5 dp visual margin, a 4 dp grip width, and a 48 dp grip height. Give it a larger invisible hit target of approximately 10 dp by 64 dp for reliable pulling without reclaiming the full transparent window.

### 2. Offset a separate narrow window

Use a small collapsed window positioned inside the Back region, then animate its `x` offset and width into the expanded panel. This minimizes the window frame itself but couples gesture handling to service-level window geometry and adds transition risk.

### 3. Visual-only reduction

Move and shrink the painted grip without changing input routing. This improves appearance but leaves the underlying application blocked, so it does not solve the reported problem.

## Behavior

- The compatibility grip sits approximately 21.5 dp from the physical edge instead of 26 dp.
- Only the padded grip region is touchable while fully collapsed on affected gesture-navigation systems.
- The transparent area between the grip and physical edge passes taps and vertical gestures to the underlying application, except for the operating system's own Back region.
- Normal edge-mode devices keep the existing 28 dp edge touch target.
- Peeking, animation, dragging, and the fully expanded panel retain the full window touch target.
- Hidden and detached rails publish no custom touch region.

## Structure and testing

Add a pure geometry helper that returns the safe grip bounds and padded touch bounds for either screen side. `EdgeRailView` uses the same geometry for drawing and for `ViewTreeObserver.OnComputeInternalInsetsListener`, preventing visual and input regions from drifting apart.

Unit tests cover left/right mirroring, clipping, padding, invalid sizes, and the reduced collapsed width. Device testing verifies visual distance, application clicks through transparent overlay space, pulling still expands in an application and on the launcher, and Back still works outside the grip.
