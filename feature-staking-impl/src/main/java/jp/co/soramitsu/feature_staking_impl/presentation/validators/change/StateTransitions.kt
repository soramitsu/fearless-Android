package jp.co.soramitsu.feature_staking_impl.presentation.validators.change

import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState

fun SetupStakingSharedState.retractValidators() = mutate {
    if (it is SetupStakingProcess.Confirm) {
        it.previous()
    } else {
        it
    }
}

fun SetupStakingSharedState.setValidators(
    validators: List<Validator>
) = mutate {
    if (it is SetupStakingProcess.Validators) {
        it.next(validators)
    } else {
        (it as SetupStakingProcess.Confirm).changeValidators(validators)
    }
}
