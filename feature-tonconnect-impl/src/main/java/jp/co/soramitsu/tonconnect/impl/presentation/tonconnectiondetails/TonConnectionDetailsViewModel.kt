package jp.co.soramitsu.tonconnect.impl.presentation.tonconnectiondetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_tonconnect_impl.R
import co.jp.soramitsu.tonconnect.domain.TonConnectInteractor
import co.jp.soramitsu.tonconnect.domain.TonConnectRouter
import co.jp.soramitsu.tonconnect.model.AppEntity
import co.jp.soramitsu.tonconnect.model.TONProof
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.InfoItemSetViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.V4R2WalletContract
import jp.co.soramitsu.tonconnect.impl.presentation.dappscreen.base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.ton.crypto.hex

@HiltViewModel
class TonConnectionDetailsViewModel @Inject constructor(
    accountListingMixin: AccountListingMixin,
    private val tonConnectInteractor: TonConnectInteractor,
    private val tonConnectRouter: TonConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository,
    savedStateHandle: SavedStateHandle
) : TonConnectionDetailsScreenInterface, BaseViewModel() {

    private val app: AppEntity = savedStateHandle[TonConnectionDetailsFragment.TON_CONNECTION_APP_KEY] ?: error("No connection info provided")
    private val proofPayload: String? = savedStateHandle[TonConnectionDetailsFragment.TON_PROOF_PAYLOAD_KEY]

    private val selectedWalletId = MutableStateFlow<Long?>(null)
    private val isApproving = MutableStateFlow(false)
    private val isRejecting = MutableStateFlow(false)

    private val accountsFlow = accountListingMixin.accountsFlow(AddressIconGenerator.SIZE_BIG)

    private val walletItemsFlow: SharedFlow<List<WalletNameItemViewState>> = combine(accountsFlow, selectedWalletId) { accounts, selectedWalletId ->
        accounts.map {
            WalletNameItemViewState(
                id = it.id,
                title = it.name,
                isSelected = if (selectedWalletId == null) it.isSelected else it.id == selectedWalletId,
                walletIcon = it.picture.value
            )
        }
    }
        .inBackground()
        .share()

    val state: StateFlow<TonConnectionDetailsViewState> = combine(
        walletItemsFlow,
        isApproving,
        isRejecting
    ) { walletItems, isApproving, isRejecting ->
        val chain = tonConnectInteractor.getChain()

        val requiredNetworksSelectorState = SelectorState(
            title = resourceManager.getString(R.string.connection_required_networks),
            subTitle = chain.name,
            iconUrl = chain.icon,
            actionIcon = null
        )

        val reviewDappInfo = InfoItemSetViewState(
            title = resourceManager.getString(R.string.tc_review_dapp_title),
            infoItems = listOf(
                InfoItemViewState(
                    title = resourceManager.getString(R.string.tc_service_address),
                    subtitle = app.url
                )
            )
        )

        TonConnectionDetailsViewState(
            appInfo = app,
            reviewDappInfo = reviewDappInfo,
            requiredNetworksSelectorState = requiredNetworksSelectorState,
            wallets = walletItems,
            approving = isApproving,
            rejecting = isRejecting
        )
    }.stateIn(this, SharingStarted.Eagerly, TonConnectionDetailsViewState.default)

    init {
        launch {
            val initialSelectedWalletId = accountRepository.getSelectedLightMetaAccount().id
            selectedWalletId.value = initialSelectedWalletId
        }
    }

    override fun onClose() {
        viewModelScope.launch(Dispatchers.Main) {
            tonConnectRouter.back()
        }
    }

    override fun onApproveClick() {
        val selectedWalletId = selectedWalletId.value ?: return
        if (isApproving.value) return
        isApproving.value = true

        launch {
            val wallet = accountRepository.getMetaAccount(selectedWalletId)
            val tonPublicKey = wallet.tonPublicKey
            val senderSmartContract = V4R2WalletContract(tonPublicKey!!)

            val proof: TONProof.Result? = proofPayload?.let {
                tonConnectInteractor.requestProof(selectedWalletId, app, proofPayload)
            }


            val stateInit = senderSmartContract.stateInitCell().base64()

            val tonAddressItemReply = JSONObject()
            tonAddressItemReply.put("name", "ton_addr")
            val isTestnet = true // todo remove from release
            tonAddressItemReply.put("address", tonPublicKey.tonAccountId(isTestnet))
            val network = if (isTestnet) "-3" else "-239"
            tonAddressItemReply.put("network", network)
            tonAddressItemReply.put("publicKey", hex(tonPublicKey))
            tonAddressItemReply.put("walletStateInit", stateInit)

            val payloadItemsJson = JSONArray()
            payloadItemsJson.put(tonAddressItemReply)

            proof?.let {
                val size = proof.domain.value.toByteArray().size
                val domainJson = JSONObject()
                domainJson.put("lengthBytes", size)
                domainJson.put("length_bytes", size)
                domainJson.put("value", proof.domain.value)

                val proofJson = JSONObject()
                proofJson.put("timestamp", proof.timestamp)
                proofJson.put("domain", domainJson)
                proofJson.put("signature", proof.signature)
                proofJson.put("payload", proof.payload)

                val tonProofItemReply = JSONObject()
                tonProofItemReply.put("name", "ton_proof")
                tonProofItemReply.put("proof", proofJson)

                payloadItemsJson.put(tonProofItemReply)
            }

//            proofError?.let {
//                payloadItemsJson.put(tonProofItemReplyError(it))
//            }

            val sendTransactionFeatureJson = JSONObject()
            sendTransactionFeatureJson.put("name", "SendTransaction")
            sendTransactionFeatureJson.put("maxMessages", senderSmartContract.maxMessages)

            val featuresJsonArray = JSONArray()
            featuresJsonArray.put("SendTransaction")
            featuresJsonArray.put(sendTransactionFeatureJson)

            val deviceJson = JSONObject()
            deviceJson.put("platform", "android")
            deviceJson.put("appName", "Tonkeeper")
            deviceJson.put("appVersion", "5.0.12") //BuildConfig.VERSION_NAME)
            deviceJson.put("maxProtocolVersion", 2)
            deviceJson.put("features", featuresJsonArray)


            val payloadJson = JSONObject()
            payloadJson.put("items", payloadItemsJson)
            payloadJson.put("device", deviceJson)

            val json = JSONObject()
            json.put("event", "connect")
            json.put("id", System.currentTimeMillis())
            json.put("payload", payloadJson)

            println("!!! connectEventSuccess json = $json")

            tonConnectRouter.backWithResult(TonConnectionDetailsFragment.TON_CONNECT_RESULT_KEY to json.toString())
        }
    }

    private fun onApproveSessionSuccess(): () -> Unit = {
//        viewModelScope.launch(Dispatchers.Main.immediate) {
//            tonConnectRouter.openOperationSuccessAndPopUpToNearestRelatedScreen(
//                null,
//                null,
//                resourceManager.getString(R.string.connection_approve_success_message, app.name),
//                resourceManager.getString(R.string.all_done)
//            )
//        }
        isApproving.value = false
    }

    private fun onApproveSessionError(error: String): () -> Unit = {
        isApproving.value = false
        viewModelScope.launch(Dispatchers.Main.immediate) {
            showError(
                title = resourceManager.getString(R.string.common_error_general_title),
                message = error,
                positiveButtonText = resourceManager.getString(R.string.common_close),
                positiveClick = ::onClose,
                onBackClick = ::onClose
            )
        }
    }

    override fun onRejectClicked() {
        if (isRejecting.value) return
        isRejecting.value = true

        onClose()
    }

//    private fun onRejectSessionSuccess(): (Wallet.Params.SessionReject) -> Unit = {
//        isRejecting.value = false
//        viewModelScope.launch(Dispatchers.Main.immediate) {
//            tonConnectRouter.openOperationSuccessAndPopUpToNearestRelatedScreen(
//                null,
//                null,
//                resourceManager.getString(R.string.common_rejected),
//                resourceManager.getString(R.string.all_done)
//            )
//        }
//    }

//    private fun callSilentRejectSession() {
//        tonConnectInteractor.silentRejectSession(
//            proposal = proposal,
//            onSuccess = { onClose() },
//            onError = { onClose() }
//        )
//    }

    override fun onWalletSelected(item: WalletNameItemViewState) {
        selectedWalletId.value = item.id
    }
}
