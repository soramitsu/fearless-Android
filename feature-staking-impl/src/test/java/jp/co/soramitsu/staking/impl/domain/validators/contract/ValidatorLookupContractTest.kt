package jp.co.soramitsu.staking.impl.domain.validators.contract

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class ValidatorLookupContractTest {
    @Test fun identityFailureCannotReturnAPartialCatalogue() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        val failure = IllegalStateException("identity unavailable")
        whenever(f.identities.getIdentitiesFromIds(any(), any())).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.elected() }.exceptionOrNull())
    }

    @Test fun slashesFailureCannotReturnAPartialCatalogue() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        val failure = IllegalStateException("slashes unavailable")
        whenever(f.repository.getSlashes(any(), any())).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.elected() }.exceptionOrNull())
    }

    @Test fun addressLookupUsesOnlyTheRequestedAccountForAllEnrichment() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        val actual = f.provider.getValidatorWithoutElectedInfo(f.chain, f.addresses[0])
        assertEquals(f.hex[0], actual.accountIdHex)
        assertEquals(f.addresses[0], actual.address)
        assertSame(f.prefs, actual.prefs)
        assertSame(f.identity, actual.identity)
        assertTrue(actual.slashed)
        assertNull(actual.electedInfo)
        verifyNoInteractions(f.rewards)
        verify(f.repository).getSlashes(f.chainId, listOf(f.hex[0]))
    }

    @Test fun addressLookupWithoutPreferencesStillRetainsIdentityForSearchRejection() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        whenever(f.repository.getValidatorPrefs(f.chainId, listOf(f.hex[0]))).thenReturn(emptyMap())
        val actual = f.provider.getValidatorWithoutElectedInfo(f.chain, f.addresses[0])
        assertNull(actual.prefs)
        assertSame(f.identity, actual.identity)
        assertEquals(f.hex[0], actual.accountIdHex)
    }

    @Test fun invalidLookupAddressFailsBeforeAnyRepositoryRead() = runTest {
        val f = ValidatorCatalogueFixture()
        f.defaults()
        assertNotNull(runCatching { f.provider.getValidatorWithoutElectedInfo(f.chain, "invalid") }.exceptionOrNull())
        verify(f.repository, never()).getValidatorPrefs(any(), any())
        verifyNoInteractions(f.identities, f.rewards)
    }

    @Test fun createSoraReceivesTheUtilityAssetAndExactTargetSet() = runTest {
        val f = ValidatorCatalogueFixture(jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId)
        f.defaults()
        assertEquals(BigDecimal("0.125"), f.elected().first().electedInfo?.apy)
        verify(f.rewards).createSora(eq(f.asset), argThat { single().accountIdHex == f.hex[0] }, isNull())
        verify(f.rewards, never()).createManual(any(), any())
    }

    @Test fun createReefReceivesTheUtilityAssetAndExactTargetSet() = runTest {
        val f = ValidatorCatalogueFixture(jp.co.soramitsu.runtime.multiNetwork.chain.model.reefChainId)
        f.defaults()
        assertEquals(BigDecimal("0.125"), f.elected().first().electedInfo?.apy)
        verify(f.rewards).createReef(eq(f.asset), argThat { single().accountIdHex == f.hex[0] }, isNull())
        verify(f.rewards, never()).createManual(any(), any())
    }

    @Test fun createTernoaReceivesTheUtilityAssetAndExactTargetSet() = runTest {
        val f = ValidatorCatalogueFixture(jp.co.soramitsu.runtime.multiNetwork.chain.model.ternoaChainId)
        f.defaults()
        assertEquals(BigDecimal("0.125"), f.elected().first().electedInfo?.apy)
        verify(f.rewards).createTernoa(eq(f.asset), argThat { single().accountIdHex == f.hex[0] })
        verify(f.rewards, never()).createManual(any(), any())
    }
}
