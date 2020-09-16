package jp.co.soramitsu.feature_account_impl.presentation.networks

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class NetworksViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter
): BaseViewModel() {


}