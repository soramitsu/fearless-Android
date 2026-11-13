package jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.UniversalPasswordNoticeState
import jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet.OptionsWalletFragment.Companion.KEY_ALLOW_WALLET_DETAILS
import jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet.OptionsWalletFragment.Companion.KEY_WALLET_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OptionsWalletViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val accountInteractor: AccountInteractor,
    private val router: WalletRouter,
    private val universalPasswordNotice: UniversalWalletPasswordNotice = UniversalWalletPasswordNotice.NONE
) : BaseViewModel(), OptionsWalletCallback {
    private val walletId: Long = savedStateHandle[KEY_WALLET_ID] ?: error("No walletId provided")
    private val allowWalletDetails: Boolean = savedStateHandle[KEY_ALLOW_WALLET_DETAILS] ?: true

    private val _deleteWalletConfirmation = MutableLiveData<Event<Long>>()
    val deleteWalletConfirmation: LiveData<Event<Long>> = _deleteWalletConfirmation

    private val _showUniversalPasswordNotice = MutableLiveData<Event<UniversalPasswordNoticeState>>()
    val showUniversalPasswordNotice: LiveData<Event<UniversalPasswordNoticeState>> = _showUniversalPasswordNotice

    private val _universalPasswordSwitchResult = MutableLiveData<Event<UniversalPasswordNoticeState>>()
    val universalPasswordSwitchResult: LiveData<Event<UniversalPasswordNoticeState>> = _universalPasswordSwitchResult

    // Rows of addresses the recovery words alone open; reloaded after each switch.
    private val oldAddresses = MutableStateFlow<List<UniversalWalletPasswordNotice.Row>>(emptyList())

    private val selectedWallet = accountInteractor.selectedLightMetaAccountFlow()
        .inBackground()
        .share()

    private val chosenWallet = flowOf { accountInteractor.getLightMetaAccount(walletId) }
        .inBackground()
        .share()

    private val nameFlow = chosenWallet.map { it.name }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<OptionsWalletScreenViewState> = combine(
        selectedWallet,
        chosenWallet,
        oldAddresses
    ) { selectedWallet, chosenWallet, oldAddresses ->
        OptionsWalletScreenViewState(
            isSelected = selectedWallet.id == walletId,
            showScoreButton = chosenWallet.ethereumAddress != null,
            showDetailsButton = walletDetailsAvailable(chosenWallet, allowWalletDetails),
            showOldAddressesButton = oldAddresses.isNotEmpty()
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        OptionsWalletScreenViewState(isSelected = true, showScoreButton = false, showDetailsButton = false)
    )

    override fun onChangeWalletNameClick() {
        router.back()
        router.openRenameWallet(
            metaAccountId = walletId,
            name = nameFlow.value
        )
    }

    override fun onWalletDetailsClick() {
        router.openAccountDetails(walletId)
    }

    override fun onDeleteWalletClick() {
        _deleteWalletConfirmation.value = Event(walletId)
    }

    override fun onCloseClick() {
        router.back()
    }

    fun deleteWalletConfirmed() {
        launch {
            accountInteractor.deleteAccount(walletId)
            router.back()
        }
    }

    override fun onBackupWalletClick() {
        router.openBackupWalletScreen(walletId)
    }

    override fun onShowWalletScoreClick() {
        router.openScoreDetailsScreen(walletId)
    }

    init {
        launch { oldAddresses.value = loadOldAddresses() }
    }

    override fun onOldAddressesClick() {
        val rows = oldAddresses.value
        if (rows.isNotEmpty()) _showUniversalPasswordNotice.value = Event(UniversalPasswordNoticeState(walletId, rows))
    }

    fun onUniversalPasswordSwitch() {
        launch {
            val rows = rowsOrEmpty { switch(walletId) }
            _universalPasswordSwitchResult.value = Event(UniversalPasswordNoticeState(walletId, rows))
            oldAddresses.value = loadOldAddresses()
        }
    }

    fun onUniversalPasswordNoticeClosed() = universalPasswordNotice.dismiss(walletId)

    private suspend fun loadOldAddresses() = rowsOrEmpty { rows(walletId) }

    // The notice is optional: a wallet it cannot read simply offers none.
    private suspend fun rowsOrEmpty(
        read: suspend UniversalWalletPasswordNotice.() -> List<UniversalWalletPasswordNotice.Row>
    ): List<UniversalWalletPasswordNotice.Row> = withContext(Dispatchers.Default) {
        try {
            universalPasswordNotice.read()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            emptyList()
        }
    }
}

internal fun walletDetailsAvailable(wallet: LightMetaAccount, allowWalletDetails: Boolean): Boolean =
    allowWalletDetails && (
        wallet.tonPublicKey == null || wallet.substrateAccountId != null || wallet.ethereumAddress != null
    )
