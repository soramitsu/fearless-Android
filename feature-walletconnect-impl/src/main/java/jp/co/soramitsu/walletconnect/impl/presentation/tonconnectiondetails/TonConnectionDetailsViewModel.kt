package jp.co.soramitsu.walletconnect.impl.presentation.tonconnectiondetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.TonConnectInteractor
import co.jp.soramitsu.walletconnect.domain.TonConnectRouter
import co.jp.soramitsu.walletconnect.model.AppEntity
import com.walletconnect.web3.wallet.client.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

    private val selectedWalletIds = MutableStateFlow<Set<Long>>(setOf())
    private val isApproving = MutableStateFlow(false)
    private val isRejecting = MutableStateFlow(false)

    private val accountsFlow = accountListingMixin.accountsFlow(AddressIconGenerator.SIZE_BIG)

    private val walletItemsFlow: SharedFlow<List<WalletNameItemViewState>> = combine(accountsFlow, selectedWalletIds) { accounts, selectedWalletIds ->
        accounts.map {
            WalletNameItemViewState(
                id = it.id,
                title = it.name,
                isSelected = if (selectedWalletIds.isEmpty()) it.isSelected else it.id in selectedWalletIds,
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
            selectedWalletIds.value = setOf(initialSelectedWalletId)
        }
    }

    override fun onClose() {
        viewModelScope.launch(Dispatchers.Main) {
            tonConnectRouter.back()
        }
    }

    override fun onApproveClick() {
        if (isApproving.value) return
        isApproving.value = true

        launch {
            val selectedWalletIds = selectedWalletIds.value
//            tonConnectInteractor.approveSession(
//                proposal = proposal,
//                selectedWalletIds = selectedWalletIds,
//                selectedOptionalChainIds = selectedOptionalChainIds,
//                onSuccess = onApproveSessionSuccess(),
//                onError = ::onApproveSessionError
//            )
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

    private fun onApproveSessionError(error: Wallet.Model.Error): () -> Unit = {
        isApproving.value = false
        viewModelScope.launch(Dispatchers.Main.immediate) {
            showError(
                title = resourceManager.getString(R.string.common_error_general_title),
                message = error.throwable.message.orEmpty(),
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
        viewModelScope.launch {
            val currentIds = selectedWalletIds.value

            val newSelected: Set<Long> = if (item.id in currentIds) {
                currentIds.filter { it != item.id }.toSet()
            } else {
                currentIds.plus(item.id)
            }

            selectedWalletIds.value = newSelected
        }
    }
}
