package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.domain.model.Collator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ProducerSearchBoundaryContractTest {
    @get:Rule val environment: TestRule = SearchContractEnvironment()

    @Test fun relayAndParachainCataloguesUseDistinctCacheEntries() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        f.rows()
        f.selection.value = SingleAssetSharedState.AssetWithChain(f.para.chain, f.para.asset)
        assertEquals(f.para.hex.take(2), f.rows().map { it.address })
        verify(f.validatorProvider, times(1)).getValidators(any(), any())
        verify(f.collatorProvider, times(1)).getCollators(any(), anyOrNull())
    }

    @Test fun relaySelectionUsesCachedSnapshotUntilLifecycleEnds() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        f.rows()
        whenever(f.validatorProvider.getValidators(any(), any())).thenReturn(emptyList())
        assertTrue(f.choose(f.relay.addresses[1]).isSuccess)
        assertEquals(f.validators, f.relayPayload().blockProducers)
        verify(f.validatorProvider, times(1)).getValidators(any(), any())
    }

    @Test fun parachainNavigationUsesCachedSnapshotUntilLifecycleEnds() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        f.rows()
        whenever(f.collatorProvider.getCollators(any(), anyOrNull())).thenReturn(emptyMap())
        val opened = mutableListOf<Collator>()
        f.interactor.navigateBlockProducerInfo(f.para.hex[0], f.lifecycle, opened::add, { fail("Wrong route") })
        assertSame(f.collators.getValue(f.para.hex[0]), opened.single())
        verify(f.collatorProvider, times(1)).getCollators(any(), anyOrNull())
    }

    @Test fun relayAddressSearchWithoutIdentityUsesItsAddressAndZeroElectionReward() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        whenever(f.validatorProvider.getValidatorWithoutElectedInfo(any(), any())).thenReturn(f.relay.validator(1, apy = null))
        val row = requireNotNull(f.interactor.getBlockProducerByAddress(f.relay.addresses[1]))
        assertEquals(f.relay.addresses[1], row.name)
        assertEquals("0%", row.rewardsPercent)
    }

    @Test fun unsupportedValidAddressSearchFailsWithoutProviderRequests() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        whenever(f.asset.staking).thenReturn(Asset.StakingType.UNSUPPORTED)
        assertEquals(
            "Wrong staking type",
            runCatching {
            f.interactor.getBlockProducerByAddress(f.relay.addresses[0])
        }.exceptionOrNull()?.message
        )
        verifyNoInteractions(f.validatorProvider, f.collatorProvider)
    }

    @Test fun unsupportedSelectionLeavesTheEntireSetupPayloadUnchanged() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val before = f.process.setupStakingProcess.value
        whenever(f.asset.staking).thenReturn(Asset.StakingType.UNSUPPORTED)
        assertEquals("Wrong staking type", runCatching { f.choose(f.relay.addresses[0]) }.exceptionOrNull()?.message)
        assertSame(before, f.process.setupStakingProcess.value)
        verifyNoInteractions(f.validatorProvider, f.collatorProvider)
    }

    @Test fun unsupportedInfoNavigationDoesNotInvokeEitherRoute() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        whenever(f.asset.staking).thenReturn(Asset.StakingType.UNSUPPORTED)
        val result = runCatching {
            f.interactor.navigateBlockProducerInfo(f.relay.addresses[0], f.lifecycle, {
                fail("Unexpected route")
            }, { fail("Unexpected route") })
        }
        assertEquals("Wrong staking type", result.exceptionOrNull()?.message)
    }

    @Test fun repeatedParachainSelectionKeepsOneTargetWithoutTogglingItAway() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        assertTrue(f.choose(f.para.hex[0]).isSuccess)
        assertTrue(f.choose(f.para.hex[0]).isSuccess)
        assertEquals(listOf(f.para.hex[0]), f.paraPayload().blockProducers.map { it.address })
    }
}
