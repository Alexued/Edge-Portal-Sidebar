package com.codex.edgeshelf.overlay

import com.codex.edgeshelf.data.ShelfSide
import kotlin.math.abs

sealed interface RailGestureState {
    data object Collapsed : RailGestureState
    data object Peeking : RailGestureState
    data object Expanded : RailGestureState
    data object Dragging : RailGestureState
    data object Editing : RailGestureState
    data class Settling(val expanded: Boolean) : RailGestureState
}

data class GestureThresholds(
    val expandDp: Float = 24f,
    val longPressMs: Long = 450L,
    val settleMs: Long = 210L,
)

sealed interface GestureEffect {
    data class Peek(val inwardDistance: Float) : GestureEffect
    data object BeginExpanded : GestureEffect
    data object BeginDragging : GestureEffect
    data class MoveVertical(val delta: Float) : GestureEffect
    data class Settle(val expanded: Boolean, val durationMs: Long) : GestureEffect
    data object Collapse : GestureEffect
    data object BeginEditing : GestureEffect
    data object NoOp : GestureEffect
}

class GestureStateMachine(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val thresholds: GestureThresholds = GestureThresholds(),
) {
    var state: RailGestureState = RailGestureState.Collapsed
        private set

    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var activeSide = ShelfSide.RIGHT

    fun onDown(x: Float, y: Float, side: ShelfSide): GestureEffect {
        downX = x
        downY = y
        lastY = y
        downAt = clock()
        activeSide = side
        return GestureEffect.NoOp
    }

    fun onMove(x: Float, y: Float): GestureEffect {
        val elapsed = (clock() - downAt).coerceAtLeast(0L)
        val verticalDistance = abs(y - downY)
        val inwardDistance = inwardDistance(x)
        if (state == RailGestureState.Collapsed || state == RailGestureState.Peeking) {
            if (elapsed >= thresholds.longPressMs && verticalDistance > inwardDistance) {
                state = RailGestureState.Dragging
                lastY = y
                return GestureEffect.BeginDragging
            }
            if (inwardDistance > 0f && inwardDistance >= verticalDistance) {
                state = RailGestureState.Peeking
                return GestureEffect.Peek(inwardDistance)
            }
        }
        if (state == RailGestureState.Expanded) {
            val outwardDistance = -inwardDistance
            if (outwardDistance >= thresholds.expandDp) {
                state = RailGestureState.Settling(expanded = false)
                return GestureEffect.Settle(expanded = false, durationMs = thresholds.settleMs)
            }
        }
        if (state == RailGestureState.Dragging) {
            val delta = y - lastY
            lastY = y
            return GestureEffect.MoveVertical(delta)
        }
        return GestureEffect.NoOp
    }

    fun onUp(x: Float, y: Float): GestureEffect {
        val inwardDistance = inwardDistance(x)
        val effect = when (state) {
            RailGestureState.Peeking -> {
                val shouldExpand = inwardDistance >= thresholds.expandDp
                state = RailGestureState.Settling(shouldExpand)
                GestureEffect.Settle(shouldExpand, thresholds.settleMs)
            }
            RailGestureState.Dragging -> {
                state = RailGestureState.Settling(expanded = false)
                GestureEffect.Settle(expanded = false, durationMs = thresholds.settleMs)
            }
            RailGestureState.Expanded -> {
                state = RailGestureState.Expanded
                GestureEffect.NoOp
            }
            else -> GestureEffect.NoOp
        }
        return effect
    }

    fun onCancel(): GestureEffect {
        val shouldExpand = state == RailGestureState.Expanded
        state = RailGestureState.Settling(shouldExpand)
        return GestureEffect.Settle(shouldExpand, thresholds.settleMs)
    }

    fun markExpanded() {
        state = RailGestureState.Expanded
    }

    fun markCollapsed() {
        state = RailGestureState.Collapsed
    }

    fun markEditing() {
        state = RailGestureState.Editing
    }

    private fun inwardDistance(currentX: Float): Float = if (activeSide == ShelfSide.RIGHT) {
        downX - currentX
    } else {
        currentX - downX
    }
}
