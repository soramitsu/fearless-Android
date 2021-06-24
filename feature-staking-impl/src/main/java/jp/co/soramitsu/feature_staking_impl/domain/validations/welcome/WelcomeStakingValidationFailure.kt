package jp.co.soramitsu.feature_staking_impl.domain.validations.welcome

sealed class WelcomeStakingValidationFailure {
    object Election : WelcomeStakingValidationFailure()

    object MaxNominatorsReached : WelcomeStakingValidationFailure()
}
