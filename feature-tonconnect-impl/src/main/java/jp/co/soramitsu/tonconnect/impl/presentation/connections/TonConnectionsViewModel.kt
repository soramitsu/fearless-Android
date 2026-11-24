package jp.co.soramitsu.tonconnect.impl.presentation.connections

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.tonconnect.api.domain.connectFromLink
import jp.co.soramitsu.tonconnect.api.model.DappConfig
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.TonConnectLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import jp.co.soramitsu.common.R as CommonR

@HiltViewModel
class TonConnectionsViewModel @Inject constructor(
    private val tonConnectRouter: TonConnectRouter,
    private val resourceManager: ResourceManager,
    private val tonConnectInteractor: TonConnectInteractor
) : BaseViewModel() {

    private val _openScannerEvent = MutableLiveData<Event<Unit>>()
    val openScannerEvent: LiveData<Event<Unit>> = _openScannerEvent

    private val enteredChainQueryFlow = MutableStateFlow("")

    private val connectedDapps: Flow<DappConfig> = tonConnectInteractor.getConnectedDappsFlow(ConnectionSource.QR)

    val state = combine(connectedDapps, enteredChainQueryFlow) { connectedDapps, searchQuery ->
        val dApps = connectedDapps.apps.filter {
            it.name?.contains(searchQuery, true) == true
        }

        TonConnectionsScreenViewState(
            items = dApps,
            searchQuery = searchQuery,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TonConnectionsScreenViewState.default)

    fun onDappClicked(dappItem: DappModel) {
        tonConnectRouter.openTonConnectionInfo(dappItem)
    }

    fun onSearchInput(input: String) {
        enteredChainQueryFlow.value = input
    }

    fun onClose() {
        tonConnectRouter.back()
    }

    fun onCreateNewConnection() {
        _openScannerEvent.value = Event(Unit)
    }

    fun qrCodeScanned(content: String) {
        if (!TonConnectLink.isCandidate(content)) {
            showError(resourceManager.getString(R.string.invoice_scan_error_no_info))
            return
        }
        val link = runCatching { TonConnectLink.parse(content) }.getOrElse {
            showError(resourceManager.getString(CommonR.string.ton_connect_invalid_link))
            return
        }
        viewModelScope.launch {
            try {
                tonConnectInteractor.connectFromLink(link, tonConnectRouter::openTonConnectionAndWaitForResult)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                showError(failure)
            }
        }
    }
}
