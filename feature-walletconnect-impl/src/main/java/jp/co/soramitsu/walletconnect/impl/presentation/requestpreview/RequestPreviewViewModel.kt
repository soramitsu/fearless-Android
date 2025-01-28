package jp.co.soramitsu.walletconnect.impl.presentation.requestpreview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import com.walletconnect.web3.wallet.client.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.walletconnect.impl.presentation.address
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import jp.co.soramitsu.walletconnect.impl.presentation.dappUrl
import jp.co.soramitsu.walletconnect.impl.presentation.message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RequestPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val walletConnectInteractor: WalletConnectInteractor,
    private val walletConnectRouter: WalletConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository,
    private val addressIconGenerator: AddressIconGenerator
) : RequestPreviewScreenInterface, BaseViewModel() {

    private val topic: String = savedStateHandle[RequestPreviewFragment.PAYLOAD_TOPIC_KEY] ?: error("No topic provided for request preview screen")

    private val sessions: List<Wallet.Model.SessionRequest> = walletConnectInteractor.getPendingListOfSessionRequests(topic).also {
        if (it.isEmpty()) {
            walletConnectRouter.back()
        }
    }

    private val recentSession = sessions.sortedByDescending { it.request.id }[0]

    private val isLoading = MutableStateFlow(false)
    private val requestChainFlow = MutableSharedFlow<Chain?>()
        .onStart {
            val value: Chain? = walletConnectInteractor.getChains().firstOrNull { chain ->
                chain.caip2id == recentSession.chainId
            }
            emit(value)
        }
        .stateIn(this, SharingStarted.Eagerly, null)

    private val requestWalletItemFlow: SharedFlow<WalletNameItemViewState?> = requestChainFlow.filterNotNull().map { requestChain ->
        val requestAddress = recentSession.request.address

        val requestedWallet = accountRepository.allMetaAccounts().firstOrNull { wallet ->
            wallet.address(requestChain).equals(requestAddress, true)
        }

        if (requestedWallet == null) {
            launch(Dispatchers.Main) {
                showError(
                    title = resourceManager.getString(R.string.common_error_general_title),
                    message = resourceManager.getString(R.string.connection_account_not_supported_warning),
                    positiveButtonText = resourceManager.getString(R.string.common_close),
                    positiveClick = { walletConnectRouter.back() }
                )
            }
            return@map null
        }

        val requestedWalletIcon = addressIconGenerator.createAddressIcon(
            requestedWallet.substrateAccountId ?: byteArrayOf(),
            AddressIconGenerator.SIZE_BIG
        )

        WalletNameItemViewState(
            id = requestedWallet.id,
            title = requestedWallet.name,
            isSelected = false,
            walletIcon = requestedWalletIcon
        )
    }
        .inBackground()
        .share()

    val state = combine(requestWalletItemFlow, requestChainFlow.filterNotNull(), isLoading) { requestWallet, requestChain, isLoading ->
        val icon = GradientIconState.Remote(requestChain.icon, "EE0077")

        val tableItems = listOf(
            TitleValueViewState(
                resourceManager.getString(R.string.common_dapp),
                recentSession.peerMetaData?.name
            ),
            TitleValueViewState(
                resourceManager.getString(R.string.common_host),
                recentSession.peerMetaData?.dappUrl
            ),
            TitleValueViewState(
                resourceManager.getString(R.string.common_network),
                requestChain.name
            ),
            TitleValueViewState(
                resourceManager.getString(R.string.common_transaction_raw_data),
                value = "",
                clickState = TitleValueViewState.ClickState.Value(R.drawable.ic_right_arrow_24_align_right, TRANSACTION_RAW_DATA_CLICK_ID)
            )
        )

        requestWallet?.let {
            RequestPreviewViewState(
                chainIcon = icon,
                method = recentSession.request.method,
                tableItems = tableItems,
                wallet = it,
                loading = isLoading
            )
        } ?: RequestPreviewViewState.default
    }
        .stateIn(this, SharingStarted.Eagerly, RequestPreviewViewState.default)

    override fun onClose() {
        launch(Dispatchers.Main) {
            walletConnectRouter.back()
        }
    }

    override fun onSignClick() {
        if (isLoading.value) return
        val chain = requestChainFlow.value ?: return

        isLoading.value = true
        viewModelScope.launch {
            walletConnectInteractor.handleSignAction(
                chain = chain,
                topic = topic,
                recentSession = recentSession,
                onSignError = ::onSignError,
                onRequestSuccess = ::onRespondSessionRequestSuccess,
                onRequestError = ::onRespondRequestSessionError
            )
        }
    }

    private fun onRespondSessionRequestSuccess(operationHash: String?, chainId: ChainId?) {
        isLoading.value = false
        viewModelScope.launch(Dispatchers.Main) {
            walletConnectRouter.openOperationSuccessAndPopUpToNearestRelatedScreen(
                operationHash = operationHash,
                chainId = chainId,
                customMessage = null
            )
        }
    }

    private fun onRespondRequestSessionError(error: Wallet.Model.Error) {
        isLoading.value = false
        viewModelScope.launch(Dispatchers.Main) {
            showError(
                title = resourceManager.getString(R.string.common_error_general_title),
                message = resourceManager.getString(R.string.common_try_again) + "\n" + error.throwable.message.orEmpty(),
                positiveButtonText = resourceManager.getString(R.string.common_ok)
            )
        }
    }

    private fun onSignError(e: Exception) {
        isLoading.value = false
        viewModelScope.launch(Dispatchers.Main) {
            showError(
                title = resourceManager.getString(R.string.common_error_general_title),
                message = resourceManager.getString(R.string.common_try_again) + "\n" + e.message.orEmpty(),
                positiveButtonText = resourceManager.getString(R.string.common_ok)
            )
        }
    }

    override fun onTableItemClick(id: Int) {
        if (id == TRANSACTION_RAW_DATA_CLICK_ID) {
            walletConnectRouter.openRawData(recentSession.request.message)
        }
    }

    override fun onTableRowClick(id: Int) {
        if (id == TRANSACTION_RAW_DATA_CLICK_ID) {
            walletConnectRouter.openRawData(recentSession.request.message)
        }
    }

    companion object {
        private const val TRANSACTION_RAW_DATA_CLICK_ID = 1
    }
}
