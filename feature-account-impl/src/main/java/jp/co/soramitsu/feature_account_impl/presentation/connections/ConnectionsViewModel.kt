package jp.co.soramitsu.feature_account_impl.presentation.connections

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class ConnectionsViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter
): BaseViewModel() {


}