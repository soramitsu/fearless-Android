package jp.co.soramitsu.feature_account_impl.presentation.account.list

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel
import kotlinx.coroutines.launch

class AccountListViewModel(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    accountListingMixin: AccountListingMixin
) : BaseViewModel() {

    val accountListingLiveData = accountListingMixin.accountListingFlow().asLiveData()

    val selectedAccountLiveData = accountListingMixin.selectedAccountFlow().asLiveData()

    fun infoClicked(accountModel: AccountModel) {
        accountRouter.openAccountDetails(accountModel.address)
    }

    fun editClicked() {
        accountRouter.openEditAccounts()
    }

    fun selectAccountClicked(accountModel: AccountModel) {
        viewModelScope.launch {
            accountInteractor.selectAccount(accountModel.address)

            accountRouter.returnToMain()
        }
    }

    fun backClicked() {
        accountRouter.back()
    }

    fun addAccountClicked() {
        accountRouter.openAddAccount()
    }
}
