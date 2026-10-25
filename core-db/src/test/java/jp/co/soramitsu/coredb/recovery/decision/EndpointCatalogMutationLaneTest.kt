package jp.co.soramitsu.coredb.recovery.decision

import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catalog wildcard covers parent sets discovered only after storage admission. */
class EndpointCatalogMutationLaneTest {
    @Test fun catalogRetiresAllOlderChainCaptures() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val first = lane.admit(listOf("a"))
        val second = lane.admit(listOf("b"))
        val catalog = lane.admitCatalogReplacement()
        assertFalse(lane.isCurrent(first))
        assertFalse(lane.isCurrent(second))
        lane.withOperation(catalog) {
            lane.requireOwnedCatalog(catalog)
            lane.requireOwnedTargets(catalog, listOf("newly-discovered-parent"))
            lane.grantFinal(catalog) {}
        }
        assertTrue(lane.isLatest(catalog))
        assertFalse(lane.isCurrent(catalog))
    }

    @Test fun anyNewParentIntentRetiresQueuedCatalog() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val held = lane.admit(listOf("held"))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) {
            lane.withOperation(held) {
                entered.complete(Unit)
            withTimeout(5000) { release.await() }
            }
        }
        withTimeout(5000) { entered.await() }
        val catalog = lane.admitCatalogReplacement()
        var callback = false
        val queued = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { lane.withOperation(catalog) { callback = true } }
        }
        val insert = lane.admit(listOf("new-parent"))
        assertFalse(lane.isCurrent(catalog))
        assertTrue(lane.isCurrent(insert))
        release.complete(Unit)
        withTimeout(5000) { owner.await() }
        assertTrue(withTimeout(5000) { queued.await() }.isFailure)
        assertFalse(callback)
    }

    @Test fun chainIntentBeforeCatalogFinalGrantRejectsIt() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val catalog = lane.admitCatalogReplacement()
        lane.withOperation(catalog) {
            lane.requireOwnedTargets(catalog, listOf("a", "parent-added-before-snapshot"))
            lane.admit(listOf("a"))
            assertTrue(runCatching { lane.grantFinal(catalog) { error("Cannot grant retired catalog") } }.isFailure)
        }
    }

    @Test fun catalogAfterParentAdmissionRetiresParentBeforeWrite() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val parent = lane.admit(listOf("new-parent"))
        val catalog = lane.admitCatalogReplacement()
        assertTrue(runCatching { lane.withOperation(parent) { error("Retired parent write") } }.isFailure)
        assertTrue(lane.isCurrent(catalog))
    }

    @Test fun wildcardCannotBeUsedAsExactSavedSelectionAuthority() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val catalog = lane.admitCatalogReplacement()
        lane.withOperation(catalog) {
            assertTrue(runCatching { lane.requireChain(catalog, "a") }.isFailure)
        }
    }

    @Test fun normalTargetsNeedOwnedLaneAndExactCoverage() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val action = lane.admit(listOf("a", "b"))
        assertTrue(runCatching { lane.requireOwnedTargets(action, listOf("a")) }.isFailure)
        lane.withOperation(action) {
            lane.requireOwnedTargets(action, listOf("a"))
            assertTrue(runCatching { lane.requireOwnedTargets(action, listOf("unknown")) }.isFailure)
            assertTrue(runCatching { lane.requireOwnedCatalog(action) }.isFailure)
        }
    }

    @Test fun catalogReplacementHandlesAbaAndPreservesLaterUnrelatedChains() {
        val lane = EndpointMutationLane()
        val first = lane.admitCatalogReplacement()
        val second = lane.admitCatalogReplacement()
        val third = lane.admitCatalogReplacement()
        assertFalse(lane.isCurrent(first))
        assertFalse(lane.isCurrent(second))
        assertTrue(lane.isCurrent(third))
        val a = lane.admit(listOf("a"))
        val b = lane.admit(listOf("b"))
        assertFalse(lane.isCurrent(third))
        assertTrue(lane.isCurrent(a))
        assertTrue(lane.isCurrent(b))
    }

    @Test fun wildcardOverflowRetirementAndBoundsFailClosed() = runBlocking<Unit> {
        assertTrue(runCatching { EndpointMutationLane(Long.MAX_VALUE).admitCatalogReplacement() }.isFailure)
        val lane = EndpointMutationLane()
        val catalog = lane.admitCatalogReplacement()
        lane.withOperation(catalog) {
            assertTrue(runCatching { lane.requireOwnedTargets(catalog, listOf("")) }.isFailure)
            assertTrue(runCatching { lane.requireOwnedTargets(catalog, (0..4096).map(Int::toString)) }.isFailure)
        }
        lane.retire()
        assertFalse(lane.isLatest(catalog))
        assertTrue(runCatching { lane.admitCatalogReplacement() }.isFailure)
    }
}
