# Edge Portal Sidebar brand and open-source release design

## Goal

Publish the existing Android application as the public repository `Alexued/Edge-Portal-Sidebar` under the user-facing name **Edge Portal Sidebar / 界枢侧边栏**, with a coherent science-fiction launcher icon and documentation suitable for outside users and contributors.

## Chosen approach

Use a compatibility-preserving brand migration:

- Change only user-facing names, artwork, documentation, version metadata, and repository metadata.
- Keep the package name `com.codex.edgeshelf`, Kotlin class names, DataStore keys, notification channel IDs, intents, and existing `Recordings/EdgeShelf` and `Pictures/EdgeShelf` media locations unchanged.
- Explain those legacy internal identifiers where relevant instead of migrating them. This avoids losing preferences or making existing recordings and screenshots disappear after an upgrade.

Alternatives considered and rejected:

1. A complete internal rename would make the source tree visually consistent but adds migration risk without user-visible benefit.
2. A documentation-only rename would be safest but would leave the launcher, notifications, and accessibility service with the old identity.

## Visual identity

The launcher mark uses a deep-space background, two cold-silver structural rails, and a cyan energy core. The negative space resembles both a narrow tablet window and a shelf emerging from the screen edge. The adaptive, legacy, and Android 13 monochrome resources must express the same silhouette and remain legible at launcher size.

The existing warm off-white and emerald application UI remains unchanged. This release is a focused identity update, not a full interface redesign.

## Public documentation

The README will lead with the outcome and cover:

- main features and interaction;
- Android and Xiaomi/HyperOS compatibility;
- standard wrapper-based build and ADB installation commands without local paths or device serials;
- a permission-purpose table, including the optional accessibility service used only for user-triggered screenshots;
- local data and privacy behavior;
- known platform limitations and troubleshooting;
- an explicit unofficial-project and Xiaomi-trademark disclaimer.

The repository will include Apache License 2.0, contribution guidance, a responsible security-reporting policy, and a separate trademark notice.

## Release and repository transition

- Bump the application to `1.6.0` (`versionCode 11`) because the public identity changes while upgrade compatibility is retained.
- Validate XML/resources with unit tests, Android Lint, a debug build, and `git diff --check`.
- Scan tracked content and Git history for likely credentials, local absolute paths, account details, and device identifiers before exposure.
- Commit and push while the repository is still private.
- Rename the repository to `Edge-Portal-Sidebar`, update the Git remote and description, then make it public only after remote verification.

## Success criteria

1. The installed app and all user-visible system surfaces show the new name.
2. Adaptive, legacy, and themed icons compile and share one recognizable sci-fi design.
3. Existing settings, recordings, screenshots, and upgrade identity remain intact.
4. A new contributor can understand, build, install, and test the project from the public README.
5. No known secret, machine-specific path, or device serial is exposed in the current tree or reachable Git history.
6. GitHub reports the requested repository name, public visibility, Apache-2.0 license, updated description, and the expected default-branch HEAD.
