package com.codex.edgeshelf.overlay

import com.codex.edgeshelf.data.ShelfMode
import com.codex.edgeshelf.data.ShelfSide

enum class RailTailRow {
    NONE,
    ADD,
    RECENT_EMPTY,
    LOADING,
}

fun railTailRow(
    mode: ShelfMode,
    appCount: Int,
    contentLoaded: Boolean,
): RailTailRow = when {
    mode == ShelfMode.FIXED -> RailTailRow.ADD
    appCount > 0 -> RailTailRow.NONE
    contentLoaded -> RailTailRow.RECENT_EMPTY
    else -> RailTailRow.LOADING
}

data class RailBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

fun clampVerticalFraction(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f

fun verticalTop(
    verticalFraction: Float,
    screenHeight: Int,
    railHeight: Int,
    topInset: Int,
    bottomInset: Int,
): Int {
    val minTop = topInset.coerceAtLeast(0)
    val maxTop = (screenHeight - bottomInset - railHeight).coerceAtLeast(minTop)
    return (minTop + (maxTop - minTop) * clampVerticalFraction(verticalFraction)).toInt()
}

fun railBounds(
    side: ShelfSide,
    screenWidth: Int,
    top: Int,
    railWidth: Int,
): RailBounds {
    val safeWidth = railWidth.coerceAtLeast(1)
    return if (side == ShelfSide.RIGHT) {
        RailBounds(screenWidth - safeWidth, top, screenWidth, top + safeWidth)
    } else {
        RailBounds(0, top, safeWidth, top + safeWidth)
    }
}

fun visibleRailRowCapacity(
    availableHeight: Int,
    itemHeight: Int,
    verticalPadding: Int,
    preferredMaximum: Int,
): Int {
    val safeItemHeight = itemHeight.coerceAtLeast(1)
    val usableHeight = (availableHeight - verticalPadding.coerceAtLeast(0) * 2)
        .coerceAtLeast(safeItemHeight)
    return (usableHeight / safeItemHeight).coerceIn(1, preferredMaximum.coerceAtLeast(1))
}

fun railContentAlpha(panelProgress: Float, revealThreshold: Float = 0.4f): Int {
    if (!panelProgress.isFinite()) return 0
    val safeThreshold = revealThreshold.coerceIn(0f, 0.99f)
    val normalized = ((panelProgress - safeThreshold) / (1f - safeThreshold)).coerceIn(0f, 1f)
    return (normalized * 255f).toInt()
}
