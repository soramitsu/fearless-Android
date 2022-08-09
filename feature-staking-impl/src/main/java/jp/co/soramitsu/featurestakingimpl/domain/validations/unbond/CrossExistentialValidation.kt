package jp.co.soramitsu.featurestakingimpl.domain.validations.unbond

import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrWarning
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.featurestakingimpl.scenarios.StakingScenarioInteractor

class CrossExistentialValidation(
    private val stakingScenarioInteractor: StakingScenarioInteractor
) : UnbondValidation {

    override suspend fun validate(value: UnbondValidationPayload): ValidationStatus<UnbondValidationFailure> {
        val bonded = stakingScenarioInteractor.getUnstakeAvailableAmount(value.asset, value.collatorAddress?.fromHex())

        val isValid: Boolean = stakingScenarioInteractor.checkCrossExistentialValidation(value)
        return validOrWarning(isValid) {
            UnbondValidationFailure.BondedWillCrossExistential(willBeUnbonded = bonded)
        }
    }
}
