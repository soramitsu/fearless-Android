package jp.co.soramitsu.staking.impl.domain.validations.rewardDestination

sealed class RewardDestinationValidationFailure {
    object CannotPayFees : RewardDestinationValidationFailure()

    class MissingController(val controllerAddress: String) : RewardDestinationValidationFailure()
}
