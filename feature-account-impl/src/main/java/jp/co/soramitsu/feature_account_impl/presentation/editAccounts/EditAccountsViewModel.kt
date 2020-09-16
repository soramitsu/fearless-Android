package jp.co.soramitsu.feature_account_impl.presentation.editAccounts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.accountManagment.AccountModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.AccountListingMixin

class EditAccountsViewModel(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    private val accountListingMixin: AccountListingMixin
) : BaseViewModel(), AccountListingMixin by accountListingMixin {

    private val _deleteConfirmationLiveData = MutableLiveData<Event<AccountModel>>()
    val deleteConfirmationLiveData: LiveData<Event<AccountModel>> = _deleteConfirmationLiveData

    fun backClicked() {
        accountRouter.back()
    }

    fun addAccountClicked() {
        accountRouter.openAddAccount()
    }

    fun deleteClicked(account: AccountModel) {
        val selectedAccount = selectedAccountLiveData.value!!

        if (selectedAccount.address != account.address) {
            _deleteConfirmationLiveData.value = Event(account)
        }
    }

    fun deleteConfirmed(account: AccountModel) {
        disposables += accountInteractor.deleteAccount(account.address)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeToError { showError(it.message!!) }
    }

    init {
        disposables += accountListingDisposable
    }
}