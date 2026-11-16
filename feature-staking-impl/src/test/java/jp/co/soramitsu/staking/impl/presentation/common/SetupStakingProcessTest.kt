package jp.co.soramitsu.staking.impl.presentation.common

import io.mockk.mockk
import java.math.BigDecimal
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Filters
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Sorting
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit.SelectionMethod
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.SelectBlockProducersStep
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.SetupStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStakingProcessTest {

    private val amount = BigDecimal("12.5")
    private val validator = mockk<Validator>(relaxed = true)
    private val collator = mockk<Collator>(relaxed = true)

    @Test
    fun initialStepKeepsTypeAndOffersExistingStashFlows() {
        val initial = SetupStakingProcess.Initial(StakingType.RELAYCHAIN)
        assertEquals(StakingType.RELAYCHAIN, initial.stakingType)
        assertEquals(BigDecimal.TEN, initial.defaultAmount)
        val stash = SetupStep.Stash(amount)
        assertSame(stash, initial.fullFlow(stash))
        assertSame(SelectBlockProducersStep.Payload.ExistingStash, initial.existingStashFlow().payload)
        assertSame(SelectBlockProducersStep.Payload.Validators, initial.changeValidatorsFlow().payload)
    }

    @Test
    fun stashStepMovesToValidatorSelectionAndBack() {
        val stash = SetupStep.Stash(amount)
        assertEquals(amount, stash.amount)
        assertEquals(StakingType.RELAYCHAIN, stash.previous().stakingType)

        val next = stash.next(SetupStep.Payload.RelayChain(amount, RewardDestination.Restake, "me"))
        next as SelectBlockProducersStep.Validators
        val payload = next.payload as SelectBlockProducersStep.Payload.Relaychain
        assertEquals(amount, payload.amount)
        assertEquals("me", payload.controllerAddress)
        assertSame(RewardDestination.Restake, payload.rewardDestination)
        assertEquals(StakingType.RELAYCHAIN, (next.previous() as SetupStakingProcess.Initial).stakingType)
        assertEquals(setOf(Filters.HavingOnChainIdentity, Filters.NotSlashedFilter, Filters.NotOverSubscribed), next.filtersSet)
        assertTrue(next.quickFilters.isEmpty())
        assertEquals(setOf(Sorting.EstimatedRewards, Sorting.TotalStake, Sorting.ValidatorsOwnStake), next.sortingSet)
    }

    @Test(expected = IllegalArgumentException::class)
    fun stashStepRejectsParachainPayload() {
        SetupStep.Stash(amount).next(SetupStep.Payload.Parachain(amount, "me"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun parachainStepRejectsRelayPayload() {
        SetupStep.Parachain(amount).next(SetupStep.Payload.RelayChain(amount, RewardDestination.Restake, "me"))
    }

    @Test
    fun parachainStepMovesToCollatorsAndReadyToSubmit() {
        val step = SetupStep.Parachain(amount)
        assertEquals(StakingType.PARACHAIN, step.previous().stakingType)
        val collators = step.next(SetupStep.Payload.Parachain(amount, "me")) as SelectBlockProducersStep.Collators
        assertEquals("me", collators.payload.accountAddress)
        assertEquals(setOf(Filters.HavingOnChainIdentity, Filters.WithRelevantBond), collators.filtersSet)
        assertEquals(collators.filtersSet, collators.quickFilters)
        assertEquals(5, collators.sortingSet.size)

        val ready = collators.next(listOf(collator), SelectionMethod.RECOMMENDED) as ReadyToSubmit.Parachain
        val payload = ready.payload as ReadyToSubmit.Payload.Parachain
        assertEquals(amount, payload.amount)
        assertEquals("me", payload.currentAccountAddress)
        assertEquals(listOf(collator), payload.blockProducers)
        assertEquals(SelectionMethod.RECOMMENDED, payload.selectionMethod)

        val back = ready.previous() as SelectBlockProducersStep.Collators
        assertEquals(amount, back.payload.amount)
        assertEquals(StakingType.PARACHAIN, ready.finish().stakingType)

        val changed = ready.changeBlockProducers(emptyList(), SelectionMethod.CUSTOM)
        assertTrue(changed.payload.blockProducers.isEmpty())
        assertEquals(SelectionMethod.CUSTOM, changed.payload.selectionMethod)
        assertEquals("me", (changed.payload as ReadyToSubmit.Payload.Parachain).currentAccountAddress)
    }

    @Test
    fun validatorPayloadsMapToMatchingReadyPayloads() {
        val full = SelectBlockProducersStep.Validators(SelectBlockProducersStep.Payload.Full(amount, RewardDestination.Restake, "c"))
        assertEquals(amount, (full.previous() as SetupStep.Stash).amount)
        val readyFull = full.next(listOf(validator), SelectionMethod.CUSTOM) as ReadyToSubmit.Stash
        val fullPayload = readyFull.payload as ReadyToSubmit.Payload.Full
        assertEquals("c", fullPayload.currentAccountAddress)
        assertSame(RewardDestination.Restake, fullPayload.rewardDestination)
        assertEquals(amount, fullPayload.amount)
        val backFull = (readyFull.previous() as SelectBlockProducersStep.Validators).payload as SelectBlockProducersStep.Payload.Full
        assertEquals("c", backFull.controllerAddress)
        assertEquals(StakingType.RELAYCHAIN, readyFull.finish().stakingType)
        assertEquals(3, readyFull.filtersSet.size)
        assertTrue(readyFull.quickFilters.isEmpty())
        assertEquals(3, readyFull.sortingSet.size)
        val changedFull = readyFull.changeBlockProducers(emptyList(), SelectionMethod.RECOMMENDED)
        assertTrue(changedFull.payload is ReadyToSubmit.Payload.Full)
        assertTrue(changedFull.payload.blockProducers.isEmpty())

        val existing = SelectBlockProducersStep.Validators(SelectBlockProducersStep.Payload.ExistingStash)
        assertEquals(StakingType.RELAYCHAIN, (existing.previous() as SetupStakingProcess.Initial).stakingType)
        val readyExisting = existing.next(listOf(validator), SelectionMethod.CUSTOM) as ReadyToSubmit.Stash
        assertTrue(readyExisting.payload is ReadyToSubmit.Payload.ExistingStash)
        assertSame(SelectBlockProducersStep.Payload.ExistingStash, (readyExisting.previous() as SelectBlockProducersStep.Validators).payload)
        assertTrue(readyExisting.changeBlockProducers(emptyList(), SelectionMethod.CUSTOM).payload is ReadyToSubmit.Payload.ExistingStash)

        val change = SelectBlockProducersStep.Validators(SelectBlockProducersStep.Payload.Validators)
        assertTrue((change.next(listOf(validator), SelectionMethod.CUSTOM) as ReadyToSubmit.Stash).payload is ReadyToSubmit.Payload.ExistingStash)

        val relay = SelectBlockProducersStep.Validators(SelectBlockProducersStep.Payload.Relaychain(amount, RewardDestination.Restake, "c"))
        val readyRelay = relay.next(listOf(validator), SelectionMethod.RECOMMENDED) as ReadyToSubmit.Stash
        val relayPayload = readyRelay.payload as ReadyToSubmit.Payload.RelayChain
        assertEquals("c", relayPayload.controllerAddress)
        assertEquals(amount, relayPayload.amount)
        assertSame(RewardDestination.Restake, relayPayload.rewardDestination)
        assertSame(SelectBlockProducersStep.Payload.Validators, (readyRelay.previous() as SelectBlockProducersStep.Validators).payload)
        val changedRelay = readyRelay.changeBlockProducers(emptyList(), SelectionMethod.CUSTOM).payload as ReadyToSubmit.Payload.RelayChain
        assertEquals("c", changedRelay.controllerAddress)
    }

    @Test(expected = IllegalStateException::class)
    fun collatorPayloadCannotSelectValidators() {
        SelectBlockProducersStep.Validators(SelectBlockProducersStep.Payload.Collators).next(emptyList(), SelectionMethod.CUSTOM)
    }

    @Test
    fun sharedStateReadsTypedSteps() {
        val shared = SetupStakingSharedState()
        assertEquals(StakingType.PARACHAIN, shared.get<SetupStakingProcess.Initial>().stakingType)
        assertEquals(null, shared.getOrNull<SetupStep.Stash>())
        shared.setupStakingProcess.value = SetupStep.Stash(amount)
        assertEquals(amount, shared.getOrNull<SetupStep.Stash>()?.amount)
    }
}
