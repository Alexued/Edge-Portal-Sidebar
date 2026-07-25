# Gesture-safe handle polish implementation plan

1. Add pure safe-grip bounds and edge-offset interpolation helpers.
2. Update fallback constants to reduce inset, window width, window height, grip width, and grip height.
3. Extend rail window geometry with an edge offset and apply it in the overlay service.
4. Draw the compatibility grip inside the compact collapsed window.
5. Interpolate offset, width, and height back to the physical edge during expansion.
6. Add unit tests for mirrored, clipped, invalid, and interpolated geometry.
7. Run all unit tests, Android Lint, debug assembly, and whitespace checks.
8. Install over the existing app and verify visual position, click-through, expansion, and Back behavior with ADB.
9. Commit and push the tested fix.
