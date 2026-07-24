package com.codex.edgeshelf.data

enum class ShelfSide {
    LEFT,
    RIGHT,
}

enum class ShelfMode {
    RECENT,
    FIXED,
}

data class RecentEntry(
    val instanceKey: AppInstanceKey,
    val lastLaunchedEpochMs: Long,
) {
    /** Transitional convenience for package-oriented diagnostics and UI copy. */
    val packageName: String
        get() = instanceKey.packageName

    constructor(
        packageName: String,
        lastLaunchedEpochMs: Long,
    ) : this(AppInstanceKey.legacy(packageName), lastLaunchedEpochMs)
}

data class ShelfSettings(
    val side: ShelfSide = ShelfSide.RIGHT,
    val verticalFraction: Float = 0.5f,
    val mode: ShelfMode = ShelfMode.RECENT,
    val favorites: List<AppInstanceKey> = emptyList(),
    val pinnedApps: List<AppInstanceKey> = emptyList(),
    val recents: List<RecentEntry> = emptyList(),
    val recordingEnabled: Boolean = true,
    val enabled: Boolean = false,
    val autoStart: Boolean = false,
    val autoHide: Boolean = true,
    val onboardingCompleted: Boolean = false,
)
