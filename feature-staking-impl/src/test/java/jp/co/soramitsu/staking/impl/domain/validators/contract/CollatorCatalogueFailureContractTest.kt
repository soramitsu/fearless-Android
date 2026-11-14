package jp.co.soramitsu.staking.impl.domain.validators.contract

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class CollatorCatalogueFailureContractTest {
    @Test fun nextLookupUsesNewlySelectedWalletRatherThanCachedDelegations() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        f.used(0)
        assertEquals(listOf(f.hex[1], f.hex[2]), f.provider.getCollators(f.chain).keys.toList())
        f.used(1)
        assertEquals(listOf(f.hex[0], f.hex[2]), f.provider.getCollators(f.chain).keys.toList())
        verify(f.accounts, times(2)).getSelectedMetaAccount()
    }

    @Test fun zeroRewardRemainsExactZeroRatherThanMissing() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        whenever(f.calculator.getApy(any())).thenReturn(mapOf(f.hex[0] to BigDecimal.ZERO))
        assertEquals(BigDecimal.ZERO, f.provider.getCollators(f.chain)[f.hex[0]]?.apy)
    }

    @Test fun accountFailureCannotPublishPartialCollators() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        val failure = IllegalStateException("account unavailable")
        whenever(f.accounts.getSelectedMetaAccount()).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.provider.getCollators(f.chain) }.exceptionOrNull())
    }

    @Test fun delegationFailureCannotPublishPartialCollators() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        val failure = IllegalStateException("delegation unavailable")
        whenever(f.repository.getDelegatorState(f.chainId, f.accountId)).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.provider.getCollators(f.chain) }.exceptionOrNull())
    }

    @Test fun candidateFailureCannotPublishPartialCollators() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        val failure = IllegalStateException("candidate unavailable")
        whenever(f.repository.getCandidateInfos(any(), any())).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.provider.getCollators(f.chain) }.exceptionOrNull())
    }

    @Test fun identityFailureCannotPublishPartialCollators() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        val failure = IllegalStateException("identity unavailable")
        whenever(f.identities.getIdentitiesFromIdsBytes(any(), any())).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.provider.getCollators(f.chain) }.exceptionOrNull())
    }

    @Test fun rewardCancellationPropagatesInsteadOfReturningUnknownRewards() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        val failure = CancellationException("left collator screen")
        whenever(f.calculator.getApy(any())).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.provider.getCollators(f.chain) }.exceptionOrNull())
    }

    @Test fun candidateCancellationStopsBeforeIdentityAndRewardRequests() = runTest {
        val f = CollatorCatalogueFixture()
        f.defaults()
        val failure = CancellationException("changed chain")
        whenever(f.repository.getCandidateInfos(any(), any())).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.provider.getCollators(f.chain) }.exceptionOrNull())
        verifyNoInteractions(f.identities, f.calculator)
    }
}
