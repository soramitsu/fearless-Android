package jp.co.soramitsu.account.impl.domain.recovery

import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectionCoreTestBridgeTest {
    @Test
    fun suspendedReadWaitsForRealLaneAndReturnsActualCurrentVersion() = runTest {
        val lane = EndpointMutationLane()
        val operation = lane.admit(listOf("chain"))
        val gate = CompletableDeferred<Unit>()
        val held = launch { lane.withOperation(operation) { gate.await() } }
        runCurrent()
        var read = false
        val result = async {
            SelectionCoreTestBridge.readRuntime(lane, "chain") {
            read = true
        "fixture"
        }
        }
        runCurrent()
        assertFalse(read)
        gate.complete(Unit)
        held.join()
        val observed = result.await()
        assertEquals("fixture", observed.value)
        assertTrue(lane.isCurrent(observed.version))
    }

    @Test
    fun canceledQueuedReadDoesNotRunAfterLaneIsReleased() = runTest {
        val lane = EndpointMutationLane()
        val gate = CompletableDeferred<Unit>()
        val held = launch { lane.withOperation(lane.admit(listOf("chain"))) { gate.await() } }
        runCurrent()
        var read = false
        val waiting = launch { SelectionCoreTestBridge.readRuntime(lane, "chain") { read = true } }
        runCurrent()
        waiting.cancelAndJoin()
        gate.complete(Unit)
        held.join()
        assertFalse(read)
    }

    @Test
    fun synchronousFailurePropagatesActualCauseWithoutReflectionWrapper() = runTest {
        val lane = EndpointMutationLane()
        val failure = IllegalStateException("public fixture failure")
        val observed = runCatching { SelectionCoreTestBridge.readRuntime(lane, "chain") { throw failure } }.exceptionOrNull()
        assertSame(failure, observed)
    }
}
