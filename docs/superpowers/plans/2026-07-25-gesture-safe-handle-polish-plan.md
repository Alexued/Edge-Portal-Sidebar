# Gesture-safe handle polish implementation plan

1. Add pure safe-grip visual and touch-bound geometry helpers.
2. Update fallback constants to reduce inset, margin, width, and height.
3. Draw the compatibility grip from the shared geometry.
4. Register an internal-insets listener that limits the collapsed touchable region to the padded grip bounds.
5. Restore full-frame touch handling for peeking, animation, dragging, and expanded states.
6. Add unit tests for mirrored, clipped, invalid, and padded regions.
7. Run all unit tests, Android Lint, debug assembly, and whitespace checks.
8. Install over the existing app and verify visual position, click-through, expansion, and Back behavior with ADB.
9. Commit and push the tested fix.
