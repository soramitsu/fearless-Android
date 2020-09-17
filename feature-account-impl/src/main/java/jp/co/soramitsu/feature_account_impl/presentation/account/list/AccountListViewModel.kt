package jp.co.soramitsu.feature_account_impl.presentation.account.list

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.AccountListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.account.AccountModel

class AccountListViewModel(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    private val accountListingMixin: AccountListingMixin
) : BaseViewModel(), AccountListingMixin by accountListingMixin {
    fun infoClicked(accountModel: AccountModel) {
        accountRouter.openAccountDetails(accountModel.address)
    }

    fun editClicked() {
        accountRouter.openEditAccounts()
    }

    fun selectAccountClicked(accountModel: AccountModel) {
        disposables += accountInteractor.selectAccount(accountModel.address)
            .subscribe()
    }

    fun backClicked() {
        accountRouter.back()
    }

    fun addAccountClicked() {
        accountRouter.openAddAccount()
    }

    init {
        disposables += accountListingDisposable
    }
}