package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter

class ValidatorDetailsViewModel(
    private val interactor: StakingInteractor,
    private val router: StakingRouter
) : BaseViewModel() {

}