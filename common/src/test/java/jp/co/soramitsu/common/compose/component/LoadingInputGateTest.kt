package jp.co.soramitsu.common.compose.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadingInputGateTest {
    @Test fun idleUpdatesKeepFreshGesturesAvailable() {
        val gate = LoadingInputGate()
        val original = gate.revision
        gate.update(false)
        gate.update(false)
        assertEquals(original, gate.revision)
        assertFalse(gate.blocks(original))
    }

    @Test fun busyBlocksBothExistingAndNewGestures() {
        val gate = LoadingInputGate()
        val earlier = gate.revision
        gate.update(true)
        assertTrue(gate.blocks(earlier))
        assertTrue(gate.blocks(gate.revision))
    }

    @Test fun endingLoadingDoesNotReviveAGestureStartedBeforeIt() {
        val gate = LoadingInputGate()
        val earlier = gate.revision
        gate.update(true)
        gate.update(false)
        assertTrue(gate.blocks(earlier))
        assertFalse(gate.blocks(gate.revision))
    }

    @Test fun recompositionWhileBusyDoesNotInventAnotherTransition() {
        val gate = LoadingInputGate()
        gate.update(true)
        val busy = gate.revision
        gate.update(true)
        assertEquals(busy, gate.revision)
        gate.update(false)
        assertFalse(gate.blocks(busy))
    }

    @Test fun eachNewBusyPeriodRevokesThePreviouslyFreshGesture() {
        val gate = LoadingInputGate()
        gate.update(true)
        gate.update(false)
        val fresh = gate.revision
        gate.update(true)
        gate.update(false)
        assertTrue(gate.blocks(fresh))
        assertFalse(gate.blocks(gate.revision))
    }
}
