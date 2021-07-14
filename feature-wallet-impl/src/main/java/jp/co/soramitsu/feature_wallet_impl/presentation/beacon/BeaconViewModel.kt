package jp.co.soramitsu.feature_wallet_impl.presentation.beacon

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import it.airgap.beaconsdk.data.beacon.P2pPeer
import it.airgap.beaconsdk.message.BeaconRequest
import it.airgap.beaconsdk.message.PermissionBeaconRequest
import it.airgap.beaconsdk.message.SignPayloadBeaconRequest
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.common.utils.viewModelSharedFlow
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DAppMetadataModel(
    val url: String?,
    val address: String,
    val icon: String?,
    val name: String
)

class BeaconViewModel(
    private val beaconApi: BeaconApi,
    private val router: WalletRouter,
    private val interactor: WalletInteractor,
    private val iconGenerator: AddressIconGenerator,
    qrContent: String
) : BaseViewModel() {

    init {
        initializeFromQr(qrContent)
    }

    val dAppMetadata = viewModelSharedFlow<DAppMetadataModel>()

    private val currentAccount = interactor.selectedAccountFlow()
        .inBackground()
        .share()

    val currentAccountAddressModel = currentAccount
        .map { iconGenerator.createAddressModel(it.address, AddressIconGenerator.SIZE_SMALL, it.name) }
        .inBackground()
        .share()

    private val _showPermissionRequestSheet = MutableLiveData<Event<PermissionBeaconRequest>>()
    val showPermissionRequestSheet: LiveData<Event<PermissionBeaconRequest>> = _showPermissionRequestSheet

    fun exit() {
        launch {
            beaconApi.disconnect()

            router.back()
        }
    }

    fun permissionGranted(request: PermissionBeaconRequest) {
        launch {
            beaconApi.allowPermissions(request)
        }
    }

    fun permissionDenied() {
        exit()
    }

    private fun initializeFromQr(qrContent: String) = launch {
        beaconApi.connectFromQR(qrContent)
            .onFailure {
                showMessage("Invalid QR code")
                router.back()
            }.onSuccess { (peer, requestsFlow) ->
                dAppMetadata.emit(mapP2pPeerToDAppMetadataModel(peer))

                listenForRequests(requestsFlow)
            }
    }

    private fun listenForRequests(requestsFlow: Flow<BeaconRequest?>) {
        requestsFlow
            .distinctUntilChanged()
            .onEach {
                Log.d("RX", it.toString())
                when (it) {
                    is PermissionBeaconRequest -> _showPermissionRequestSheet.value = Event(it)
                    is SignPayloadBeaconRequest -> {
                        val result = beaconApi.decodePayload(it)

                        result.onSuccess { call ->
                            showMessage("Received request for ${call.module.name}.${call.function.name}")
                        }
                    }
                }
            }.launchIn(viewModelScope)
    }

    private suspend fun mapP2pPeerToDAppMetadataModel(p2pPeer: P2pPeer) = with(p2pPeer) {
        val networkType = currentAccount.first().network.type

        DAppMetadataModel(
            url = appUrl,
            address = p2pPeer.publicKey.fromHex().toAddress(networkType),
            icon = icon,
            name = name
        )
    }
}
