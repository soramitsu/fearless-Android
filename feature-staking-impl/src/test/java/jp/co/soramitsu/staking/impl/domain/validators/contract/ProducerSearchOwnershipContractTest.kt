package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.Validator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProducerSearchOwnershipContractTest {
    @get:Rule val environment: TestRule = SearchContractEnvironment()

    @Test fun relayInfoNavigationUsesTheExactChosenValidatorObject() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val values = mutableListOf<Validator>()
        f.interactor.navigateBlockProducerInfo(f.relay.addresses[1], f.lifecycle, { fail("Wrong destination") }, values::add)
        assertSame(f.validators[1], values.single())
    }

    @Test fun parachainInfoNavigationUsesTheExactChosenCollatorObject() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        val values = mutableListOf<Collator>()
        f.interactor.navigateBlockProducerInfo(f.para.hex[1], f.lifecycle, values::add, { fail("Wrong destination") })
        assertSame(f.collators.getValue(f.para.hex[1]), values.single())
    }

    @Test fun missingRelayInfoCannotNavigateToAnUnrelatedValidator() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val result = runCatching {
            f.interactor.navigateBlockProducerInfo(f.relay.addresses[2], f.lifecycle, {
                fail("Unexpected route")
            }, { fail("Unexpected route") })
        }
        assertEquals("cannot find validator", result.exceptionOrNull()?.message)
    }

    @Test fun missingParachainInfoCannotNavigateToAnUnrelatedCollator() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        val result = runCatching {
            f.interactor.navigateBlockProducerInfo(f.para.hex[2], f.lifecycle, {
                fail("Unexpected route")
            }, { fail("Unexpected route") })
        }
        assertEquals("cannot find collator", result.exceptionOrNull()?.message)
    }

    @Test fun changingRelayChainCannotReuseThePreviousChainsCachedCatalogue() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        f.rows()
        val other = ValidatorCatalogueFixture("another-relay")
        whenever(f.validatorProvider.getValidators(eq(other.chain), any())).thenReturn(listOf(other.validator(2)))
        f.selection.value = SingleAssetSharedState.AssetWithChain(other.chain, other.asset)
        assertEquals(listOf(other.addresses[2]), f.rows().map { it.address })
        verify(f.validatorProvider).getValidators(eq(other.chain), any())
    }

    @Test fun oneDestroyedOwnerCannotEvictAnotherLiveOwnersCatalogue() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        f.rows()
        val second = SearchContractLifecycle()
        f.interactor.getBlockProducers(second)
        f.lifecycle.destroy()
        f.interactor.getBlockProducers(second)
        verify(f.validatorProvider, times(1)).getValidators(any(), any())
        second.destroy()
        f.interactor.getBlockProducers(SearchContractLifecycle())
        verify(f.validatorProvider, times(2)).getValidators(any(), any())
    }

    @Test fun cancelledCatalogueLoadCannotMutateRelaySelection() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val before = f.process.setupStakingProcess.value
        val failure = CancellationException("left selection")
        whenever(f.validatorProvider.getValidators(any(), any())).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.choose(f.relay.addresses[1]) }.exceptionOrNull())
        assertSame(before, f.process.setupStakingProcess.value)
    }

    @Test fun remoteParachainFailureCannotEraseSelectedDelegation() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        val before = f.process.setupStakingProcess.value
        val failure = IllegalStateException("candidate offline")
        whenever(f.collatorProvider.getCollators(any(), anyOrNull())).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.choose(f.para.hex[0]) }.exceptionOrNull())
        assertSame(before, f.process.setupStakingProcess.value)
    }
}
