package com.codex.edgeshelf.overlay

import kotlin.math.abs

fun shouldStartRailScroll(
    deltaX: Float,
    deltaY: Float,
    touchSlop: Float,
    maximumOffset: Float,
): Boolean {
    if (!deltaX.isFinite() || !deltaY.isFinite() ||
        !touchSlop.isFinite() || !maximumOffset.isFinite() ||
        touchSlop < 0f || maximumOffset <= 0f
    ) {
        return false
    }
    return abs(deltaY) > touchSlop && abs(deltaY) > abs(deltaX)
}

fun railOffsetAfterDrag(
    currentOffset: Float,
    fingerDeltaY: Float,
    maximumOffset: Float,
): Float {
    if (!fingerDeltaY.isFinite()) return clampRailScrollOffset(currentOffset, maximumOffset)
    return clampRailScrollOffset(currentOffset - fingerDeltaY, maximumOffset)
}

fun railContentFlingVelocity(
    fingerVelocityY: Float,
    minimumVelocity: Float,
    maximumVelocity: Float,
): Float {
    if (!fingerVelocityY.isFinite() || !minimumVelocity.isFinite() ||
        !maximumVelocity.isFinite() || minimumVelocity < 0f ||
        maximumVelocity <= 0f || minimumVelocity > maximumVelocity
    ) {
        return 0f
    }
    if (abs(fingerVelocityY) < minimumVelocity) return 0f
    return (-fingerVelocityY).coerceIn(-maximumVelocity, maximumVelocity)
}

fun canRailFling(
    scrollOffset: Float,
    maximumOffset: Float,
    contentVelocityY: Float,
): Boolean {
    if (!scrollOffset.isFinite() || !maximumOffset.isFinite() ||
        !contentVelocityY.isFinite() || maximumOffset <= 0f || contentVelocityY == 0f
    ) {
        return false
    }
    val offset = scrollOffset.coerceIn(0f, maximumOffset)
    return if (contentVelocityY > 0f) offset < maximumOffset else offset > 0f
}
