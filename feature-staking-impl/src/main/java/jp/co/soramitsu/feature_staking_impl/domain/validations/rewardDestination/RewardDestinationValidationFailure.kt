package jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination

sealed class RewardDestinationValidationFailure {
    object CannotPayFees : RewardDestinationValidationFailure()

    class MissingController(val controllerAddress: String) : RewardDestinationValidationFailure()
}
