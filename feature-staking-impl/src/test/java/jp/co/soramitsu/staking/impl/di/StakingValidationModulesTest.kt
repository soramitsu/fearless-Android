package jp.co.soramitsu.staking.impl.di

import io.mockk.mockk
import java.math.BigDecimal
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.impl.di.validations.BondMoreValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.MakePayoutValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.RebondValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.RedeemValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.RewardDestinationValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.SetControllerValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.StakingBalanceValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.UnbondValidationsModule
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class StakingValidationModulesTest {

    private inline fun <reified T : Any> m(): T = mockk(relaxed = true)

    private fun assertType(expected: String, value: Any) = assertEquals(expected, value::class.simpleName)

    @Test
    fun bondMoreAndPayoutValidationSystemsCombineTheirChecks() {
        val bond = BondMoreValidationsModule()
        assertType("EnoughToPayFeesValidation", bond.provideFeeValidation(m(), m(), m()))
        assertType("NotZeroAmountValidation", bond.provideNotZeroBondValidation())
        assertType("ValidationSystem", bond.provideBondMoreValidationSystem(m(), m()))

        val payout = MakePayoutValidationsModule()
        assertType("EnoughToPayFeesValidation", payout.provideFeeValidation(m(), m(), m()))
        assertType("ProfitablePayoutValidation", payout.provideProfitableValidation())
        assertType("ValidationSystem", payout.provideValidationSystem(m(), m()))
    }

    @Test
    fun rebondRedeemAndRewardDestinationSystemsAreBuilt() {
        val rebond = RebondValidationsModule()
        assertType("EnoughToPayFeesValidation", rebond.provideFeeValidation())
        assertType("NotZeroAmountValidation", rebond.provideNotZeroUnbondValidation())
        assertType("EnoughToRebondValidation", rebond.provideEnoughToRebondValidation(m()))
        assertType("ValidationSystem", rebond.provideRebondValidationSystem(m(), m(), m()))

        val redeem = RedeemValidationsModule()
        assertType("EnoughToPayFeesValidation", redeem.provideFeeValidation())
        assertType("ValidationSystem", redeem.provideRedeemValidationSystem(m()))

        val reward = RewardDestinationValidationsModule()
        assertType("EnoughToPayFeesValidation", reward.provideFeeValidation())
        assertType("AccountRequiredValidation", reward.controllerRequiredValidation(m()))
        assertType("ValidationSystem", reward.provideRedeemValidationSystem(m(), m()))
    }

    @Test
    fun controllerBalanceAndUnbondSystemsAreBuilt() {
        val controller = SetControllerValidationsModule()
        assertType("EnoughToPayFeesValidation", controller.provideFeeValidation())
        assertType("AccountIsNotControllerValidation", controller.provideControllerValidation(m()))
        assertType("NotZeroBalanceValidation", controller.provideZeroBalanceControllerValidation(m(), m()))
        assertType("ValidationSystem", controller.provideSetControllerValidationSystem(m(), m(), m()))

        val balance = StakingBalanceValidationsModule()
        assertType("AccountRequiredValidation", balance.provideControllerValidation(m()))
        assertType("AccountRequiredValidation", balance.provideStashValidation(m()))
        assertType("UnbondingRequestsLimitValidation", balance.provideUnbondingLimitValidation(m()))

        val unbond = UnbondValidationsModule()
        assertType("EnoughToPayFeesValidation", unbond.provideFeeValidation())
        assertType("NotZeroAmountValidation", unbond.provideNotZeroUnbondValidation())
        assertType("UnbondingRequestsLimitValidation", unbond.provideUnbondLimitValidation(m()))
        assertType("EnoughToUnbondValidation", unbond.provideEnoughToUnbondValidation(m()))
        assertType("CrossExistentialValidation", unbond.provideCrossExistentialValidation(m()))
        assertType("ValidationSystem", unbond.provideUnbondValidationSystem(m(), m(), m(), m(), m()))
        assertNotNull(StakingValidationModule())
    }

    @Test
    fun scenarioInteractorFollowsTheSetupProcess() {
        val relay = m<StakingRelayChainScenarioInteractor>()
        val para = m<StakingParachainScenarioInteractor>()
        val shared = SetupStakingSharedState()
        val module = StakingScenarioModule()
        fun pick(process: SetupStakingProcess): Any {
            shared.setupStakingProcess.value = process
            return module.provideScenarioInteractor(shared, para, relay)
        }

        assertSame(relay, pick(SetupStakingProcess.Initial(StakingType.RELAYCHAIN)))
        assertSame(para, pick(SetupStakingProcess.Initial(StakingType.PARACHAIN)))
        assertSame(relay, pick(SetupStakingProcess.Initial(StakingType.POOL)))
        assertSame(relay, pick(SetupStakingProcess.SetupStep.Stash(BigDecimal.ONE)))
        assertSame(para, pick(SetupStakingProcess.SetupStep.Parachain(BigDecimal.ONE)))
        val validators = SetupStakingProcess.SelectBlockProducersStep.Validators(
            SetupStakingProcess.SelectBlockProducersStep.Payload.Relaychain(BigDecimal.ONE, RewardDestination.Restake, "addr")
        )
        assertSame(relay, pick(validators))
        val collators = SetupStakingProcess.SelectBlockProducersStep.Collators(
            SetupStakingProcess.SelectBlockProducersStep.Payload.Parachain(BigDecimal.ONE, "addr")
        )
        assertSame(para, pick(collators))
        assertSame(relay, pick(validators.next(emptyList(), SetupStakingProcess.ReadyToSubmit.SelectionMethod.CUSTOM)))
        assertSame(para, pick(collators.next(emptyList(), SetupStakingProcess.ReadyToSubmit.SelectionMethod.RECOMMENDED)))
    }
}
