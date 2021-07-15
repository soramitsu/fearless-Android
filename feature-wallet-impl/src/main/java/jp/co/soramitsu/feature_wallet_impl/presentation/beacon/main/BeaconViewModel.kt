package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main

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
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.domain.beacon.BeaconInteractor
import jp.co.soramitsu.feature_wallet_impl.domain.beacon.SignStatus
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main.BeaconStateMachine.SideEffect
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
    private val beaconInteractor: BeaconInteractor,
    private val router: WalletRouter,
    private val interactor: WalletInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    qrContent: String
) : BaseViewModel() {

    init {
        initializeFromQr(qrContent)
    }

    private val stateMachine = BeaconStateMachine()

    val state = stateMachine.currentState

    private val currentAccount = interactor.selectedAccountFlow()
        .inBackground()
        .share()

    val currentAccountAddressModel = currentAccount
        .map { iconGenerator.createAddressModel(it.address, AddressIconGenerator.SIZE_SMALL, it.name) }
        .inBackground()
        .share()

    private val _showPermissionRequestSheet = MutableLiveData<Event<String>>()
    val showPermissionRequestSheet: LiveData<Event<String>> = _showPermissionRequestSheet

    init {
        listenSideEffects()

        listenForApprovals()
    }

    private fun listenSideEffects() {
        stateMachine.sideEffects.onEach {
            when (it) {
                is SideEffect.RespondApprovedSign -> {
                    beaconInteractor.signPayload(it.request)

                    showMessage(resourceManager.getString(R.string.beacon_signed))
                }

                is SideEffect.AskPermissionsApproval -> {
                    _showPermissionRequestSheet.value = Event(it.dAppName)
                }

                is SideEffect.AskSignApproval -> {
                    router.openSignBeaconTransaction(it.request.payload)
                }

                is SideEffect.RespondApprovedPermissions -> {
                    beaconInteractor.allowPermissions(it.request)

                    showMessage(resourceManager.getString(R.string.beacon_connected, it.request.appMetadata.name))
                }

                SideEffect.Exit -> {
                    beaconInteractor.disconnect()

                    router.back()
                }

                is SideEffect.RespondDeclinedSign -> {
                    beaconInteractor.reportSignDeclined(it.request)

                    showMessage(resourceManager.getString(R.string.beacon_declined))
                }

                is SideEffect.RespondDeclinedPermissions -> {
                    beaconInteractor.reportPermissionsDeclined(it.request)

                    showMessage(resourceManager.getString(R.string.beacon_pairing_cancelled))
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun listenForApprovals() {
        router.beaconSignStatus.onEach {
            when (it) {
                SignStatus.APPROVED -> stateMachine.transition(BeaconStateMachine.Event.ApprovedSigning)
                SignStatus.DECLINED -> stateMachine.transition(BeaconStateMachine.Event.DeclinedSigning)
            }
        }.launchIn(viewModelScope)
    }

    fun exit() {
        stateMachine.transition(BeaconStateMachine.Event.ExistRequested)
    }

    fun permissionGranted() {
        stateMachine.transition(BeaconStateMachine.Event.ApprovedPermissions)
    }

    fun permissionDenied() {
        stateMachine.transition(BeaconStateMachine.Event.DeclinedPermissions)
    }

    private fun initializeFromQr(qrContent: String) = launch {
        beaconInteractor.connectFromQR(qrContent)
            .onFailure {
                showMessage("Invalid QR code")
                router.back()
            }.onSuccess { (peer, requestsFlow) ->
                val dAppMetadata = mapP2pPeerToDAppMetadataModel(peer)

                stateMachine.transition(BeaconStateMachine.Event.ReceivedMetadata(dAppMetadata))

                listenForRequests(requestsFlow)
            }
    }

    private fun listenForRequests(requestsFlow: Flow<BeaconRequest?>) {
        requestsFlow
            .distinctUntilChanged()
            .onEach {
                Log.d("RX", it.toString())
                when (it) {
                    is PermissionBeaconRequest -> {
                        stateMachine.transition(BeaconStateMachine.Event.ReceivedPermissionsRequest(it))
                    }

                    is SignPayloadBeaconRequest -> {
                        stateMachine.transition(BeaconStateMachine.Event.ReceivedSigningRequest(it))
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
