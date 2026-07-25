# Edge rail and back gesture coexistence implementation plan

1. Add a pure rail-gesture-exclusion bounds helper with invalid-input handling and a configurable height cap.
2. Add unit tests for normal, disabled, invalid, and capped bounds.
3. In `EdgeRailView`, convert the pure bounds to an Android `Rect` and publish it with `systemGestureExclusionRects`.
4. Refresh exclusions after attachment, resize, visibility change, and rail-state/animation transitions.
5. Run targeted tests, then all unit tests, Lint, and debug assembly.
6. Install the APK on the connected Android phone and repeat the desktop/application ADB swipe comparison.
7. Verify Back still works from an edge location outside the rail's vertical exclusion segment.
8. Update version metadata and public documentation if the behavior passes device verification.
9. Commit and push the tested fix to `main`.
