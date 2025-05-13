package jp.co.soramitsu.walletconnect.impl.presentation.connections

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import com.walletconnect.android.internal.common.exception.MalformedWalletConnectUri
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.wallet.impl.domain.QR_PREFIX_WALLET_CONNECT
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.presentation.dappUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val resourceManager: ResourceManager,
    private val walletConnectInteractor: WalletConnectInteractor
) : BaseViewModel() {

    private val _openScannerEvent = MutableLiveData<Event<Unit>>()
    val openScannerEvent: LiveData<Event<Unit>> = _openScannerEvent

    private val enteredChainQueryFlow = MutableStateFlow("")

    val state = combine(WCDelegate.activeSessionFlow, enteredChainQueryFlow) { activeSessions, searchQuery ->
        val sessions = activeSessions.filter {
            it.metaData?.name?.contains(searchQuery, true) == true
        }

        val sessionItems = sessions.map {
            SessionItemState(
                topic = it.topic,
                title = it.metaData?.name.orEmpty(),
                url = it.metaData?.dappUrl,
                imageUrl = it.metaData?.icons?.firstOrNull()
            )
        }

        ConnectionsScreenViewState(
            items = sessionItems,
            searchQuery = searchQuery,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionsScreenViewState.default)

    fun onSessionClicked(sessionItemState: SessionItemState) {
        walletRouter.openConnectionDetails(sessionItemState.topic)
    }

    fun onSearchInput(input: String) {
        enteredChainQueryFlow.value = input
    }

    fun onClose() {
        walletRouter.back()
    }

    fun onCreateNewConnection() {
        _openScannerEvent.value = Event(Unit)
    }

    fun qrCodeScanned(content: String) {
        viewModelScope.launch {
            if (content.startsWith(QR_PREFIX_WALLET_CONNECT)) {
                sendWalletConnectPair(pairingUri = content)
            } else {
                showError("Неверный код. Пожалуйста, попробуйте ещё раз")
            }
        }
    }

    private fun sendWalletConnectPair(pairingUri: String) {
        walletConnectInteractor.pair(
            pairingUri = pairingUri,
            onSuccess = {
                WCDelegate.refreshConnections()
            },
            onError = { error ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (error.throwable is MalformedWalletConnectUri) {
                        showError(
                            title = resourceManager.getString(R.string.connection_invalid_url_error_title),
                            message = resourceManager.getString(R.string.connection_invalid_url_error_message),
                            positiveButtonText = resourceManager.getString(R.string.common_close)
                        )
                    } else {
                        showError(error.throwable.message ?: "WalletConnect pairing error")
                    }
                }
            }
        )
    }
}
