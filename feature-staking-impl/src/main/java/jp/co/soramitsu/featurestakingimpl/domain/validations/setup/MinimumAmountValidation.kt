package jp.co.soramitsu.featurestakingimpl.domain.validations.setup

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.featurestakingimpl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.featurewalletapi.domain.model.amountFromPlanks

class MinimumAmountValidation(
    private val stakingScenarioInteractor: StakingScenarioInteractor
) : Validation<SetupStakingPayload, SetupStakingValidationFailure> {

    override suspend fun validate(value: SetupStakingPayload): ValidationStatus<SetupStakingValidationFailure> {
        val assetConfiguration = value.asset.token.configuration

        val minimumBondInPlanks = stakingScenarioInteractor.getMinimumStake(assetConfiguration.chainId)
        val minimumBond = assetConfiguration.amountFromPlanks(minimumBondInPlanks)

        // either first time bond or already existing bonded balance
        val amountToCheckAgainstMinimum = value.bondAmount ?: value.asset.bonded

        return if (amountToCheckAgainstMinimum >= minimumBond) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, SetupStakingValidationFailure.TooSmallAmount(minimumBond))
        }
    }
}
