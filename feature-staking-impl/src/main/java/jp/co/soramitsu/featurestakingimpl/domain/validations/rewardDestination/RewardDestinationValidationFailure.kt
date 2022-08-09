package jp.co.soramitsu.featurestakingimpl.domain.validations.rewardDestination

sealed class RewardDestinationValidationFailure {
    object CannotPayFees : RewardDestinationValidationFailure()

    class MissingController(val controllerAddress: String) : RewardDestinationValidationFailure()
}
