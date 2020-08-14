package jp.co.soramitsu.feature_account_impl.presentation.profile

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class ProfileViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

}