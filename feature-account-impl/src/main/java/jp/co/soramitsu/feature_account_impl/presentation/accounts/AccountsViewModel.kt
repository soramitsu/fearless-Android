package jp.co.soramitsu.feature_account_impl.presentation.accounts

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.accounts.model.AccountModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.AccountListingMixin

class AccountsViewModel(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    private val accountListingMixin: AccountListingMixin
) : BaseViewModel(), AccountListingMixin by accountListingMixin {
    fun infoClicked(accountModel: AccountModel) {
        // TODO
    }

    fun editClicked() {
        // TODO
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