package jp.co.soramitsu.coredb.recovery.decision

import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EndpointSelectionAuthorityTest {
    @Test fun freshCommandPreparesThenCommitsExactlyOnce() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val capture = capture(lane)
        val authority = EndpointSelectionAuthority(lane)
        val attempt = authority.newAttempt(capture)
        var calls = 0
        lane.withOperation(capture.operation) { authority.requireCurrent(attempt, capture) }
        val result = lane.withOperation(capture.operation) {
            authority.commitOnce(attempt, capture) {
                calls++
            "committed"
            }
        }
        assertEquals("committed", result)
        assertEquals(1, calls)
        assertFalse(lane.isCurrent(capture.operation))
        assertTrue(lane.isLatest(capture.operation))
        assertThrows(IllegalStateException::class.java) { authority.requireCurrent(attempt, capture) }
    }

    @Test fun sameValueCaptureAndForeignAuthorityCannotStealAttempt() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val capture = capture(lane)
        val authority = EndpointSelectionAuthority(lane)
        val attempt = authority.newAttempt(capture)
        val twin = EndpointSelectionCapture(capture.request, capture.operation, capture.targetRowId, capture.selected)
        assertThrows(IllegalArgumentException::class.java) { authority.requireCurrent(attempt, twin) }
        assertThrows(IllegalArgumentException::class.java) { EndpointSelectionAuthority(lane).requireCurrent(attempt, capture) }
        lane.withOperation(capture.operation) { authority.commitOnce(attempt, capture) { Unit } }
    }

    @Test fun differentDatabaseLaneNeverAdoptsCapture() {
        val capture = capture(EndpointMutationLane())
        assertThrows(IllegalArgumentException::class.java) {
            EndpointSelectionAuthority(EndpointMutationLane()).newAttempt(capture)
        }
    }

    @Test fun revokeBeforeGrantRejectsAndConsumesAttempt() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val capture = capture(lane)
        val authority = EndpointSelectionAuthority(lane)
        val attempt = authority.newAttempt(capture)
        lane.withOperation(capture.operation) {
            authority.revoke()
            assertFalse(authority.isCurrent())
            assertThrows(IllegalStateException::class.java) { authority.commitOnce(attempt, capture) { error("unreachable") } }
        }
        assertFalse(lane.isCurrent(capture.operation))
        assertThrows(IllegalStateException::class.java) { authority.newAttempt(capture) }
    }

    @Test fun abandonmentRetiresTheExactInvocationBeforeCommit() {
        val lane = EndpointMutationLane()
        val capture = capture(lane)
        val authority = EndpointSelectionAuthority(lane)
        val attempt = authority.newAttempt(capture)
        authority.abandon(attempt)
        assertFalse(lane.isCurrent(capture.operation))
        assertTrue(authority.isCurrent())
        assertThrows(IllegalStateException::class.java) { authority.requireCurrent(attempt, capture) }
        assertThrows(IllegalArgumentException::class.java) { EndpointSelectionAuthority(lane).abandon(attempt) }
    }

    @Test fun finalGrantRequiresLaneOwnership() {
        val lane = EndpointMutationLane()
        val capture = capture(lane)
        val authority = EndpointSelectionAuthority(lane)
        val attempt = authority.newAttempt(capture)
        assertThrows(IllegalStateException::class.java) { authority.commitOnce(attempt, capture) { error("unreachable") } }
        assertTrue(lane.isCurrent(capture.operation))
    }

    @Test fun overlappingIntentRetiresHeldPreparationAcrossAba() {
        val lane = EndpointMutationLane()
        val capture = capture(lane)
        val authority = EndpointSelectionAuthority(lane)
        val attempt = authority.newAttempt(capture)
        val middle = lane.admit(listOf("solana-mainnet"))
        val latest = lane.admit(listOf("solana-mainnet"))
        assertFalse(lane.isCurrent(capture.operation))
        assertFalse(lane.isCurrent(middle))
        assertTrue(lane.isCurrent(latest))
        assertThrows(IllegalStateException::class.java) { authority.requireCurrent(attempt, capture) }
    }

    @Test fun unrelatedChainIntentPreservesHealthyGrant() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val capture = capture(lane)
        val authority = EndpointSelectionAuthority(lane)
        val attempt = authority.newAttempt(capture)
        val other = lane.admit(listOf("other-chain"))
        lane.withOperation(capture.operation) { authority.commitOnce(attempt, capture) { Unit } }
        assertTrue(lane.isCurrent(other))
    }

    @Test fun overlappingPartOfCatalogOperationRetiresWholeCatalogToken() {
        val lane = EndpointMutationLane()
        val catalog = lane.admit(listOf("a", "b"))
        lane.admit(listOf("b"))
        assertFalse(lane.isCurrent(catalog))
    }

    @Test fun mutableCallerCollectionCannotChangeAdmittedChains() {
        val lane = EndpointMutationLane()
        val ids = mutableListOf("a")
        val operation = lane.admit(ids)
        ids[0] = "b"
        lane.requireChain(operation, "a")
        assertThrows(IllegalArgumentException::class.java) { lane.requireChain(operation, "b") }
    }

    @Test fun boundsInvalidTextAndOverflowFailClosed() {
        val lane = EndpointMutationLane()
        for (ids in listOf(emptyList(), listOf(""), listOf("\uD800"), listOf("a".repeat(513)))) {
            assertThrows(IllegalArgumentException::class.java) { lane.admit(ids) }
        }
        assertThrows(IllegalStateException::class.java) { EndpointMutationLane(Long.MAX_VALUE).admit(listOf("a")) }
        val last = EndpointMutationLane(Long.MAX_VALUE - 1)
        val operation = last.admit(listOf("a"))
        assertThrows(IllegalStateException::class.java) { last.admit(listOf("a")) }
        assertTrue(last.isCurrent(operation))
    }

    @Test fun chainBudgetBoundsRetainedOrderWithoutRetiringExistingCommand() {
        val lane = EndpointMutationLane()
        val operation = lane.admit((0 until 4096).map { "chain-$it" })
        assertThrows(IllegalStateException::class.java) { lane.admit(listOf("extra")) }
        assertTrue(lane.isCurrent(operation))
        assertThrows(IllegalArgumentException::class.java) { lane.admit((0..4096).map { "chain-$it" }) }
    }

    @Test fun retiredDatabaseRejectsEveryOldIdentity() {
        val lane = EndpointMutationLane()
        val operation = lane.admit(listOf("a"))
        lane.retire()
        assertFalse(lane.isLatest(operation))
        assertFalse(lane.isCurrent(operation))
        assertThrows(IllegalStateException::class.java) { lane.admit(listOf("a")) }
    }

    @Test fun foreignLaneOperationsRejectWithoutRetiringHealthyCommand() {
        val lane = EndpointMutationLane()
        val operation = lane.admit(listOf("a"))
        val foreign = EndpointMutationLane()
        assertThrows(IllegalArgumentException::class.java) { foreign.isCurrent(operation) }
        assertThrows(IllegalArgumentException::class.java) { foreign.isLatest(operation) }
        assertThrows(IllegalArgumentException::class.java) { foreign.abandon(operation) }
        assertTrue(lane.isCurrent(operation))
    }

    @Test fun laterIntentCanRetireAnActiveWorkerWithoutWaitingForItsLane() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val capture = capture(lane)
        val authority = EndpointSelectionAuthority(lane)
        val attempt = authority.newAttempt(capture)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val worker = async(start = CoroutineStart.UNDISPATCHED) {
            lane.withOperation(capture.operation) {
                entered.complete(Unit)
                release.await()
                assertThrows(IllegalStateException::class.java) { authority.commitOnce(attempt, capture) { error("unreachable") } }
            }
        }
        entered.await()
        val next = lane.admit(listOf("solana-mainnet"))
        release.complete(Unit)
        worker.await()
        lane.withOperation(next) { lane.grantFinal(next) {} }
        assertTrue(lane.isLatest(next))
    }

    @Test fun queuedOldWorkCannotRunAfterNewerIntent() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val holder = lane.admit(listOf("holder"))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holding = launch(start = CoroutineStart.UNDISPATCHED) {
            lane.withOperation(holder) {
                entered.complete(Unit)
            release.await()
            }
        }
        entered.await()
        val old = lane.admit(listOf("a"))
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { lane.withOperation(old) { error("stale callback ran") } }
        }
        val next = lane.admit(listOf("a"))
        release.complete(Unit)
        holding.join()
        assertTrue(waiting.await().exceptionOrNull() is IllegalStateException)
        lane.withOperation(next) { Unit }
    }

    @Test fun cancellationWhileWaitingConsumesCommandWithoutEnteringCallback() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val holder = lane.admit(listOf("holder"))
        val release = CompletableDeferred<Unit>()
        val holding = launch(start = CoroutineStart.UNDISPATCHED) { lane.withOperation(holder) { release.await() } }
        val operation = lane.admit(listOf("a"))
        var calls = 0
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) { lane.withOperation(operation) { calls++ } }
        waiting.cancelAndJoin()
        assertFalse(lane.isCurrent(operation))
        assertEquals(0, calls)
        release.complete(Unit)
        holding.join()
    }

    @Test fun thrownPreparationRetiresCommandAndReleasesLane() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val operation = lane.admit(listOf("a"))
        val sentinel = IllegalArgumentException("synthetic")
        assertSame(sentinel, runCatching { lane.withOperation(operation) { throw sentinel } }.exceptionOrNull())
        assertFalse(lane.isCurrent(operation))
        val next = lane.admit(listOf("a"))
        lane.withOperation(next) { Unit }
    }

    @Test fun sameOperationCannotReenterAndDeadlockItsLane() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val operation = lane.admit(listOf("a"))
        lane.withOperation(operation) {
            assertTrue(
                runCatching {
                    lane.withOperation(operation) { error("unreachable") }
                }.exceptionOrNull() is IllegalStateException
            )
        }
        assertFalse(lane.isCurrent(operation))
    }

    @Test fun failedPhysicalCommitCannotReplayGrantedAttempt() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val capture = capture(lane)
        val authority = EndpointSelectionAuthority(lane)
        val attempt = authority.newAttempt(capture)
        val failure = IllegalStateException("synthetic commit failure")
        val result = runCatching {
            lane.withOperation(capture.operation) { authority.commitOnce(attempt, capture) { throw failure } }
        }
        assertSame(failure, result.exceptionOrNull())
        assertThrows(IllegalStateException::class.java) { authority.requireCurrent(attempt, capture) }
    }

    @Test fun physicalCommitRunsOutsideMonitorAndMayFinishAfterRevocation() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val capture = capture(lane)
        val authority = EndpointSelectionAuthority(lane)
        val attempt = authority.newAttempt(capture)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = lane.withOperation(capture.operation) {
                authority.commitOnce(attempt, capture) {
                    assertFalse(Thread.holdsLock(lane.stateLock))
                    val done = CountDownLatch(1)
                    executor.execute {
                        authority.revoke()
                    lane.admit(listOf("solana-mainnet"))
                    done.countDown()
                    }
                    assertTrue(done.await(2, TimeUnit.SECONDS))
                    "authorized physical completion"
                }
            }
            assertEquals("authorized physical completion", result)
            assertFalse(authority.isCurrent())
            assertFalse(lane.isLatest(capture.operation))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun resultAndDiagnosticsRetainIdentityWithoutEndpointTextOrNetworkClaims() {
        val lane = EndpointMutationLane()
        val capture = capture(lane)
        val authority = EndpointSelectionAuthority(lane)
        val attempt = authority.newAttempt(capture)
        val result = EndpointSelectionResult(EndpointSelectionResult.Kind.SELECTED, capture)
        assertSame(capture.operation, result.command)
        assertSame(capture.request.decision, result.decision)
        assertSame(capture.request.owner, result.owner)
        assertFalse(capture.alreadySelected)
        val active = EndpointSelectionCapture(capture.request, capture.operation, 7, EndpointSelectionSql.Selected(7, "a".repeat(64)))
        assertTrue(active.alreadySelected)
        assertThrows(IllegalArgumentException::class.java) { EndpointSelectionOwner(1, 0, null) }
        for (value in listOf(capture.request, capture, capture.operation, attempt, result)) {
            assertFalse(value.toString().contains("credential-marker"))
            assertFalse(value.toString().contains("https://"))
        }
    }

    private fun capture(lane: EndpointMutationLane): EndpointSelectionCapture {
        val source = EndpointDecisionSourceBinding(EndpointRecordRef(EndpointInventorySource.OWNED, -1, 8), "a".repeat(64))
        val candidate = EndpointDecisionCandidate("credential-marker", "https://example.invalid/credential-marker?key=credential-marker")
        val target = EndpointDecisionTarget("solana-mainnet", "b".repeat(64), EndpointDecisionTarget.Family.SOLANA, "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d")
        val decision = EndpointRecoveryDecision(1, 2, source, candidate, target)
        val request = EndpointSelectionRequest(decision, EndpointSelectionOwner(-1, 8, -2))
        return EndpointSelectionCapture(request, lane.admit(listOf(target.chainId)), 7, null)
    }
}
