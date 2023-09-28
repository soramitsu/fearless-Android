package jp.co.soramitsu.walletconnect.impl.presentation.connections

import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.wallet.impl.domain.QR_PREFIX_WALLET_CONNECT
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.presentation.dappUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
) : BaseViewModel() {

    val refresh = MutableStateFlow(Event(Unit))

    val activeSessionsFlow = refresh.flatMapLatest {
        flowOf {
            Web3Wallet.getListOfActiveSessions()
        }
    }.onEach {
        val joinToString = it.joinToString { it.topic + ":" + it.metaData }
        println("!!! ConnectionsViewModel activeSessionsFlow: ${it.size}: $joinToString")
    }

    private val _openScannerEvent = MutableLiveData<Event<Unit>>()
    val openScannerEvent: LiveData<Event<Unit>> = _openScannerEvent

    private val enteredChainQueryFlow = MutableStateFlow("")

    val state = combine(WCDelegate.activeSessionFlow, enteredChainQueryFlow) { activeSessions, searchQuery ->
        val joinToString = activeSessions.joinToString { it.topic + ":" + it.metaData }
        println("!!! ConnectionsViewModel state combine activeSessions: ${activeSessions.size}: $joinToString")

        val sessions = activeSessions.filter {
            it.metaData?.name?.contains(searchQuery, false) == true
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

    init {
        WCDelegate.walletEvents.onEach {
            println("ConnectionsViewModel: WCDelegate.walletEvents $it")
//            refresh.value = Event(Unit)
        }.stateIn(this, SharingStarted.Eagerly, null)
    }

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
        val pairingParams = Wallet.Params.Pair(pairingUri)
        Web3Wallet.pair(
            params = pairingParams,
            onSuccess = {
                println("!!! ConnectionsViewModel Web3Wallet.pair success, params: $it")
//                refresh.value = Event(Unit)
                WCDelegate.refreshConnections()
            },
            onError = { error ->
                println("!!! ConnectionsViewModel Web3Wallet.pair onError: ${error.throwable.message}")
                error.throwable.printStackTrace()
            })
    }
}
