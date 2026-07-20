package com.codex.edgeshelf.overlay

import com.codex.edgeshelf.data.ShelfSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureStateMachineTest {
    @Test
    fun inwardPullPeeksAndSettlesExpanded() {
        var now = 0L
        val machine = GestureStateMachine(clock = { now })
        machine.onDown(1000f, 500f, ShelfSide.RIGHT)
        assertTrue(machine.onMove(960f, 502f) is GestureEffect.Peek)
        val effect = machine.onUp(960f, 502f)
        assertEquals(GestureEffect.Settle(true, 210L), effect)
    }

    @Test
    fun longVerticalPressBeginsDraggingInsteadOfPeeking() {
        var now = 0L
        val machine = GestureStateMachine(clock = { now })
        machine.onDown(1000f, 500f, ShelfSide.RIGHT)
        now = 500L
        assertEquals(GestureEffect.BeginDragging, machine.onMove(1002f, 540f))
        assertEquals(GestureEffect.MoveVertical(10f), machine.onMove(1002f, 550f))
    }

    @Test
    fun longVerticalPressCanTakeOverAfterAThumbSizedPeek() {
        var now = 0L
        val machine = GestureStateMachine(clock = { now })
        machine.onDown(1000f, 500f, ShelfSide.RIGHT)
        assertTrue(machine.onMove(992f, 502f) is GestureEffect.Peek)

        now = 500L
        assertEquals(GestureEffect.BeginDragging, machine.onMove(992f, 540f))
        assertEquals(RailGestureState.Dragging, machine.state)
    }

    @Test
    fun leftSideInwardPullUsesPositiveXDirection() {
        val machine = GestureStateMachine(clock = { 0L })
        machine.onDown(0f, 500f, ShelfSide.LEFT)
        assertTrue(machine.onMove(30f, 500f) is GestureEffect.Peek)
    }
}
