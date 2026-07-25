package com.codex.edgeshelf.overlay

import com.codex.edgeshelf.data.ShelfSide
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

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

fun railGestureExclusionBounds(
    side: ShelfSide,
    viewWidth: Int,
    viewHeight: Int,
    maximumWidth: Int,
    maximumHeight: Int,
    enabled: Boolean,
): RailBounds? {
    if (!enabled || viewWidth <= 0 || viewHeight <= 0 || maximumWidth <= 0 || maximumHeight <= 0) {
        return null
    }
    val exclusionWidth = viewWidth.coerceAtMost(maximumWidth)
    val exclusionHeight = viewHeight.coerceAtMost(maximumHeight)
    val left = if (side == ShelfSide.RIGHT) viewWidth - exclusionWidth else 0
    return RailBounds(
        left = left,
        top = 0,
        right = left + exclusionWidth,
        bottom = exclusionHeight,
    )
}

fun gestureSafeGripBounds(
    side: ShelfSide,
    viewWidth: Int,
    viewHeight: Int,
    gripWidth: Int,
    gripHeight: Int,
    edgeMargin: Int,
): RailBounds? {
    if (viewWidth <= 0 || viewHeight <= 0 ||
        gripWidth <= 0 || gripHeight <= 0 || edgeMargin < 0
    ) {
        return null
    }
    val safeGripWidth = gripWidth.coerceAtMost(viewWidth)
    val safeGripHeight = gripHeight.coerceAtMost(viewHeight)
    val top = (viewHeight - safeGripHeight) / 2
    val bottom = top + safeGripHeight
    return if (side == ShelfSide.RIGHT) {
        val right = (viewWidth - edgeMargin).coerceIn(0, viewWidth)
        val left = (right - safeGripWidth).coerceAtLeast(0)
        if (right <= left) return null
        RailBounds(left, top, right, bottom)
    } else {
        val left = edgeMargin.coerceIn(0, viewWidth)
        val right = (left + safeGripWidth).coerceAtMost(viewWidth)
        if (right <= left) return null
        RailBounds(left, top, right, bottom)
    }
}

fun effectiveRailEdgeOffset(
    systemGestureInset: Int,
    requestedEdgeDistance: Int,
): Int = max(
    systemGestureInset.coerceAtLeast(0),
    requestedEdgeDistance.coerceAtLeast(0),
)

fun railEdgeOffset(
    systemGestureInset: Int,
    requestedEdgeDistance: Int,
    panelProgress: Float,
): Int {
    val collapsedOffset = effectiveRailEdgeOffset(systemGestureInset, requestedEdgeDistance)
    if (collapsedOffset <= 0) return 0
    val progress = if (panelProgress.isFinite()) panelProgress.coerceIn(0f, 1f) else 0f
    return (collapsedOffset * (1f - progress)).roundToInt()
}

fun visibleRailRowCapacity(
    availableHeight: Int,
    itemHeight: Int,
    verticalPadding: Int,
    preferredMaximum: Int,
    reservedHeight: Int = 0,
): Int {
    val safeItemHeight = itemHeight.coerceAtLeast(1)
    val usableHeight = (
        availableHeight - reservedHeight.coerceAtLeast(0) -
            verticalPadding.coerceAtLeast(0) * 2
        )
        .coerceAtLeast(safeItemHeight)
    return (usableHeight / safeItemHeight).coerceIn(1, preferredMaximum.coerceAtLeast(1))
}

fun railHeaderContains(
    x: Float,
    y: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): Boolean {
    if (!x.isFinite() || !y.isFinite() || !left.isFinite() || !top.isFinite() ||
        !right.isFinite() || !bottom.isFinite() || right <= left || bottom <= top
    ) {
        return false
    }
    return x >= left && x < right && y >= top && y < bottom
}

fun maxRailScrollOffset(
    rowCount: Int,
    visibleRowCapacity: Int,
    itemHeight: Float,
): Float {
    if (!itemHeight.isFinite() || itemHeight <= 0f) return 0f
    val hiddenRows = rowCount.coerceAtLeast(0) - visibleRowCapacity.coerceAtLeast(1)
    return max(0f, hiddenRows * itemHeight)
}

fun clampRailScrollOffset(
    scrollOffset: Float,
    maximumOffset: Float,
): Float {
    if (!scrollOffset.isFinite() || !maximumOffset.isFinite() || maximumOffset <= 0f) return 0f
    return scrollOffset.coerceIn(0f, maximumOffset)
}

fun railRowIndexAt(
    localY: Float,
    viewportHeight: Float,
    scrollOffset: Float,
    itemHeight: Float,
    rowCount: Int,
): Int {
    if (!localY.isFinite() || !viewportHeight.isFinite() ||
        !scrollOffset.isFinite() || !itemHeight.isFinite() ||
        localY < 0f || localY >= viewportHeight || itemHeight <= 0f || rowCount <= 0
    ) {
        return -1
    }
    val index = floor((localY + scrollOffset.coerceAtLeast(0f)) / itemHeight).toInt()
    return index.takeIf { it in 0 until rowCount } ?: -1
}

fun visibleRailRowRange(
    scrollOffset: Float,
    viewportHeight: Float,
    itemHeight: Float,
    rowCount: Int,
): IntRange {
    if (!scrollOffset.isFinite() || !viewportHeight.isFinite() ||
        !itemHeight.isFinite() || viewportHeight <= 0f || itemHeight <= 0f || rowCount <= 0
    ) {
        return IntRange.EMPTY
    }
    val safeOffset = scrollOffset.coerceAtLeast(0f)
    val first = floor(safeOffset / itemHeight).toInt().coerceIn(0, rowCount - 1)
    val lastExclusive = ceil((safeOffset + viewportHeight) / itemHeight).toInt()
    val last = (lastExclusive - 1).coerceIn(first, rowCount - 1)
    return first..last
}

fun railContentAlpha(panelProgress: Float, revealThreshold: Float = 0.4f): Int {
    if (!panelProgress.isFinite()) return 0
    val safeThreshold = revealThreshold.coerceIn(0f, 0.99f)
    val normalized = ((panelProgress - safeThreshold) / (1f - safeThreshold)).coerceIn(0f, 1f)
    return (normalized * 255f).toInt()
}
