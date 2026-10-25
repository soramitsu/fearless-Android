package jp.co.soramitsu.coredb.recovery.decision

import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointRuntimeVersionTest {
    @Test fun freshReadDoesNotAdmitMutationAndUnrelatedChainStaysIndependent() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val first = lane.readRuntime("a") { "public" }
        assertEquals("public", first.value)
        lane.admit(listOf("b"))
        assertTrue(lane.isCurrent(first.version))
        lane.admit(listOf("a"))
        assertFalse(lane.isCurrent(first.version))
    }

    @Test fun sameTextAbaAndWildcardNeverRestoreEarlierRead() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        lane.admit(listOf("a"))
        val first = lane.readRuntime("a") { Unit }
        lane.admit(listOf("a"))
        lane.admit(listOf("a"))
        assertFalse(lane.isCurrent(first.version))
        val latest = lane.readRuntime("a") { Unit }
        lane.admitCatalogReplacement()
        assertFalse(lane.isCurrent(latest.version))
    }

    @Test fun preparedOperationLaterGrantInvalidatesReadWithoutNewAdmission() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val operation = lane.admit(listOf("a"))
        lane.withOperation(operation) { /* read-only prepare releases lane for an explicit probe */ }
        val before = lane.readRuntime("a") { Unit }
        assertTrue(lane.isCurrent(before.version))
        lane.withOperation(operation) { lane.grantFinal(operation) {} }
        assertFalse(lane.isCurrent(before.version))
        assertTrue(lane.isCurrent(lane.readRuntime("a") { Unit }.version))
    }

    @Test fun pendingCatalogGrantAndAbandonInvalidateBeforeCommitReads() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val catalog = lane.admitCatalogReplacement()
        val before = lane.readRuntime("new-parent") { Unit }
        lane.withOperation(catalog) { lane.grantFinal(catalog) {} }
        assertFalse(lane.isCurrent(before.version))
        val operation = lane.admit(listOf("a"))
        val pending = lane.readRuntime("a") { Unit }
        lane.abandon(operation)
        assertFalse(lane.isCurrent(pending.version))
    }

    @Test fun newerIntentDuringReadCannotReturnQualifyingCapability() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        assertTrue(
            runCatching {
            lane.readRuntime("a") {
                lane.admit(listOf("a"))

            Unit
            }
        }.isFailure
        )
    }

    @Test fun physicalCommitRetirementCompletesBeforeFreshReadCanRun() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val operation = lane.admit(listOf("a"))
        val granted = CompletableDeferred<Unit>()
        val physicalComplete = CompletableDeferred<Unit>()
        var durable = "old"
        val writer = launch(start = CoroutineStart.UNDISPATCHED) {
            lane.withOperation(operation) {
                lane.grantFinal(operation) {}
                granted.complete(Unit)
                physicalComplete.await()
                durable = "new"
            }
        }
        withTimeout(5_000) { granted.await() }
        val reader = async(start = CoroutineStart.UNDISPATCHED) { lane.readRuntime("a") { durable } }
        assertFalse(reader.isCompleted)
        physicalComplete.complete(Unit)
        val observed = withTimeout(5_000) { reader.await() }
        writer.join()
        assertEquals("new", observed.value)
        assertTrue(lane.isCurrent(observed.version))
    }

    @Test fun databaseCloseRetiresReadAndQueuedReaderWithoutNewAuthority() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val old = lane.readRuntime("a") { Unit }
        val release = CompletableDeferred<Unit>()
        val writer = launch(start = CoroutineStart.UNDISPATCHED) {
            lane.withOperation(lane.admit(listOf("b"))) { release.await() }
        }
        val reader = async(start = CoroutineStart.UNDISPATCHED) { runCatching { lane.readRuntime("a") { Unit } } }
        lane.retire()
        assertFalse(lane.isCurrent(old.version))
        release.complete(Unit)
        assertTrue(withTimeout(5_000) { reader.await() }.isFailure)
        writer.join()
    }

    @Test fun cancelledQueuedReadDoesNotRunSqlCallback() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val release = CompletableDeferred<Unit>()
        val writer = launch(start = CoroutineStart.UNDISPATCHED) {
            lane.withOperation(lane.admit(listOf("b"))) { release.await() }
        }
        var reads = 0
        val reader = async(start = CoroutineStart.UNDISPATCHED) { lane.readRuntime("a") { reads++ } }
        reader.cancel()
        release.complete(Unit)
        writer.join()
        reader.join()
        assertEquals(0, reads)
    }
}
