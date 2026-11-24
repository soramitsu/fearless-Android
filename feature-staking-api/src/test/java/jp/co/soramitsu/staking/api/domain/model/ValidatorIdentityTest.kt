package jp.co.soramitsu.staking.api.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ValidatorIdentityTest {

    @Test
    fun shouldComposeChildNameAndDelegateParentIdentityDetails() {
        val parent = RootIdentity("Parent", "Legal", "web", "riot", "email", "fingerprint", "image", "twitter")
        val child = ChildIdentity("Child", parent)

        assertEquals("Parent / Child", child.display)
        assertSame(parent, child.parentIdentity)
        assertEquals(parent.legal, child.legal)
        assertEquals(parent.web, child.web)
        assertEquals(parent.riot, child.riot)
        assertEquals(parent.email, child.email)
        assertEquals(parent.pgpFingerprint, child.pgpFingerprint)
        assertEquals(parent.image, child.image)
        assertEquals(parent.twitter, child.twitter)
        assertEquals("Parent / ", ChildIdentity(null, parent).display)
    }

    @Test
    fun shouldExposeNoFieldsForAnEmptyIdentity() {
        val identity = RootIdentity.empty()
        listOf(identity.display, identity.legal, identity.web, identity.riot, identity.email,
            identity.pgpFingerprint, identity.image, identity.twitter).forEach { assertNull(it) }
    }

    @Test
    fun shouldOrderNominationGroupsByStatusWithoutLosingCounts() {
        val waiting = NominatedValidator.Status.Group.WaitingForNextEra(16, 4)
        val groups = listOf(
            waiting, NominatedValidator.Status.Group.Inactive(3),
            NominatedValidator.Status.Group.Elected(2), NominatedValidator.Status.Group.Active(1)
        )
        val ordered = groups.sortedWith(NominatedValidator.Status.Group.COMPARATOR)

        assertEquals(listOf(0, 1, 2, 3), ordered.map { it.position })
        assertEquals(listOf(1, 2, 3, 4), ordered.map { it.numberOfValidators })
        assertEquals(16, waiting.maxValidatorsPerNominator)
    }
}
