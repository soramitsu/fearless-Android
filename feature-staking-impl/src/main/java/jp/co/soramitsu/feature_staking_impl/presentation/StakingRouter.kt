package jp.co.soramitsu.feature_staking_impl.presentation

interface StakingRouter {

    fun openSetupStaking()

    fun openRecommendedValidators()

    fun openConfirmStaking()

    fun openConfirmNominations()

    fun finishSetupStakingFlow()

    fun back()
}