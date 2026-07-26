# Preview-only grip highlight implementation plan

1. Add transient preview-active state and an invalidating setter to `EdgeRailView`.
2. Select original pale paints normally and high-contrast paints only during preview.
3. Carry preview-active state beside settings in `EdgeShelfService` without persisting it.
4. Bump to version 1.7.3 (17).
5. Run unit tests, Lint, debug assembly, and `git diff --check`.
6. Install on `c19561a2`; capture before/during/after screenshots and test distance movement.
7. Re-test gestures, persistence, compact frame, recovery, and logcat at the user's 0 dp value.
8. Commit and push `main`.

