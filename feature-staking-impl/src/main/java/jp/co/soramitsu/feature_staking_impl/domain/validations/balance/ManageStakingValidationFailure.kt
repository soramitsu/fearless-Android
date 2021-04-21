package jp.co.soramitsu.feature_staking_impl.domain.validations.balance

sealed class ManageStakingValidationFailure {

    object UnbondingRequestLimitReached : ManageStakingValidationFailure()

    object ControllerRequired : ManageStakingValidationFailure()

    object ElectionPeriodOpen : ManageStakingValidationFailure()
}
