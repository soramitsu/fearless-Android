package jp.co.soramitsu.account.impl.domain.recovery

import org.junit.Assert.*
import org.junit.Test

class SavedEndpointDecisionAccessTest {
    @Test fun onlyExactClaimedLeaseExposesAuthorityAndEveryClearingPathRevokesIt() {
        val actions: List<(SavedEndpointAccess, Any) -> Unit> = listOf(
            { access, owner -> access.begin(owner) },
            { access, owner -> access.revokeLease(owner) },
            { access, owner -> access.revokeAuthorization(owner) },
            { access, owner -> access.revoke(owner) },
            { access, _ -> access.revokeAll() }
        )
        actions.forEach { revoke ->
            val access = SavedEndpointAccess { 0L }
            val owner = Any()
            val request = access.begin(owner)
            assertNull(access.decisionAuthority(owner, SavedEndpointAccess.Lease()))
            assertTrue(access.approve(owner, request.id))
            val lease = checkNotNull(access.claim(owner, request.id))
            val authority = checkNotNull(access.decisionAuthority(owner, lease))
            assertTrue(authority.isCurrent())
            assertNull(access.decisionAuthority(Any(), lease))
            assertNull(access.decisionAuthority(owner, SavedEndpointAccess.Lease()))
            revoke(access, owner)
            assertFalse(authority.isCurrent())
            assertNull(access.decisionAuthority(owner, lease))
        }
    }

    @Test fun foreignOwnerCannotRevokeAndFreshApprovalNeverReactivatesOldAuthority() {
        val access = SavedEndpointAccess { 0L }
        val owner = Any()
        fun claim(): SavedEndpointAccess.Lease {
            val request = access.begin(owner)
            assertTrue(access.approve(owner, request.id))
            return checkNotNull(access.claim(owner, request.id))
        }
        val oldLease = claim()
        val old = checkNotNull(access.decisionAuthority(owner, oldLease))
        access.revokeLease(Any())
        access.revokeAuthorization(Any())
        access.revoke(Any())
        assertTrue(old.isCurrent())
        val freshLease = claim()
        val fresh = checkNotNull(access.decisionAuthority(owner, freshLease))
        assertNotSame(old, fresh)
        assertFalse(old.isCurrent())
        assertTrue(fresh.isCurrent())
    }
}
