package jp.co.soramitsu.staking.impl.presentation.validators.change

import io.mockk.mockk
import java.math.BigDecimal
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit.SelectionMethod
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.SelectBlockProducersStep
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StateTransitionsCoverageTest {

    private val shared = SetupStakingSharedState()
    private val amount = BigDecimal("3")
    private val validator = mockk<Validator>(relaxed = true)
    private val collator = mockk<Collator>(relaxed = true)
    private val fullStep = SelectBlockProducersStep.Validators(
        SelectBlockProducersStep.Payload.Full(amount, RewardDestination.Restake, "me")
    )
    private val collatorStep = SelectBlockProducersStep.Collators(SelectBlockProducersStep.Payload.Parachain(amount, "me"))

    private fun current() = shared.setupStakingProcess.value

    @Test
    fun customValidatorsMoveSelectionStepToReadyAndChangeLaterSelection() {
        shared.setupStakingProcess.value = fullStep

        shared.setCustomValidators(listOf(validator))
        val ready = current() as ReadyToSubmit.Stash
        assertEquals(listOf(validator), ready.payload.blockProducers)
        assertEquals(SelectionMethod.CUSTOM, ready.payload.selectionMethod)

        shared.setRecommendedValidators(emptyList())
        val changed = current() as ReadyToSubmit.Stash
        assertTrue(changed.payload.blockProducers.isEmpty())
        assertEquals(SelectionMethod.RECOMMENDED, changed.payload.selectionMethod)
    }

    @Test
    fun validatorsLeaveInitialUntouched() {
        val initial = SetupStakingProcess.Initial(StakingType.RELAYCHAIN)
        shared.setupStakingProcess.value = initial

        shared.setCustomValidators(listOf(validator))
        shared.setRecommendedCollators(listOf(collator))
        shared.retractValidators()

        assertSame(initial, current())
    }

    @Test(expected = IllegalArgumentException::class)
    fun validatorsCannotBeSetOnCollatorStep() {
        shared.setupStakingProcess.value = collatorStep
        shared.setCustomValidators(listOf(validator))
    }

    @Test
    fun collatorsMoveCollatorStepToReadyAndChangeLaterSelection() {
        shared.setupStakingProcess.value = collatorStep

        shared.setRecommendedCollators(listOf(collator))
        val ready = current() as ReadyToSubmit.Parachain
        assertEquals(listOf(collator), ready.payload.blockProducers)
        assertEquals(SelectionMethod.RECOMMENDED, ready.payload.selectionMethod)

        shared.setCustomCollators(emptyList())
        val changed = current() as ReadyToSubmit.Parachain
        assertTrue(changed.payload.blockProducers.isEmpty())
        assertEquals(SelectionMethod.CUSTOM, changed.payload.selectionMethod)
    }

    @Test(expected = IllegalArgumentException::class)
    fun collatorsCannotBeSetOnValidatorStep() {
        shared.setupStakingProcess.value = fullStep
        shared.setCustomCollators(listOf(collator))
    }

    @Test
    fun retractReturnsToPreviousSteps() {
        shared.setupStakingProcess.value = ReadyToSubmit.Stash(
            ReadyToSubmit.Payload.Full(amount, RewardDestination.Restake, "me", listOf(validator), SelectionMethod.CUSTOM)
        )

        shared.retractValidators()
        val step = current() as SelectBlockProducersStep.Validators
        assertTrue(step.payload is SelectBlockProducersStep.Payload.Full)

        shared.retractValidators()
        assertEquals(amount, (current() as SetupStakingProcess.SetupStep.Stash).amount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun retractIsRejectedOnSetupStep() {
        shared.setupStakingProcess.value = SetupStakingProcess.SetupStep.Stash(amount)
        shared.retractValidators()
    }
}
