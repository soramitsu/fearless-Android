package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.staking.impl.domain.validators.current.search.BlockedValidatorException
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class ProducerSelectionMutationContractTest {
    @get:Rule val environment: TestRule = SearchContractEnvironment()

    @Test fun addingRelayValidatorPreservesBondAndPayoutContext() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        assertTrue(f.choose(f.relay.addresses[1]).isSuccess)
        val payload = f.relayPayload() as SetupStakingProcess.ReadyToSubmit.Payload.RelayChain
        assertEquals(f.validators, payload.blockProducers)
        assertEquals(BigDecimal("12.34"), payload.amount)
        assertSame(f.destination, payload.rewardDestination)
        assertEquals("controller", payload.controllerAddress)
        assertEquals(SetupStakingProcess.ReadyToSubmit.SelectionMethod.CUSTOM, payload.selectionMethod)
    }

    @Test fun tappingAnAlreadySelectedRelayValidatorRemovesIt() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        assertTrue(f.choose(f.relay.addresses[0]).isSuccess)
        assertTrue(f.relayPayload().blockProducers.isEmpty())
    }

    @Test fun blockedValidatorCannotChangeTheExistingSelection() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        whenever(f.validatorProvider.getValidators(any(), any())).thenReturn(listOf(f.relay.validator(1, blocked = true)))
        val before = f.process.setupStakingProcess.value
        assertSame(BlockedValidatorException, f.choose(f.relay.addresses[1]).exceptionOrNull())
        assertSame(before, f.process.setupStakingProcess.value)
    }

    @Test fun unknownRelaySelectionDoesNotDiscardExistingTargets() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val before = f.process.setupStakingProcess.value
        assertEquals("cannot find validator", runCatching { f.choose(f.relay.addresses[2]) }.exceptionOrNull()?.message)
        assertSame(before, f.process.setupStakingProcess.value)
    }

    @Test fun parachainSelectionReplacesTheSingleTargetAndKeepsBondContext() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        assertTrue(f.choose(f.para.hex[0]).isSuccess)
        val payload = f.paraPayload() as SetupStakingProcess.ReadyToSubmit.Payload.Parachain
        assertEquals(listOf(f.collators.getValue(f.para.hex[0])), payload.blockProducers)
        assertEquals(BigDecimal("12.34"), payload.amount)
        assertEquals("delegator", payload.currentAccountAddress)
        assertEquals(SetupStakingProcess.ReadyToSubmit.SelectionMethod.CUSTOM, payload.selectionMethod)
    }

    @Test fun missingParachainSelectionCannotSilentlyClearTheExistingTarget() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        val before = f.process.setupStakingProcess.value
        val outcome = runCatching { f.choose(f.para.hex[2]) }
        assertTrue(outcome.isFailure || outcome.getOrNull()?.isFailure == true)
        assertSame(before, f.process.setupStakingProcess.value)
    }

    @Test fun equalRelayAccountFromARefreshedObjectTogglesInsteadOfDuplicating() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        f.readyRelay(listOf(f.relay.validator()))
        assertTrue(f.choose(f.relay.addresses[0]).isSuccess)
        assertTrue(f.relayPayload().blockProducers.isEmpty())
    }

    @Test fun remoteRelaySearchResultCanBeSelectedWithoutBeingInElectedCatalogue() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val remote = f.relay.validator(2)
        whenever(f.validatorProvider.getValidatorWithoutElectedInfo(any(), any())).thenReturn(remote)
        assertEquals(remote.address, f.interactor.getBlockProducerByAddress(remote.address)?.address)
        assertTrue(f.choose(remote.address).isSuccess)
        assertEquals(listOf(f.relay.addresses[0], remote.address), f.relayPayload().blockProducers.map { it.address })
    }
}
