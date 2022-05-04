package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main

import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateNetwork
import it.airgap.beaconsdk.blockchain.substrate.message.request.PermissionSubstrateRequest
import it.airgap.beaconsdk.blockchain.substrate.message.request.SignPayloadSubstrateRequest
import it.airgap.beaconsdk.core.data.P2pPeer
import it.airgap.beaconsdk.core.message.BeaconRequest
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_account_api.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.TotalBalance
import jp.co.soramitsu.feature_account_impl.presentation.account.model.format
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.domain.beacon.BeaconInteractor
import jp.co.soramitsu.feature_wallet_impl.domain.beacon.SignStatus
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main.BeaconStateMachine.SideEffect
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Parcelize
class DAppMetadataModel(
    val url: String?,
    val address: String,
    val icon: String?,
    val name: String
) : Parcelable

class BeaconViewModel(
    private val beaconInteractor: BeaconInteractor,
    private val router: WalletRouter,
    walletInteractor: WalletInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    totalBalance: GetTotalBalanceUseCase,
    qrContent: String?
) : BaseViewModel() {

    private val stateMachine = BeaconStateMachine()

    val state = stateMachine.currentState

    val currentAccountAddressModel = combine(
        walletInteractor.selectedMetaAccountFlow(),
        walletInteractor.polkadotAddressForSelectedAccountFlow()
    ) { metaAccount: MetaAccount, polkadotAddress: String ->
        iconGenerator.createAddressModel(polkadotAddress, AddressIconGenerator.SIZE_SMALL, metaAccount.name)
    }.inBackground()
        .share()

    val totalBalanceLiveData = totalBalance().map(TotalBalance::format).asLiveData()

    private val _scanBeaconQrEvent = MutableLiveData<Event<Unit>>()
    val scanBeaconQrEvent: LiveData<Event<Unit>> = _scanBeaconQrEvent

    private var beaconRequestedNetworks = listOf<SubstrateNetwork>()

    init {
        qrContent?.let { initializeFromQr(qrContent) } ?: initialize()

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
                    // todo think about multiple networks
                    // todo hardcoded westend
                    beaconInteractor.registerNetwork("e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e")
                    // (it.request.networks.first().genesisHash)
                }

                is SideEffect.AskSignApproval -> {

                    router.openSignBeaconTransaction(it.request.payload, it.dAppMetadata)
                }

                is SideEffect.RespondApprovedPermissions -> {
                    beaconInteractor.allowPermissions(it.request)

                    router.openSuccessFragment(currentAccountAddressModel.first().image)
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
                    exit()
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
        stateMachine.transition(BeaconStateMachine.Event.ExitRequested)
    }

    private fun permissionGranted() {
        stateMachine.transition(BeaconStateMachine.Event.ApprovedPermissions)
    }

    private fun permissionDenied() {
        stateMachine.transition(BeaconStateMachine.Event.DeclinedPermissions)
    }

    private fun initializeFromQr(qrContent: String) = launch {
        beaconInteractor.connectFromQR(qrContent)
            .onFailure {
                Log.e(BeaconViewModel::class.java.name, it.localizedMessage ?: it.message ?: "Failed connect to beacon qr code")
                showMessage("Invalid QR code")
                router.back()
            }.onSuccess { (peer, requestsFlow) ->
                val dAppMetadata = mapP2pPeerToDAppMetadataModel(peer)

                stateMachine.transition(BeaconStateMachine.Event.ReceivedMetadata(dAppMetadata))

                listenForRequests(requestsFlow)
            }
    }

    private fun initialize() {
        stateMachine.transition(BeaconStateMachine.Event.ConnectToExistingPeer)
        viewModelScope.launch {
            if (beaconInteractor.hasPeers().not()) {
                _scanBeaconQrEvent.value = Event(Unit)
                return@launch
            }
            beaconInteractor.initWithoutQr()
                .onFailure {
                    val message = it.localizedMessage ?: it.message ?: "Failed connect to beacon qr code"
                    Log.e(BeaconViewModel::class.java.name, message)
                    showMessage(message)
                    router.back()
                }.onSuccess { (peer, requestsFlow) ->
                    val dAppMetadata = mapP2pPeerToDAppMetadataModel(peer)

                    stateMachine.transition(BeaconStateMachine.Event.ReceivedMetadata(dAppMetadata))

                    listenForRequests(requestsFlow)
                }
        }
    }

    private fun listenForRequests(requestsFlow: Flow<BeaconRequest?>) {
        requestsFlow
            .distinctUntilChanged()
            .onEach {
                when (it) {
                    is PermissionSubstrateRequest -> {
                        beaconRequestedNetworks = it.networks
                        stateMachine.transition(BeaconStateMachine.Event.ReceivedPermissionsRequest(it))
                    }

                    is SignPayloadSubstrateRequest -> {
                        stateMachine.transition(BeaconStateMachine.Event.ReceivedSigningRequest(it))
                    }
                }
            }.launchIn(viewModelScope)
    }

    private suspend fun mapP2pPeerToDAppMetadataModel(p2pPeer: P2pPeer) = with(p2pPeer) {
        DAppMetadataModel(
            url = appUrl ?: relayServer, // todo address stub
            address = publicKey, // p2pPeer.publicKey.fromHex().toAddress(networkType),
            icon = icon,
            name = name
        )
    }

    fun back() {
        viewModelScope.launch {
            if (stateMachine.currentState.first() is BeaconStateMachine.State.AwaitingPermissionsApproval) {
                permissionDenied()
            } else {
                router.back()
            }
        }
    }

    fun connectClicked() {
        viewModelScope.launch {
            when {
                stateMachine.currentState.first() is BeaconStateMachine.State.AwaitingPermissionsApproval -> {
                    permissionGranted()
                }
                beaconInteractor.isConnected() -> {
                    exit()
                }
            }
        }
    }

    fun beaconQrScanned(qrContent: String) {
        initializeFromQr(qrContent)
    }
}
