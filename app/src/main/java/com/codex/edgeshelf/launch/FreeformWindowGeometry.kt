package com.codex.edgeshelf.launch

import kotlin.math.roundToInt

enum class FreeformContentOrientation {
    PORTRAIT,
    LANDSCAPE,
}

fun responsiveFreeformBounds(
    availableBounds: FreeformWindowBounds,
    contentOrientation: FreeformContentOrientation,
    isLargeScreen: Boolean,
): FreeformWindowBounds {
    val availableWidth = (availableBounds.right - availableBounds.left).coerceAtLeast(1)
    val availableHeight = (availableBounds.bottom - availableBounds.top).coerceAtLeast(1)
    val displayIsPortrait = availableHeight >= availableWidth
    val widthFraction = when {
        isLargeScreen -> LARGE_SCREEN_WIDTH_FRACTION
        displayIsPortrait && contentOrientation == FreeformContentOrientation.PORTRAIT -> 1f
        displayIsPortrait -> PHONE_PORTRAIT_LANDSCAPE_APP_WIDTH_FRACTION
        else -> PHONE_LANDSCAPE_WIDTH_FRACTION
    }
    val heightFraction = when {
        isLargeScreen -> LARGE_SCREEN_HEIGHT_FRACTION
        displayIsPortrait -> PHONE_PORTRAIT_HEIGHT_FRACTION
        else -> PHONE_LANDSCAPE_HEIGHT_FRACTION
    }
    val maxWidth = (availableWidth * widthFraction).roundToInt().coerceAtLeast(1)
    val maxHeight = (availableHeight * heightFraction).roundToInt().coerceAtLeast(1)
    val aspectRatio = when (contentOrientation) {
        FreeformContentOrientation.PORTRAIT -> PORTRAIT_ASPECT_RATIO
        FreeformContentOrientation.LANDSCAPE -> 1f / PORTRAIT_ASPECT_RATIO
    }

    val widthFromHeight = (maxHeight * aspectRatio).roundToInt().coerceAtLeast(1)
    val windowWidth: Int
    val windowHeight: Int
    if (widthFromHeight <= maxWidth) {
        windowWidth = widthFromHeight
        windowHeight = maxHeight
    } else {
        windowWidth = maxWidth
        windowHeight = (maxWidth / aspectRatio).roundToInt().coerceAtLeast(1)
    }

    val left = availableBounds.left + (availableWidth - windowWidth) / 2
    val top = availableBounds.top + (availableHeight - windowHeight) / 2
    return FreeformWindowBounds(
        left = left,
        top = top,
        right = left + windowWidth,
        bottom = top + windowHeight,
    )
}

private const val PORTRAIT_ASPECT_RATIO = 5f / 8f
private const val LARGE_SCREEN_WIDTH_FRACTION = 0.78f
private const val LARGE_SCREEN_HEIGHT_FRACTION = 0.82f
private const val PHONE_PORTRAIT_LANDSCAPE_APP_WIDTH_FRACTION = 0.94f
private const val PHONE_PORTRAIT_HEIGHT_FRACTION = 0.90f
private const val PHONE_LANDSCAPE_WIDTH_FRACTION = 0.88f
private const val PHONE_LANDSCAPE_HEIGHT_FRACTION = 0.88f
