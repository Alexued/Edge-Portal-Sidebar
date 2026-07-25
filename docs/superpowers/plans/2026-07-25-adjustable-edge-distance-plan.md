# Adjustable edge distance implementation plan

## Goal

Restore reliable finger acquisition of the collapsed rail on the launcher without restoring the
large transparent overlay, and add a persisted edge-distance slider whose preview moves the rail
in real time.

## 1. Persist and normalize the requested distance

Files:

- `app/src/main/java/com/codex/edgeshelf/data/ShelfModels.kt`
- `app/src/main/java/com/codex/edgeshelf/data/ShelfStore.kt`
- `app/src/test/java/com/codex/edgeshelf/data/ShelfStoreTest.kt`

Add `edgeDistanceDp` to `ShelfSettings`, a DataStore float key and setter, and a pure normalizer
with a 0-40 dp range and 0 dp fallback. Test defaults, stored-value mapping, finite clamping, and
non-finite fallback.

## 2. Add a live preview channel

Files:

- Create `app/src/main/java/com/codex/edgeshelf/data/EdgeDistancePreview.kt`
- Create `app/src/test/java/com/codex/edgeshelf/data/EdgeDistancePreviewTest.kt`

Expose a process-local nullable `StateFlow<Float>` with normalized update and expected-value
clear operations. Test synchronous updates, normalization, and protection against an older
commit clearing a newer preview.

## 3. Share gesture compatibility and extend pure geometry

Files:

- Create `app/src/main/java/com/codex/edgeshelf/overlay/GestureNavigationCompatibility.kt`
- Update `app/src/main/java/com/codex/edgeshelf/overlay/RailGeometry.kt`
- Update `app/src/test/java/com/codex/edgeshelf/overlay/RailGeometryTest.kt`

Move affected-navigation detection and the 20 dp safety constants out of `EdgeRailView`. Extend
edge-offset geometry to take both the system safety inset and requested distance. Test clamping,
NaN progress, user distances above the safety floor, and expansion interpolation.

## 4. Apply compact geometry and improve launcher acquisition

Files:

- `app/src/main/java/com/codex/edgeshelf/overlay/EdgeRailView.kt`

Increase only the inset collapsed window width from 6 dp to 10 dp. Use compact 10 x 64 dp
geometry whenever the effective offset is positive, keep the painted grip at 4 x 48 dp, and
retain the original 28 x 116 dp edge target when the effective offset is zero. Drive drawing,
gesture exclusion, expansion, and collapse from the same effective-offset calculation.

## 5. Feed preview settings to the running service

Files:

- `app/src/main/java/com/codex/edgeshelf/service/EdgeShelfService.kt`

Combine persisted settings with the process preview flow. Preview-only distance changes update
rail geometry but do not refresh applications because `affectsShelfContent` ignores the new
field. Preserve service shutdown and configuration-change behavior.

## 6. Add the real-time settings control

Files:

- `app/src/main/java/com/codex/edgeshelf/ui/EdgeShelfViewModel.kt`
- `app/src/main/java/com/codex/edgeshelf/ui/EdgeShelfScreen.kt`
- `app/src/main/java/com/codex/edgeshelf/MainActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`

Expose the current navigation safety floor in UI state. Add preview, commit, and clear callbacks
to the ViewModel. Add a 1 dp stepped Material slider below the side selector, display the
effective value and safety floor, update preview on movement, persist on release, and clear an
unfinished preview on lifecycle exit.

## 7. Version and documentation

Files:

- `app/build.gradle.kts`
- `README.md`

Bump to version 1.7.0 (14). Document the adjustable distance, live preview, safety minimum, and
compact hit target without adding vendor names to user-facing product copy.

## 8. Automated verification

Run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --stacktrace --console=plain
git diff --check
```

Fix every regression before installing.

## 9. Connected-device ADB verification

1. Install the 1.7.0 debug APK on `c19561a2`.
2. Verify the default current-device collapsed frame is approximately 27 x 176 px at the 20 dp
   safety floor.
3. From the launcher, expand from the outer, center, and inner parts of the wider hit window.
4. In Android Settings, expand without triggering Back and verify physical-edge Back elsewhere.
5. Tap immediately outside the compact frame to prove click-through.
6. Move the slider to 20, 30, and 40 dp with ADB and confirm window `x` changes immediately while
   the drag is active.
7. Reopen the app and restart the service to verify the final value persists.
8. Sample expand and outside-collapse frames and inspect logcat for crashes or app errors.

## 10. Delivery

Commit the tested implementation, push `main`, and report the selected approach, exact device
frames, build results, launcher/application gesture results, live-preview results, and commit id.

