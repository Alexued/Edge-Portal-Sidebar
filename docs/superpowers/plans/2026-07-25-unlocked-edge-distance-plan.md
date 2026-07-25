# Unlocked edge-distance implementation plan

1. Update pure rail geometry so requested distance, not system inset, determines collapsed offset.
2. Add pure tests for 0, below-threshold, above-threshold, invalid, and animated offsets.
3. Keep compact 10 x 64 dp geometry at 0 dp on affected gesture-navigation devices.
4. Change the settings slider to the full 0-40 dp range and add live below-threshold warning copy.
5. Preserve the system threshold only as UI information and leave preview/persistence flow intact.
6. Bump the application to version 1.7.1 (15) and update README interaction notes.
7. Run all unit tests, Android Lint, debug assembly, and `git diff --check`.
8. Install on `c19561a2` and verify 20, 10, and 0 dp live frames, persistence, compact dimensions,
   launcher/application gestures, recovery, Back behavior, animation, and logcat.
9. Restore the connected phone to 20 dp unless the user has changed it independently during test.
10. Commit and push the tested implementation to `main`.

