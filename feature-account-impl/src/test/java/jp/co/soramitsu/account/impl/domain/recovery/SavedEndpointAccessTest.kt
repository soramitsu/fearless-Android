package jp.co.soramitsu.account.impl.domain.recovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedEndpointAccessTest {
    private var now = 1_000L
    private val access = SavedEndpointAccess { now }
    private val owner = Any()

    @Test fun requestAloneCannotRevealAndApprovalAndClaimAreEachOneShot() {
        val request = access.begin(owner).id
        assertNull(access.claim(owner, request))
        assertTrue(access.approve(owner, request))
        assertFalse(access.approve(owner, request))
        val lease = checkNotNull(access.claim(owner, request))
        assertTrue(access.isCurrent(owner, lease))
        assertNull(access.claim(owner, request))
        assertFalse(access.approve(owner, request))
        assertTrue(access.isCurrent(owner, lease))
    }

    @Test fun equalButDifferentOwnerCannotApproveClaimOrUseAnActiveLease() {
        val first = String(charArrayOf('a'))
        val equal = String(charArrayOf('a'))
        assertTrue(first == equal && first !== equal)
        val request = access.begin(first).id
        assertFalse(access.approve(equal, request))
        assertTrue(access.approve(first, request))
        assertNull(access.claim(equal, request))
        val lease = checkNotNull(access.claim(first, request))
        assertFalse(access.isCurrent(equal, lease))
        assertTrue(access.isCurrent(first, lease))
    }

    @Test fun wrongRequestTokenCannotApproveOrConsumeTheCurrentRequest() {
        val request = access.begin(owner).id
        assertFalse(access.approve(owner, "unrelated"))
        assertTrue(access.approve(owner, request))
        assertNull(access.claim(owner, "unrelated"))
        assertNotNull(access.claim(owner, request))
    }

    @Test fun newerRequestRejectsTheOldAuthenticatedCallbackAndRevokesTheOldPage() {
        val first = access.begin(owner).id
        assertTrue(access.approve(owner, first))
        val oldLease = checkNotNull(access.claim(owner, first))
        val second = access.begin(owner).id
        assertNotEquals(first, second)
        assertFalse(access.isCurrent(owner, oldLease))
        assertFalse(access.approve(owner, first))
        assertNull(access.claim(owner, first))
        assertTrue(access.approve(owner, second))
        assertTrue(access.isCurrent(owner, checkNotNull(access.claim(owner, second))))
    }

    @Test fun changingOwnerInvalidatesOldApprovalAndOldOwnerCleanupCannotCancelTheNewRequest() {
        val first = access.begin(owner).id
        assertTrue(access.approve(owner, first))
        val nextOwner = Any()
        val second = access.begin(nextOwner).id
        access.revoke(owner)
        assertNull(access.claim(owner, first))
        assertTrue(access.approve(nextOwner, second))
        val lease = checkNotNull(access.claim(nextOwner, second))
        access.revokeLease(owner)
        assertTrue(access.isCurrent(nextOwner, lease))
    }

    @Test fun copiedRestoredTokenCannotAuthorizeANewOwnerOrANewProcess() {
        val request = access.begin(owner).id
        assertTrue(access.approve(owner, request))
        val restoredOwner = Any()
        assertNull(access.claim(restoredOwner, request))
        val restartedProcess = SavedEndpointAccess { now }
        assertFalse(restartedProcess.approve(owner, request))
        assertNull(restartedProcess.claim(owner, request))
        assertNotNull(access.claim(owner, request))
    }

    @Test fun intentionalPinDepartureRevokesPageAuthorityButPreservesPendingRequest() {
        val request = access.begin(owner).id
        access.revokeLease(owner)
        assertTrue(access.approve(owner, request))
        val lease = checkNotNull(access.claim(owner, request))
        access.revokeLease(owner)
        assertFalse(access.isCurrent(owner, lease))
        assertNull(access.claim(owner, request))
    }

    @Test fun canceledPinRoundTripCannotApproveOrClaimAndFreshRoundTripStillWorks() {
        val request = access.begin(owner).id
        access.revoke(owner)
        assertFalse(access.approve(owner, request))
        assertNull(access.claim(owner, request))
        val fresh = access.begin(owner).id
        assertTrue(access.approve(owner, fresh))
        assertTrue(access.isCurrent(owner, checkNotNull(access.claim(owner, fresh))))
    }

    @Test fun ownerDepartureRevokesAnAlreadyClaimedLease() {
        val request = access.begin(owner).id
        assertTrue(access.approve(owner, request))
        val lease = checkNotNull(access.claim(owner, request))
        access.revoke(owner)
        assertFalse(access.isCurrent(owner, lease))
        assertNull(access.claim(owner, request))
    }

    @Test fun globalRevocationRejectsPendingApprovedAndActiveStates() {
        val pending = access.begin(owner).id
        access.revokeAll()
        assertFalse(access.approve(owner, pending))
        val approved = access.begin(owner).id
        assertTrue(access.approve(owner, approved))
        access.revokeAll()
        assertNull(access.claim(owner, approved))
        val active = access.begin(owner).id
        assertTrue(access.approve(owner, active))
        val lease = checkNotNull(access.claim(owner, active))
        access.revokeAll()
        assertFalse(access.isCurrent(owner, lease))
        assertFalse(access.approve(owner, active))
    }

    @Test fun requestExpiryAppliesToApprovalAndClaimWithoutExtendingOnApproval() {
        val request = access.begin(owner).id
        now += 299_999
        assertTrue(access.approve(owner, request))
        now += 1
        assertNull(access.claim(owner, request))
        val expired = access.begin(owner).id
        now += 300_000
        assertFalse(access.approve(owner, expired))
        assertNull(access.claim(owner, expired))
    }

    @Test fun justBeforeExpiryCanClaimAndClockRollbackDeniesPendingAuthorization() {
        val request = access.begin(owner).id
        now += 299_999
        assertTrue(access.approve(owner, request))
        assertNotNull(access.claim(owner, request))
        val backward = access.begin(owner).id
        now -= 1
        assertFalse(access.approve(owner, backward))
        assertNull(access.claim(owner, backward))
    }

    @Test fun constructedLeaseIsNeverCurrentAndRealLeaseCannotTransferBetweenOwners() {
        assertFalse(access.isCurrent(owner, SavedEndpointAccess.Lease()))
        val request = access.begin(owner).id
        assertTrue(access.approve(owner, request))
        val lease = checkNotNull(access.claim(owner, request))
        assertFalse(access.isCurrent(Any(), lease))
        assertTrue(access.isCurrent(owner, lease))
    }

    @Test fun keyRevalidationRetainsOnlyCorrelationAndClearsEarlierApproval() {
        val request = access.begin(owner).id
        access.revokeAuthorization(owner)
        assertNull(access.claim(owner, request))
        assertTrue(access.approve(owner, request))
        access.revokeAuthorization(owner)
        assertNull(access.claim(owner, request))
        assertTrue(access.approve(owner, request))
        assertNotNull(access.claim(owner, request))
    }

    @Test fun keyRevalidationRevokesActiveAuthorityWithoutRecreatingTheConsumedRequest() {
        val request = access.begin(owner).id
        assertTrue(access.approve(owner, request))
        val lease = checkNotNull(access.claim(owner, request))
        access.revokeAuthorization(owner)
        assertFalse(access.isCurrent(owner, lease))
        assertFalse(access.approve(owner, request))
        assertNull(access.claim(owner, request))
    }

    @Test fun anotherOwnerCannotRevokeAuthorizationOfPendingApprovedOrActiveState() {
        val otherOwner = Any()
        val request = access.begin(owner).id
        access.revokeAuthorization(otherOwner)
        assertTrue(access.approve(owner, request))
        access.revokeAuthorization(otherOwner)
        val lease = checkNotNull(access.claim(owner, request))
        access.revokeAuthorization(otherOwner)
        assertTrue(access.isCurrent(owner, lease))
    }

    @Test fun keyRevalidationNeverExtendsExpiryOrAcceptsAClockRollback() {
        val request = access.begin(owner).id
        now += 299_998
        assertTrue(access.approve(owner, request))
        access.revokeAuthorization(owner)
        now += 1
        assertTrue(access.approve(owner, request))
        now += 1
        assertNull(access.claim(owner, request))
        val expired = access.begin(owner).id
        now += 300_000
        access.revokeAuthorization(owner)
        assertFalse(access.approve(owner, expired))
        val backward = access.begin(owner).id
        now -= 1
        access.revokeAuthorization(owner)
        assertFalse(access.approve(owner, backward))
    }
}
