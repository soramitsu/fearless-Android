package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_staking_impl.domain.staking.controller.ControllerInteractor

class SetControllerViewModel(
    private val interactor: ControllerInteractor
) : BaseViewModel()
