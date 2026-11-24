package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.core.models.Asset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ProducerSearchCatalogueContractTest {
    @get:Rule val environment: TestRule = SearchContractEnvironment()

    @Test fun relayFractionalApyRendersAsTwelvePointFivePercent() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        assertEquals("12.5%", f.rows()[0].rewardsPercent)
        assertEquals("Alice", f.rows()[0].name)
        assertEquals(f.relay.addresses[0], f.rows()[0].address)
    }

    @Test fun parachainPercentageApyIsNotMultipliedAgain() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        assertEquals("12.5%", f.rows()[0].rewardsPercent)
        assertEquals("Collator Alpha", f.rows()[0].name)
    }

    @Test fun relayMissingIdentityAndElectionUseAddressAndZeroRewards() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        whenever(f.validatorProvider.getValidators(any(), any())).thenReturn(listOf(f.relay.validator(1, apy = null)))
        val row = f.rows().single()
        assertEquals(f.relay.addresses[1], row.name)
        assertEquals("0%", row.rewardsPercent)
    }

    @Test fun parachainMissingIdentityAndRewardUseAddressAndZeroRewards() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        whenever(f.collatorProvider.getCollators(any(), anyOrNull())).thenReturn(mapOf(f.para.hex[1] to f.para.collator(1, null)))
        val row = f.rows().single()
        assertEquals(f.para.hex[1], row.name)
        assertEquals("0%", row.rewardsPercent)
    }

    @Test fun catalogueRowsPreserveProviderOrderWithoutSortingByIdentity() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        whenever(f.validatorProvider.getValidators(any(), any())).thenReturn(f.validators.reversed())
        assertEquals(f.relay.addresses.take(2).reversed(), f.rows().map { it.address })
    }

    @Test fun unsupportedStakingFailsBeforeReadingEitherProvider() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        whenever(f.asset.staking).thenReturn(Asset.StakingType.UNSUPPORTED)
        assertEquals("Wrong staking type", runCatching { f.rows() }.exceptionOrNull()?.message)
        verifyNoInteractions(f.validatorProvider, f.collatorProvider)
    }

    @Test fun repeatedCatalogueReadsShareOneLiveLifecycleComputation() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        assertEquals(f.rows(), f.rows())
        verify(f.validatorProvider, times(1)).getValidators(any(), any())
        verifyNoInteractions(f.collatorProvider)
    }

    @Test fun destroyedLifecycleEvictsCatalogueForTheNextScreen() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        f.rows()
        f.lifecycle.destroy()
        whenever(f.validatorProvider.getValidators(any(), any())).thenReturn(listOf(f.validators[1]))
        val rows = f.interactor.getBlockProducers(SearchContractLifecycle())
        assertEquals(listOf(f.relay.addresses[1]), rows.map { it.address })
        verify(f.validatorProvider, times(2)).getValidators(any(), any())
    }
}
