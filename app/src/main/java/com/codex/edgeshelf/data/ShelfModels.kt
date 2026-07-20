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
    val packageName: String,
    val lastLaunchedEpochMs: Long,
)

data class ShelfSettings(
    val side: ShelfSide = ShelfSide.RIGHT,
    val verticalFraction: Float = 0.5f,
    val mode: ShelfMode = ShelfMode.RECENT,
    val favorites: List<String> = emptyList(),
    val recents: List<RecentEntry> = emptyList(),
    val enabled: Boolean = false,
    val autoStart: Boolean = false,
    val autoHide: Boolean = true,
    val onboardingCompleted: Boolean = false,
)
