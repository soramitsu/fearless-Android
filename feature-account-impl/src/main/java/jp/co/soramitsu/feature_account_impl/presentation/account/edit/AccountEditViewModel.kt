package jp.co.soramitsu.feature_account_impl.presentation.account.edit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.account.model.LightMetaAccountUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class UnsyncedSwapPayload(val newState: List<LightMetaAccountUi>)

class EditAccountsViewModel(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    accountListingMixin: AccountListingMixin
) : BaseViewModel() {

    private val _deleteConfirmationLiveData = MutableLiveData<Event<Long>>()
    val deleteConfirmationLiveData: LiveData<Event<Long>> = _deleteConfirmationLiveData

    private val _unsyncedSwapLiveData = MutableLiveData<UnsyncedSwapPayload>()
    val unsyncedSwapLiveData: LiveData<UnsyncedSwapPayload> = _unsyncedSwapLiveData

    val accountListingLiveData = accountListingMixin.accountsFlow()
        .share()

    fun doneClicked() {
        accountRouter.back()
    }

    fun backClicked() {
        accountRouter.backToMainScreen()
    }

    fun deleteClicked(account: LightMetaAccountUi) = launch {
        if (!account.isSelected) {
            _deleteConfirmationLiveData.value = Event(account.id)
        }
    }

    fun deleteConfirmed(metaId: Long) {
        launch {
            accountInteractor.deleteAccount(metaId)
        }
    }

    fun onItemDrag(from: Int, to: Int) {
        launch {
            val currentState = _unsyncedSwapLiveData.value?.newState
                ?: accountListingLiveData.first()

            val newUnsyncedState = currentState.toMutableList()

            newUnsyncedState.add(to, newUnsyncedState.removeAt(from))

            _unsyncedSwapLiveData.value = UnsyncedSwapPayload(newUnsyncedState)
        }
    }

    fun onItemDrop() {
        val unsyncedState = _unsyncedSwapLiveData.value?.newState ?: return

        launch {
            val idsInNewOrder = unsyncedState.map(LightMetaAccountUi::id)

            accountInteractor.updateAccountPositionsInNetwork(idsInNewOrder)
        }
    }

    fun addAccountClicked() {
        accountRouter.openAddAccount()
    }
}
