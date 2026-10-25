package jp.co.soramitsu.coredb.recovery.selection

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointObservedAdmissionTest {
    @Test fun exactRenderedObservationAdmitsOnceAndFencesItsOwnVersion() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val observed = lane.readRuntime("a") { "rendered" }
        val operation = checkNotNull(lane.admitObserved(observed.version))
        assertTrue(lane.isCurrent(operation))
        assertFalse(lane.isCurrent(observed.version))
        assertNull(lane.admitObserved(observed.version))
        assertTrue(lane.isCurrent(operation))
    }

    @Test fun newerSameChainIntentCannotBeOvertakenByStaleRenderedObservation() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val old = lane.readRuntime("a") {}
        val newer = lane.admit(listOf("a"))
        assertNull(lane.admitObserved(old.version))
        assertTrue(lane.isCurrent(newer))
        lane.abandon(newer)
        assertNull(lane.admitObserved(old.version))
    }

    @Test fun unrelatedIntentDoesNotRevokeObservationButCatalogDoes() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val first = lane.readRuntime("a") {}
        lane.admit(listOf("b"))
        assertNotNull(lane.admitObserved(first.version))
        val second = lane.readRuntime("a") {}
        lane.admitCatalogReplacement()
        assertNull(lane.admitObserved(second.version))
    }

    @Test fun twoSimultaneousTapsFromSameObservationHaveExactlyOneAdmission() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val observed = lane.readRuntime("a") {}
        val start = CountDownLatch(1)
        val both = (1..2).map {
            async(Dispatchers.Default) {
                check(start.await(5, TimeUnit.SECONDS))
            lane.admitObserved(observed.version)
            }
        }
        start.countDown()
        val results = both.awaitAll()
        assertEquals(1, results.count { it != null })
        assertTrue(lane.isCurrent(results.filterNotNull().single()))
    }

    @Test fun settledVersionRequiresExactConsumptionAndRetirementRejectsAdmission() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val operation = lane.admit(listOf("a"))
        val pending = lane.readRuntime("a") {}
        assertFalse(lane.isSettled(pending.version))
        lane.abandon(operation)
        assertFalse(lane.isSettled(pending.version))
        val settled = lane.readRuntime("a") {}
        assertTrue(lane.isSettled(settled.version))
        lane.retire()
        assertNull(lane.admitObserved(settled.version))
        assertFalse(lane.isSettled(settled.version))
    }

    @Test fun foreignLaneObservationCannotAdmitAndChangesNeverRunUnderStateMonitor() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val foreign = EndpointMutationLane().readRuntime("a") {}
        assertTrue(runCatching { lane.admitObserved(foreign.version) }.isFailure)
        var observations = 0
        val observer = launch(Dispatchers.Unconfined) {
            lane.runtimeChanges.collect {
                assertFalse(Thread.holdsLock(lane.stateLock))
            observations++
            }
        }
        val operation = lane.admit(listOf("a"))
        lane.abandon(operation)
        lane.retire()
        observer.cancel()
        assertTrue(observations >= 4)
    }

    @Test fun finalGrantNotifiesAfterPhysicalLaneReleaseAndNeverDuringCommit() = runBlocking<Unit> {
        val lane = EndpointMutationLane()
        val operation = lane.admit(listOf("a"))
        var inside = false
        var notified = false
        val observer = launch(Dispatchers.Unconfined) {
            lane.runtimeChanges.collect {
            assertFalse(inside)
        notified = true
        }
        }
        notified = false
        lane.withOperation(operation) {
            inside = true
        lane.grantFinal(operation) {}
        assertFalse(notified)
        inside = false
        }
        assertTrue(notified)
        observer.cancel()
    }
}
