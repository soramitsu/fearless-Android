package jp.co.soramitsu.feature_account_impl.presentation.account.edit

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
import jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel
import jp.co.soramitsu.feature_account_impl.data.mappers.mapAccountModelToAccount
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

data class UnsyncedSwapPayload(val newState: List<Any>, val from: Int, val to: Int)

class EditAccountsViewModel(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    private val accountListingMixin: AccountListingMixin
) : BaseViewModel(), AccountListingMixin by accountListingMixin {

    private val _deleteConfirmationLiveData = MutableLiveData<Event<AccountModel>>()
    val deleteConfirmationLiveData: LiveData<Event<AccountModel>> = _deleteConfirmationLiveData

    private val _unsyncedSwapLiveData = MutableLiveData<UnsyncedSwapPayload?>()
    val unsyncedSwapLiveData: LiveData<UnsyncedSwapPayload?> = _unsyncedSwapLiveData

    init {
        disposables += accountListingDisposable
    }

    fun doneClicked() {
        accountRouter.back()
    }

    fun backClicked() {
        accountRouter.backToMainScreen()
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

    fun onItemDrag(from: Int, to: Int) {
        val currentState = _unsyncedSwapLiveData.value?.newState
            ?: accountListingLiveData.value!!.groupedAccounts

        val fromElement = currentState[from]
        val toElement = currentState[to]

        if (isSwapable(fromElement, toElement)) {
            val newUnsyncedState = currentState.toMutableList()

            newUnsyncedState.add(to, newUnsyncedState.removeAt(from))

            _unsyncedSwapLiveData.value = UnsyncedSwapPayload(newUnsyncedState, from, to)
        }
    }

    fun onItemDrop() {
        val unsyncedState = _unsyncedSwapLiveData.value?.newState ?: return

        val accountsToUpdate = unsyncedState.filterIsInstance<AccountModel>()
            .mapIndexed { index: Int, accountModel: AccountModel ->
                mapAccountModelToAccount(accountModel, index)
            }

        disposables += accountInteractor.updateAccountPositionsInNetwork(accountsToUpdate)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _unsyncedSwapLiveData.value = null
            }, {
                showError(it.message!!)
            })
    }

    fun addAccountClicked() {
        accountRouter.openAddAccount()
    }
}

@UseExperimental(ExperimentalContracts::class)
private fun isSwapable(fromElement: Any, toElement: Any): Boolean {
    contract {
        returns(true) implies (fromElement is AccountModel && toElement is AccountModel)
    }

    return fromElement is AccountModel && toElement is AccountModel && fromElement.network.type == toElement.network.type
}