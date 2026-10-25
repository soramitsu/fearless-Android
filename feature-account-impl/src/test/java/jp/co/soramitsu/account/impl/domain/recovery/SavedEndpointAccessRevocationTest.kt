package jp.co.soramitsu.account.impl.domain.recovery

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Per-lease notification and monitor behavior; no storage or Android lifecycle execution. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedEndpointAccessRevocationTest {
    private val access = SavedEndpointAccess { 0L }
    private val owner = Any()

    @Test fun everyRevocationPathCompletesExistingAndLateWaitersOnlyAfterAuthorityIsRetired() = runTest {
        val paths: List<() -> Unit> = listOf(
            { access.revokeLease(owner) }, { access.revokeAuthorization(owner) },
            { access.revoke(owner) }, { access.revokeAll() },
            { access.begin(owner) }, { access.begin(Any()) }
        )
        for (revoke in paths) {
            val lease = claim(owner)
            val first = async(start = CoroutineStart.UNDISPATCHED) { lease.awaitRevocation() }
            val second = async(start = CoroutineStart.UNDISPATCHED) { lease.awaitRevocation() }
            assertFalse(first.isCompleted); assertFalse(second.isCompleted)
            revoke(); runCurrent()
            assertTrue(first.isCompleted); assertTrue(second.isCompleted)
            assertFalse(access.isCurrent(owner, lease))
            assertNull(access.decisionAuthority(owner, lease))
            val late = async(start = CoroutineStart.UNDISPATCHED) { lease.awaitRevocation() }
            assertTrue(late.isCompleted)
            access.revokeAll(); runCurrent()
            assertFalse(first.isCancelled); assertFalse(second.isCancelled)
        }
    }

    @Test fun wrongOwnerWrongClaimAndStaleOwnerCleanupCannotNotifyCurrentLease() = runTest {
        val staleOwner = Any()
        val old = claim(staleOwner)
        val lease = claim(owner)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { lease.awaitRevocation() }
        access.revoke(staleOwner); access.revokeLease(staleOwner); access.revokeAuthorization(staleOwner)
        assertNull(access.claim(staleOwner, "stale"))
        assertFalse(access.approve(staleOwner, "stale"))
        runCurrent()
        assertFalse(waiting.isCompleted)
        assertTrue(access.isCurrent(owner, lease))
        val retired = async(start = CoroutineStart.UNDISPATCHED) { old.awaitRevocation() }
        assertTrue(retired.isCompleted)
        access.revokeAll(); runCurrent(); assertTrue(waiting.isCompleted)
    }

    @Test fun canceledWaiterDoesNotCancelLeaseSignalOrAnyOtherWaiter() = runTest {
        val lease = claim(owner)
        val canceled = launch(start = CoroutineStart.UNDISPATCHED) { lease.awaitRevocation() }
        val remaining = async(start = CoroutineStart.UNDISPATCHED) { lease.awaitRevocation() }
        canceled.cancel(); runCurrent()
        assertTrue(canceled.isCancelled); assertFalse(remaining.isCompleted)
        assertTrue(access.isCurrent(owner, lease))
        access.revokeLease(owner); runCurrent()
        assertTrue(remaining.isCompleted); assertFalse(remaining.isCancelled)
        lease.awaitRevocation()
    }

    @Test fun unconfinedObserversNeverRunUnderAccessMonitorAndCanReenterAfterRetirement() = runTest {
        val paths: List<() -> Unit> = listOf(
            { access.revokeLease(owner) }, { access.revokeAuthorization(owner) },
            { access.revoke(owner) }, { access.revokeAll() }, { access.begin(Any()) }
        )
        for (revoke in paths) {
            val lease = claim(owner)
            var replacement: SavedEndpointAccess.Lease? = null
            val waiter = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                lease.awaitRevocation()
                assertFalse(Thread.holdsLock(access))
                assertFalse(access.isCurrent(owner, lease))
                assertNull(access.decisionAuthority(owner, lease))
                replacement = claim(owner)
            }
            revoke()
            assertTrue(waiter.isCompleted)
            assertNotNull(replacement)
            assertTrue(access.isCurrent(owner, replacement!!))
            access.revokeAll()
        }
    }

    @Test fun pendingApprovalAndClaimDoNotNotifyTheFreshLeaseUntilItsOwnRevocation() = runTest {
        val old = claim(owner)
        val oldWaiter = async(start = CoroutineStart.UNDISPATCHED) { old.awaitRevocation() }
        val token = access.begin(owner).id
        runCurrent(); assertTrue(oldWaiter.isCompleted)
        access.revokeLease(owner)
        assertTrue(access.approve(owner, token))
        val fresh = requireNotNull(access.claim(owner, token))
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { fresh.awaitRevocation() }
        assertFalse(access.approve(owner, token)); assertNull(access.claim(owner, token))
        runCurrent(); assertFalse(waiter.isCompleted)
        assertTrue(access.isCurrent(owner, fresh))
        access.revokeAuthorization(owner); runCurrent(); assertTrue(waiter.isCompleted)
    }

    private fun claim(subject: Any): SavedEndpointAccess.Lease {
        val request = access.begin(subject).id
        assertTrue(access.approve(subject, request))
        return requireNotNull(access.claim(subject, request))
    }
}
