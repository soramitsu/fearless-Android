package jp.co.soramitsu.feature_staking_impl.presentation

import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel

interface StakingRouter {

    fun openAccounts()

    fun openSetupStaking()

    fun back()

    fun openRecommendedValidators()

    fun openValidatorDetails(validatorDetails: ValidatorDetailsParcelModel)
}