package jp.co.soramitsu.feature_staking_impl.domain.validations.balance

sealed class ManageStakingValidationFailure {

    object UnbondingRequestLimitReached : ManageStakingValidationFailure()

    class ControllerRequired(val controllerAddress: String) : ManageStakingValidationFailure()

    object ElectionPeriodOpen : ManageStakingValidationFailure()
}
