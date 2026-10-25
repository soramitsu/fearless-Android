package jp.co.soramitsu.coredb.recovery.decision

import org.junit.Assert.*
import org.junit.Test
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentPolicy.Action
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentPolicy.Kind
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentPolicy.Owner
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentPolicy.Projection

class EndpointAttachmentPolicyTest {
    private val pending = Owner(-9003, 8, 0, 0, true)
    private val inactive = Projection(0, 0, true)
    private fun plan(action: Action = Action.ADD_NEW, owner: Owner? = null, projection: Projection? = null,
                     id: Long? = null, revision: Long? = null) = EndpointAttachmentPolicy.plan(action, owner, projection, id, revision)

    @Test fun absentKeyCreatesOnlyAnInactiveRevisionOneOwner() {
        assertEquals(Kind.INSERT_INACTIVE, plan().kind)
        assertEquals(1L, plan().nextRevision)
        assertNull(plan().ownerId)
    }
    @Test fun exactExistingPairIsObservationWithoutRevisionMutationIncludingMaxRevision() {
        for (active in 0L..1L) {
            val result = plan(owner = pending.copy(revision = Long.MAX_VALUE, attached = 1), projection = inactive.copy(isActive = active))
            assertEquals(if (active == 1L) Kind.ALREADY_ACTIVE else Kind.ALREADY_INACTIVE, result.kind)
            assertNull(result.nextRevision)
            assertEquals(-9003L, result.ownerId)
        }
    }
    @Test fun explicitSamePendingOwnerReattachesOnlyAtExactRevisionAndPreservesSignedId() {
        val result = plan(Action.REATTACH_EXACT_OWNED, pending, id = -9003, revision = 8)
        assertEquals(Kind.REATTACH_INACTIVE, result.kind)
        assertEquals(-9003L, result.ownerId)
        assertEquals(9L, result.nextRevision)
    }
    @Test fun reservedDefaultOrCompetingProjectionNeverBecomesAnInsert() {
        assertThrows(IllegalStateException::class.java) { plan(projection = inactive) }
        assertThrows(IllegalStateException::class.java) { plan(owner = pending.copy(attached = 1), projection = inactive.copy(isDefault = 1)) }
        assertThrows(IllegalStateException::class.java) { plan(owner = pending.copy(attached = 1), projection = inactive.copy(payloadMatches = false)) }
        assertThrows(IllegalStateException::class.java) { plan(owner = pending.copy(attached = 1)) }
    }
    @Test fun pendingOwnerRequiresExplicitSameSourceAndExactCandidate() {
        assertThrows(IllegalStateException::class.java) { plan(owner = pending, id = -9003, revision = 8) }
        for ((id, revision) in listOf(null to null, 1L to 8L, -9003L to 9L)) {
            assertThrows(IllegalStateException::class.java) { plan(Action.REATTACH_EXACT_OWNED, pending, id = id, revision = revision) }
        }
        assertThrows(IllegalStateException::class.java) { plan(Action.REATTACH_EXACT_OWNED, pending.copy(payloadMatches = false), id = -9003, revision = 8) }
        assertThrows(IllegalStateException::class.java) { plan(Action.REATTACH_EXACT_OWNED, pending, inactive, -9003, 8) }
        assertThrows(IllegalStateException::class.java) { plan(Action.REATTACH_EXACT_OWNED) }
    }
    @Test fun tombstoneNeverRevivesAndMalformedFlagsOrRevisionReject() {
        for (owner in listOf(pending.copy(deleted = 1), pending.copy(attached = 1, deleted = 1),
            pending.copy(revision = 0), pending.copy(revision = -1), pending.copy(attached = 2), pending.copy(deleted = -1))) {
            assertThrows(IllegalStateException::class.java) { plan(Action.REATTACH_EXACT_OWNED, owner, id = -9003, revision = 8) }
        }
        for (projection in listOf(inactive.copy(isActive = 2), inactive.copy(isDefault = -1))) {
            assertThrows(IllegalStateException::class.java) { plan(owner = pending.copy(attached = 1), projection = projection) }
        }
    }
    @Test fun reattachOverflowRejectsAndIncompleteOwnedSourceMetadataRejects() {
        assertThrows(IllegalStateException::class.java) { plan(Action.REATTACH_EXACT_OWNED, pending.copy(revision = Long.MAX_VALUE), id = -9003, revision = Long.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { plan(id = -9003) }
        assertThrows(IllegalArgumentException::class.java) { plan(revision = 1) }
        assertThrows(IllegalArgumentException::class.java) { plan(id = -9003, revision = 0) }
    }
}
