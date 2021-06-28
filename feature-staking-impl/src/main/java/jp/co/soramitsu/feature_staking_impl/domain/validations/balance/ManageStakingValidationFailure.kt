package jp.co.soramitsu.feature_staking_impl.domain.validations.balance

sealed class ManageStakingValidationFailure {

    class UnbondingRequestLimitReached(val limit: Int) : ManageStakingValidationFailure()

    class ControllerRequired(val controllerAddress: String) : ManageStakingValidationFailure()

    class StashRequired(val stashAddress: String) : ManageStakingValidationFailure()
}
