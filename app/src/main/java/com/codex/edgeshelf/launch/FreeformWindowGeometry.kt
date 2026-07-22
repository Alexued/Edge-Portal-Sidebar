package com.codex.edgeshelf.launch

import android.content.pm.ActivityInfo
import kotlin.math.roundToInt

enum class FreeformContentOrientation {
    PORTRAIT,
    LANDSCAPE,
}

enum class FreeformWindowShape {
    NARROW,
    WIDE,
}

internal fun resolveFreeformWindowShape(
    requestedOrientation: Int?,
    resizeCapability: FreeformResizeCapability,
    isLargeScreen: Boolean,
    displayIsPortrait: Boolean = false,
): FreeformWindowShape {
    if (requestedOrientation in LANDSCAPE_ORIENTATIONS) {
        return FreeformWindowShape.WIDE
    }
    if (!isLargeScreen) {
        return FreeformWindowShape.NARROW
    }
    if (
        resizeCapability in setOf(
            FreeformResizeCapability.NOT_RESIZABLE,
            FreeformResizeCapability.LANDSCAPE_ONLY,
            FreeformResizeCapability.UNKNOWN,
        )
    ) {
        return FreeformWindowShape.WIDE
    }
    if (requestedOrientation in PORTRAIT_ORIENTATIONS) {
        return FreeformWindowShape.NARROW
    }
    if (
        resizeCapability == FreeformResizeCapability.PRESERVE_ORIENTATION &&
        !displayIsPortrait
    ) {
        return FreeformWindowShape.WIDE
    }
    return FreeformWindowShape.NARROW
}

internal fun resolveFreeformContentOrientation(
    requestedOrientation: Int?,
    isLargeScreen: Boolean,
    resizeCapability: FreeformResizeCapability = FreeformResizeCapability.UNKNOWN,
    displayIsPortrait: Boolean = false,
): FreeformContentOrientation {
    return when (
        resolveFreeformWindowShape(
            requestedOrientation = requestedOrientation,
            resizeCapability = resizeCapability,
            isLargeScreen = isLargeScreen,
            displayIsPortrait = displayIsPortrait,
        )
    ) {
        FreeformWindowShape.NARROW -> FreeformContentOrientation.PORTRAIT
        FreeformWindowShape.WIDE -> FreeformContentOrientation.LANDSCAPE
    }
}

internal fun isLargeScreenWorkArea(
    availableBounds: FreeformWindowBounds,
    density: Float,
): Boolean {
    if (!density.isFinite() || density <= 0f) return false
    val width = (availableBounds.right - availableBounds.left).coerceAtLeast(1)
    val height = (availableBounds.bottom - availableBounds.top).coerceAtLeast(1)
    return minOf(width, height) / density >= LARGE_SCREEN_MIN_WIDTH_DP
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
        isLargeScreen && contentOrientation == FreeformContentOrientation.PORTRAIT ->
            LARGE_SCREEN_NARROW_HEIGHT_FRACTION
        isLargeScreen -> LARGE_SCREEN_HEIGHT_FRACTION
        displayIsPortrait -> PHONE_PORTRAIT_HEIGHT_FRACTION
        else -> PHONE_LANDSCAPE_HEIGHT_FRACTION
    }
    val maxWidth = (availableWidth * widthFraction).roundToInt().coerceAtLeast(1)
    val maxHeight = (availableHeight * heightFraction).roundToInt().coerceAtLeast(1)
    val aspectRatio = when (contentOrientation) {
        FreeformContentOrientation.PORTRAIT -> if (isLargeScreen) {
            LARGE_SCREEN_NARROW_ASPECT_RATIO
        } else {
            PORTRAIT_ASPECT_RATIO
        }
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
private const val LARGE_SCREEN_NARROW_ASPECT_RATIO = 9f / 16f
private const val LARGE_SCREEN_WIDTH_FRACTION = 0.78f
private const val LARGE_SCREEN_HEIGHT_FRACTION = 0.82f
private const val LARGE_SCREEN_NARROW_HEIGHT_FRACTION = 0.88f
private const val PHONE_PORTRAIT_LANDSCAPE_APP_WIDTH_FRACTION = 0.94f
private const val PHONE_PORTRAIT_HEIGHT_FRACTION = 0.90f
private const val PHONE_LANDSCAPE_WIDTH_FRACTION = 0.88f
private const val PHONE_LANDSCAPE_HEIGHT_FRACTION = 0.88f
private const val LARGE_SCREEN_MIN_WIDTH_DP = 600f

private val LANDSCAPE_ORIENTATIONS = setOf(
    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
)

private val PORTRAIT_ORIENTATIONS = setOf(
    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
    ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
)
