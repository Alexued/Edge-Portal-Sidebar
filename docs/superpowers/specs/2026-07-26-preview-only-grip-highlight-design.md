# Preview-only grip highlight design

## Context

Version 1.7.2 permanently applies the high-contrast graphite and cyan grip. The requested behavior
is transient: retain the original pale compact grip during normal use, use high contrast only
while the edge-distance slider is actively previewing, and restore pale colors immediately when
the gesture finishes.

## Selected design

Use the existing nullable `EdgeDistancePreview.distanceDp` flow as the single source of truth.
Non-null means distance adjustment is active; null means normal display. The overlay service
keeps a runtime boolean separate from persisted `ShelfSettings` and forwards it to `EdgeRailView`.

`EdgeRailView` invalidates only when this boolean changes. Its compact-grip draw path selects:

- normal: the original alpha-148 pale fill and alpha-94 white 1 dp outline;
- preview: the version 1.7.2 graphite fill and 0.85 dp cyan outline.

No timer, animation, preference, geometry change, or new service intent is added. Slider movement
remains real time. Releasing, cancelling, leaving the settings screen, or a failed persistence
clears the existing preview flow and therefore restores the original color.

## Alternatives rejected

- A fixed timeout can restore color while the user is still dragging or leave it highlighted
  after a short gesture.
- Persisting a highlight flag pollutes durable settings with an ephemeral interaction state.
- Inferring adjustment from rapid geometry changes also highlights programmatic restoration and
  cannot reliably identify the end of a drag.

## Verification

Run all unit tests, Lint, debug assembly, and whitespace checks. On the connected phone capture
the stationary pale grip, an in-progress dark/cyan preview frame, and the pale post-release frame.
Re-test 0-40 dp live movement, persistence, launcher/application expansion, outside collapse,
Back outside the grip, compact dimensions, and logcat.

