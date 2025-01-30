package jp.co.soramitsu.tonconnect.impl.presentation.connections

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.tonconnect.domain.TonConnectInteractor
import co.jp.soramitsu.tonconnect.domain.TonConnectRouter
import co.jp.soramitsu.tonconnect.model.ConnectRequest
import co.jp.soramitsu.tonconnect.model.DappConfig
import co.jp.soramitsu.tonconnect.model.DappModel
import co.jp.soramitsu.tonconnect.model.TonConnectException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.QR_PREFIX_TON_CONNECT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TonConnectionsViewModel @Inject constructor(
    private val tonConnectRouter: TonConnectRouter,
    private val resourceManager: ResourceManager,
    private val tonConnectInteractor: TonConnectInteractor
) : BaseViewModel() {

    private val _openScannerEvent = MutableLiveData<Event<Unit>>()
    val openScannerEvent: LiveData<Event<Unit>> = _openScannerEvent

    private val enteredChainQueryFlow = MutableStateFlow("")

    private val connectedDapps: Flow<DappConfig> = tonConnectInteractor.getConnectedDapps()

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
        viewModelScope.launch {
            if (content.startsWith(QR_PREFIX_TON_CONNECT)) {
                readTonQrContent(qrContent = content)
            } else {
                showError(resourceManager.getString(R.string.invoice_scan_error_no_info))
            }
        }
    }

    private suspend fun readTonQrContent(qrContent: String) {
        val uri = kotlin.runCatching { Uri.parse(qrContent) }.getOrNull() ?: return
        val clientId = uri.getQueryParameter("id")
        if (!isValidClientId(clientId)) {
            showError(TonConnectException.WrongClientId(clientId))
        }
        val request = ConnectRequest.parse(uri.getQueryParameter("r"))

        if (request.items.isEmpty()) {
            showError(resourceManager.getString(R.string.common_undefined_error_message))
        }

        val app = tonConnectInteractor.readManifest(request.manifestUrl)
        val signedRequest = tonConnectRouter.openTonConnectionAndWaitForResult(app, request.proofPayload)
        kotlin.runCatching {
            tonConnectInteractor.respondDappConnectRequest(clientId!!, request, signedRequest, app)
        }.onFailure {
            showError(it)
        }
    }

    private fun isValidClientId(clientId: String?): Boolean {
        return !clientId.isNullOrBlank() && clientId.length == 64
    }
}
