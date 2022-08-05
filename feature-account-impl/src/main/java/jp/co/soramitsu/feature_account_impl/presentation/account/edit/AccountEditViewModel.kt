package jp.co.soramitsu.feature_account_impl.presentation.account.edit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.DragAndDropDelegate
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.account.model.LightMetaAccountUi
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    accountListingMixin: AccountListingMixin
) : BaseViewModel() {

    private val _deleteConfirmationLiveData = MutableLiveData<Event<Long>>()
    val deleteConfirmationLiveData: LiveData<Event<Long>> = _deleteConfirmationLiveData

    val accountListingLiveData = accountListingMixin.accountsFlow()
        .share()

    val dragAndDropDelegate = DragAndDropDelegate(accountListingLiveData.asLiveData())

    fun doneClicked() {
        launch {
            dragAndDropDelegate.unsyncedSwapLiveData.value?.let {
                val idsInNewOrder = it.map(LightMetaAccountUi::id)
                accountInteractor.updateAccountPositionsInNetwork(idsInNewOrder)
            }

            accountRouter.back()
        }
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

    fun addAccountClicked() {
        accountRouter.openAddAccount()
    }
}
