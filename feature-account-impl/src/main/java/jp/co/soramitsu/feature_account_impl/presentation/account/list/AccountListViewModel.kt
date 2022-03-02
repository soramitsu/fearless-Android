package jp.co.soramitsu.feature_account_impl.presentation.account.list

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.account.model.LightMetaAccountUi
import kotlinx.coroutines.launch

enum class AccountChosenNavDirection {
    BACK, MAIN
}

class AccountListViewModel(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    private val accountChosenNavDirection: AccountChosenNavDirection,
    accountListingMixin: AccountListingMixin,
) : BaseViewModel() {

    val openWalletOptionsEvent = MutableLiveData<Event<Long>>()

    val accountsFlow = accountListingMixin.accountsFlow()
        .inBackground()
        .share()

    fun optionsClicked(accountModel: LightMetaAccountUi) {
        openWalletOptionsEvent.postValue(Event(accountModel.id))
    }

    fun openWalletDetails(metaAccountId: Long) {
        accountRouter.openAccountDetails(metaAccountId)
    }

    fun openExportWallet(metaAccountId: Long) {
        accountRouter.openExportWallet(metaAccountId)
    }

    fun editClicked() {
        accountRouter.openEditAccounts()
    }

    fun selectAccountClicked(account: LightMetaAccountUi) = launch {
        accountInteractor.selectMetaAccount(account.id)

        dispatchNavigation()
    }

    private fun dispatchNavigation() {
        when (accountChosenNavDirection) {
            AccountChosenNavDirection.BACK -> accountRouter.back()
            AccountChosenNavDirection.MAIN -> accountRouter.returnToWallet()
        }
    }

    fun backClicked() {
        accountRouter.back()
    }

    fun addAccountClicked() {
        accountRouter.openAddAccount()
    }
}
