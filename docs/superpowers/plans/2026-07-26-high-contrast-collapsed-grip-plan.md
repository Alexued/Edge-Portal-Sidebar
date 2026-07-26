# High-contrast collapsed grip implementation plan

1. Add a dedicated compact-grip outline paint to `EdgeRailView`.
2. Replace the pale compact fill and white outline with graphite fill and cyan outline colors.
3. Bump the application to version 1.7.2 (16).
4. Run unit tests, Lint, debug assembly, and `git diff --check`.
5. Install on `c19561a2`; inspect light-background and launcher screenshots at 0 and 20 dp.
6. Re-test live distance movement, expansion, collapse, Back, persistence, and logcat.
7. Leave the phone at its current 0 dp user-selected distance, then commit and push `main`.

