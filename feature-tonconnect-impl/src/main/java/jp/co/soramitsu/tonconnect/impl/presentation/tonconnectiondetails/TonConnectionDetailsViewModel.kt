package jp.co.soramitsu.tonconnect.impl.presentation.tonconnectiondetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_tonconnect_impl.R
import co.jp.soramitsu.tonconnect.domain.TonConnectInteractor
import co.jp.soramitsu.tonconnect.domain.TonConnectRouter
import co.jp.soramitsu.tonconnect.model.AppEntity
import co.jp.soramitsu.tonconnect.model.BridgeError
import co.jp.soramitsu.tonconnect.model.JsonBuilder
import co.jp.soramitsu.tonconnect.model.TONProof
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.InfoItemSetViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TonConnectionDetailsViewModel @Inject constructor(
    accountListingMixin: AccountListingMixin,
    private val tonConnectInteractor: TonConnectInteractor,
    private val tonConnectRouter: TonConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository,
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
            tonConnectRouter.backWithResult(TonConnectionDetailsFragment.TON_CONNECT_RESULT_KEY to JsonBuilder.connectEventError(BridgeError.USER_DECLINED_TRANSACTION).toString())
        }
    }

    override fun onApproveClick() {
        val selectedWalletId = selectedWalletId.value ?: return
        if (isApproving.value) return
        isApproving.value = true

        launch {
            val wallet = accountRepository.getMetaAccount(selectedWalletId)
            val tonPublicKey = wallet.tonPublicKey
            if (tonPublicKey == null) {
                showError("There is no ton account for this wallet")
                tonConnectRouter.backWithResult(TonConnectionDetailsFragment.TON_CONNECT_RESULT_KEY to JsonBuilder.connectEventError(BridgeError.UNKNOWN).toString())
                return@launch
            }

            @Suppress("SwallowedException")
            val proof: TONProof.Result? = proofPayload?.let {
                try {
                    tonConnectInteractor.requestProof(selectedWalletId, app, proofPayload)
                } catch (e: Exception) {
                    tonConnectRouter.backWithResult(TonConnectionDetailsFragment.TON_CONNECT_RESULT_KEY to JsonBuilder.connectEventError(BridgeError.BAD_REQUEST).toString())
                    return@launch
                }
            }

            val json = JsonBuilder.connectEventSuccess(tonPublicKey, proof, null)

            tonConnectRouter.backWithResult(TonConnectionDetailsFragment.TON_CONNECT_RESULT_KEY to json.toString())
        }
    }

    override fun onRejectClicked() {
        if (isRejecting.value) return
        isRejecting.value = true

        onClose()
    }

    override fun onWalletSelected(item: WalletNameItemViewState) {
        selectedWalletId.value = item.id
    }
}
