package com.codex.edgeshelf.overlay

import kotlin.math.abs

/**
 * Timing and interpolation values for the edge rail.
 *
 * This class deliberately has no Android animation dependencies. A view can use the
 * returned frame values from its own frame callback (or a ValueAnimator) and the same
 * calculations are therefore available to JVM unit tests.
 */
object RailMotion {
    /** The pull distance used to decide whether a peek becomes an expansion. */
    const val EXPAND_TRIGGER_THRESHOLD_DP = 24f

    /** The visual distance travelled by the panel after the trigger is crossed. */
    const val PANEL_TRAVEL_DP = 72f

    const val EXPAND_DURATION_MS = 360L
    const val COLLAPSE_DURATION_MS = 250L

    /** Delay between the panel becoming stable and the first content item entering. */
    const val CONTENT_ENTRY_DELAY_MS = 72L
    const val ICON_STAGGER_MS = 16L
    const val ICON_MAX_STAGGER_MS = 96L
    const val ICON_DURATION_MS = 180L
    const val CONTENT_TIMELINE_DURATION_MS =
        CONTENT_ENTRY_DELAY_MS + ICON_MAX_STAGGER_MS + ICON_DURATION_MS

    const val ICON_START_ALPHA = 0f
    const val ICON_END_ALPHA = 1f
    const val ICON_START_SCALE = 0.92f
    const val ICON_END_SCALE = 1f
    const val ICON_START_EDGE_OFFSET_DP = 10f
    const val ICON_END_EDGE_OFFSET_DP = 0f

    /** Equivalent control points for the expand PathInterpolator. */
    val EXPAND_INTERPOLATOR = CubicBezierSpec(
        x1 = 0.22f,
        y1 = 0.0f,
        x2 = 0.20f,
        y2 = 1.0f,
    )

    /** Equivalent control points for the collapse PathInterpolator. */
    val COLLAPSE_INTERPOLATOR = CubicBezierSpec(
        x1 = 0.40f,
        y1 = 0.0f,
        x2 = 0.60f,
        y2 = 1.0f,
    )

    /**
     * Returns the panel's normalized progress at [elapsedMillis].
     *
     * Progress is 0 when collapsed and 1 when expanded. Negative elapsed time is treated
     * as zero, and elapsed time beyond the duration is treated as the terminal state.
     * When reduced motion is requested, the terminal state is returned immediately.
     */
    fun panelProgress(
        elapsedMillis: Long,
        expanding: Boolean,
        reducedMotion: Boolean = false,
    ): Float {
        if (reducedMotion) return if (expanding) 1f else 0f
        val duration = if (expanding) EXPAND_DURATION_MS else COLLAPSE_DURATION_MS
        val fraction = normalizedTime(elapsedMillis, duration)
        val eased = ease(fraction, if (expanding) EXPAND_INTERPOLATOR else COLLAPSE_INTERPOLATOR)
        return if (expanding) eased else 1f - eased
    }

    /**
     * Maps normalized panel progress to the visual travel distance. The trigger threshold
     * is intentionally not part of this calculation; callers may use it solely for gesture
     * recognition and then animate the remaining 72dp independently.
     */
    fun panelTravelDp(progress: Float): Float =
        progress.coerceIn(0f, 1f) * PANEL_TRAVEL_DP

    /** Absolute time at which an item starts its local entrance animation. */
    fun iconStartDelayMillis(index: Int): Long =
        CONTENT_ENTRY_DELAY_MS + iconStaggerMillis(index)

    /**
     * Returns an icon's local normalized entrance progress. Index zero starts after the
     * 72ms content delay; later rows are staggered by 16ms, capped at a 96ms offset.
     */
    fun iconProgress(
        index: Int,
        elapsedMillis: Long,
        reducedMotion: Boolean = false,
    ): Float {
        if (reducedMotion) return 1f
        val localElapsed = elapsedMillis - iconStartDelayMillis(index)
        val fraction = normalizedTime(localElapsed, ICON_DURATION_MS)
        return ease(fraction, EXPAND_INTERPOLATOR)
    }

    /**
     * Computes all visual properties needed to draw one icon in a single frame.
     * [edgeOffsetDp] is a positive magnitude toward the active edge; the view can apply
     * the sign appropriate for its left/right side.
     */
    fun iconFrame(
        index: Int,
        elapsedMillis: Long,
        reducedMotion: Boolean = false,
    ): IconMotionFrame {
        val progress = iconProgress(index, elapsedMillis, reducedMotion)
        return IconMotionFrame(
            progress = progress,
            alpha = lerp(ICON_START_ALPHA, ICON_END_ALPHA, progress),
            scale = lerp(ICON_START_SCALE, ICON_END_SCALE, progress),
            edgeOffsetDp = lerp(ICON_START_EDGE_OFFSET_DP, ICON_END_EDGE_OFFSET_DP, progress),
        )
    }

    /** Applies the supplied cubic-bezier easing as a PathInterpolator would. */
    fun ease(fraction: Float, spec: CubicBezierSpec): Float {
        val input = fraction.coerceIn(0f, 1f).toDouble()
        if (input <= 0.0) return 0f
        if (input >= 1.0) return 1f

        // Solve x(t) = input. Newton converges quickly for the monotonic curves used here;
        // the binary-search fallback also handles a nearly-flat derivative safely.
        var parameter = input
        repeat(8) {
            val x = cubic(parameter, spec.x1.toDouble(), spec.x2.toDouble())
            val derivative = cubicDerivative(parameter, spec.x1.toDouble(), spec.x2.toDouble())
            if (abs(derivative) < 1e-7) return@repeat
            val next = parameter - (x - input) / derivative
            if (next !in 0.0..1.0) return@repeat
            parameter = next
        }

        var low = 0.0
        var high = 1.0
        repeat(18) {
            val x = cubic(parameter, spec.x1.toDouble(), spec.x2.toDouble())
            if (abs(x - input) < 1e-7) return@repeat
            if (x < input) low = parameter else high = parameter
            parameter = (low + high) / 2.0
        }
        return cubic(parameter, spec.y1.toDouble(), spec.y2.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    private fun iconStaggerMillis(index: Int): Long =
        (index.coerceAtLeast(0) * ICON_STAGGER_MS).coerceAtMost(ICON_MAX_STAGGER_MS)

    private fun normalizedTime(elapsedMillis: Long, durationMillis: Long): Float {
        if (durationMillis <= 0L) return 1f
        return (elapsedMillis.coerceIn(0L, durationMillis).toDouble() / durationMillis)
            .toFloat()
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress.coerceIn(0f, 1f)

    private fun cubic(parameter: Double, first: Double, second: Double): Double {
        val inverse = 1.0 - parameter
        return 3.0 * inverse * inverse * parameter * first +
            3.0 * inverse * parameter * parameter * second +
            parameter * parameter * parameter
    }

    private fun cubicDerivative(parameter: Double, first: Double, second: Double): Double {
        val inverse = 1.0 - parameter
        return 3.0 * inverse * inverse * first +
            6.0 * inverse * parameter * (second - first) +
            3.0 * parameter * parameter * (1.0 - second)
    }
}

data class CubicBezierSpec(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

data class IconMotionFrame(
    val progress: Float,
    val alpha: Float,
    val scale: Float,
    val edgeOffsetDp: Float,
)
