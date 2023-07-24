package jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet.OptionsWalletFragment.Companion.KEY_WALLET_ID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OptionsWalletViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val accountInteractor: AccountInteractor,
    private val router: WalletRouter
) : BaseViewModel(), OptionsWalletCallback {

    private val _deleteWalletConfirmation = MutableLiveData<Event<Long>>()
    val deleteWalletConfirmation: LiveData<Event<Long>> = _deleteWalletConfirmation

    private val selectedWallet = accountInteractor.selectedMetaAccountFlow()
        .inBackground()
        .share()

    val state: StateFlow<OptionsWalletScreenViewState> = selectedWallet.map {
        OptionsWalletScreenViewState(it.id == savedStateHandle[KEY_WALLET_ID]!!)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        OptionsWalletScreenViewState(true)
    )

    override fun onChangeWalletNameClick() {
        router.back()
        router.openRenameWallet(savedStateHandle[KEY_WALLET_ID]!!)
    }

    override fun onWalletDetailsClick() {
        router.openAccountDetails(savedStateHandle[KEY_WALLET_ID]!!)
    }

    override fun onDeleteWalletClick() {
        _deleteWalletConfirmation.value = Event(savedStateHandle[KEY_WALLET_ID]!!)
    }

    override fun onCloseClick() {
        router.back()
    }

    fun deleteWalletConfirmed() {
        launch {
            accountInteractor.deleteAccount(savedStateHandle[KEY_WALLET_ID]!!)
            router.back()
        }
    }

    override fun onBackupWalletClick() {
        router.openBackupWalletScreen(savedStateHandle[KEY_WALLET_ID]!!)
    }
}
