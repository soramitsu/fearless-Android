package jp.co.soramitsu.account.impl.domain.recovery

import org.junit.Assert.*
import org.junit.Test

class SavedEndpointAttachmentAccessTest {
    @Test fun everyLifecycleRetirementSynchronouslyRevokesDistinctAttachmentAuthority() {
        val actions: List<(SavedEndpointAccess, Any) -> Unit> = listOf(
            { a, o -> a.begin(o) }, { a, o -> a.revokeLease(o) },
            { a, o -> a.revokeAuthorization(o) }, { a, o -> a.revoke(o) }, { a, _ -> a.revokeAll() }
        )
        actions.forEach { retire ->
            val access = SavedEndpointAccess { 0L }
            val owner = Any()
            val request = access.begin(owner)
            assertNull(access.attachmentAuthority(owner, SavedEndpointAccess.Lease()))
            assertTrue(access.approve(owner, request.id))
            val lease = checkNotNull(access.claim(owner, request.id))
            val attachment = checkNotNull(access.attachmentAuthority(owner, lease))
            val offline = checkNotNull(access.decisionAuthority(owner, lease))
            assertNull(access.attachmentAuthority(Any(), lease))
            retire(access, owner)
            assertFalse(attachment.isCurrent())
            assertFalse(offline.isCurrent())
            assertNull(access.attachmentAuthority(owner, lease))
        }
    }

    @Test fun foreignOwnerCannotRevokeAndFreshLeaseNeverReactivatesPriorAuthority() {
        val access = SavedEndpointAccess { 0L }
        val owner = Any()
        fun claim(): SavedEndpointAccess.Lease {
            val request = access.begin(owner)
            check(access.approve(owner, request.id))
            return checkNotNull(access.claim(owner, request.id))
        }
        val old = checkNotNull(access.attachmentAuthority(owner, claim()))
        access.revoke(Any())
        access.revokeAuthorization(Any())
        access.revokeLease(Any())
        assertTrue(old.isCurrent())
        val fresh = checkNotNull(access.attachmentAuthority(owner, claim()))
        assertNotSame(old, fresh)
        assertFalse(old.isCurrent())
        assertTrue(fresh.isCurrent())
    }
}
