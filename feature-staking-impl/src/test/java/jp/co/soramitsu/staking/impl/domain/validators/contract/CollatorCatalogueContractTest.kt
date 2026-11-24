package jp.co.soramitsu.staking.impl.domain.validators.contract

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class CollatorCatalogueContractTest {
    @Test fun selectedCandidatesKeepHexIdentityAndOrder() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        assertEquals(f.hex, f.provider.getCollators(f.chain).keys.toList())
        verify(f.identities).getIdentitiesFromIdsBytes(
            eq(f.chain),
            argThat {
            indices.all { this[it].contentEquals(f.ids[it]) }
        }
        )
    }

    @Test fun delegatedCandidateIsExcludedByByteContentNotArrayReference() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        f.used(1)
        assertEquals(listOf(f.hex[0], f.hex[2]), f.provider.getCollators(f.chain).keys.toList())
        verify(f.repository).getCandidateInfos(eq(f.chainId), argThat { size == 2 && none { it.contentEquals(f.ids[1]) } })
    }

    @Test fun allAlreadyDelegatedCandidatesProduceAnEmptyCatalogue() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        f.used(0, 1, 2)
        assertTrue(f.provider.getCollators(f.chain).isEmpty())
        verify(f.calculator).getApy(emptyList())
    }

    @Test fun missingWalletAccountDoesNotReadAnotherDelegatorState() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        whenever(f.wallet.ethereumAddress).thenReturn(null)
        assertEquals(3, f.provider.getCollators(f.chain).size)
        verify(f.repository, never()).getDelegatorState(any(), any())
    }

    @Test fun missingSelectedCandidatesReturnsEmptyWithoutCandidateOrIdentityReads() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        whenever(f.repository.getSelectedCandidates(f.chainId)).thenReturn(null)
        assertTrue(f.provider.getCollators(f.chain).isEmpty())
        verify(f.repository, never()).getCandidateInfos(any(), any())
        verifyNoInteractions(f.identities, f.calculator)
    }

    @Test fun candidateEnrichmentPreservesStakeCapacityAndRewardUnits() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        val actual = requireNotNull(f.provider.getCollators(f.chain)[f.hex[0]])
        assertEquals("300", actual.bond.toString())
        assertEquals("1900", actual.totalCounted.toString())
        assertEquals("500", actual.lowestTopDelegationAmount.toString())
        assertEquals("400", actual.highestBottomDelegationAmount.toString())
        assertEquals("100", actual.lowestBottomDelegationAmount.toString())
        assertEquals("4", actual.delegationCount.toString())
        assertSame(f.candidate.topCapacity, actual.topCapacity)
        assertSame(f.candidate.bottomCapacity, actual.bottomCapacity)
        assertSame(f.candidate.status, actual.status)
        assertEquals("pending bond", actual.request)
        assertEquals(BigDecimal("12.5"), actual.apy)
        assertSame(f.identity, actual.identity)
    }

    @Test fun missingRewardAndIdentityRemainUnknownForTheMatchingCandidate() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        val actual = requireNotNull(f.provider.getCollators(f.chain)[f.hex[1]])
        assertNull(actual.apy)
        assertNull(actual.identity)
        assertEquals(f.hex[1], actual.address)
    }

    @Test fun identityAndRewardsFollowTheFilteredAccountSet() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        f.used(0)
        f.provider.getCollators(f.chain)
        verify(f.identities).getIdentitiesFromIdsBytes(
            eq(f.chain),
            argThat {
            size == 2 && none { it.contentEquals(f.ids[0]) }
        }
        )
        verify(f.calculator).getApy(argThat { size == 2 && none { it.contentEquals(f.ids[0]) } })
    }
}
