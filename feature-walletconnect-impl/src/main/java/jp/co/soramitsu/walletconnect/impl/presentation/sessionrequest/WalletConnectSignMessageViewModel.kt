package jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import com.reown.walletkit.client.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.presentation.account.CapturedPortfolioDetail
import jp.co.soramitsu.account.api.presentation.account.admitRequestPortfolio
import jp.co.soramitsu.account.api.presentation.account.capturedPortfolioDetailFlow
import jp.co.soramitsu.account.api.presentation.account.withAllHoldings
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.walletconnect.impl.presentation.address
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import jp.co.soramitsu.walletconnect.impl.presentation.dappUrl
import jp.co.soramitsu.walletconnect.impl.presentation.message
import jp.co.soramitsu.walletconnect.impl.presentation.pendingRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class WalletConnectSignMessageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTotalBalance: TotalBalanceUseCase,
    private val walletConnectInteractor: WalletConnectInteractor,
    private val walletConnectRouter: WalletConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository,
    private val addressIconGenerator: AddressIconGenerator
) : SessionRequestScreenInterface, BaseViewModel() {
    private val topic: String = savedStateHandle[WalletConnectSignMessageFragment.SESSION_REQUEST_TOPIC_KEY] ?: error("No session info provided")
    private val sessions: List<Wallet.Model.SessionRequest> = walletConnectInteractor.getPendingListOfSessionRequests(topic).also {
        if (it.isEmpty()) {
            walletConnectRouter.back()
        }
    }

    private val recentSession = sessions.takeIf { it.isNotEmpty() }?.sortedByDescending { it.request.id }?.get(0)

    private val requestWalletItemFlow: Flow<WalletItemViewState?> = flow<WalletItemViewState?> {
        if (recentSession != null && recentSession.request.message == null) {
            // Malformed params: nothing can be shown or signed, closing answers the dApp with an error.
            launch(Dispatchers.Main.immediate) {
                showError(
                    title = resourceManager.getString(R.string.common_error_general_title),
                    message = resourceManager.getString(R.string.common_undefined_error_message),
                    positiveButtonText = resourceManager.getString(R.string.common_close),
                    positiveClick = ::onClose,
                    onBackClick = ::onClose
                )
            }
            emit(null)
            return@flow
        }

        val allMetaAccounts = withContext(Dispatchers.Default) { accountRepository.allMetaAccounts() }
        val requestChain = withContext(Dispatchers.Default) { walletConnectInteractor.getChains() }.firstOrNull { chain ->
            chain.caip2id == recentSession?.chainId
        }
        val requestAddress = recentSession?.request?.address
        val requestedWallet = requestChain?.let {
            allMetaAccounts.firstOrNull { wallet ->
                matchesWalletConnectRequestAddress(requestChain.isEthereumBased, wallet.address(requestChain), requestAddress)
            }
        }
        if (requestedWallet == null || requestChain == null) {
            launch(Dispatchers.Main.immediate) {
                showError(
                    title = resourceManager.getString(R.string.common_error_general_title),
                    message = resourceManager.getString(R.string.connection_account_not_supported_warning),
                    positiveButtonText = resourceManager.getString(R.string.common_close),
                    positiveClick = { walletConnectRouter.back() }
                )
            }
            emit(
                WalletItemViewState(
                    id = 0,
                    title = "",
                    isSelected = false,
                    walletIcon = R.drawable.ic_wallet
                ).withAllHoldings(TotalBalance.Unavailable, resourceManager)
            )
            return@flow
        }

        val requestMetaId = requestedWallet.id
        val admitted = getTotalBalance.observe(requestMetaId).map { source ->
            admitRequestPortfolio(source, requestMetaId, requestMetaId) { ready ->
                val chain = ready.chainsById[requestChain.id]
                chain != null && chain.caip2id == recentSession?.chainId &&
                    (chain.isEthereumBased || chain.ecosystem == Ecosystem.Substrate) &&
                    matchesWalletConnectRequestAddress(chain.isEthereumBased, ready.meta.address(chain), requestAddress)
            }
        }
        emitAll(
            capturedPortfolioDetailFlow(admitted) { source ->
                val chain = source.chainsById.getValue(requestChain.id)
                val accountId = walletConnectRequestAccountId(chain.isEthereumBased, source.meta.address(chain))
                    ?: return@capturedPortfolioDetailFlow null
                withContext(Dispatchers.Default) {
                    if (chain.isEthereumBased) {
                        addressIconGenerator.createEthereumAddressIcon(accountId, AddressIconGenerator.SIZE_BIG)
                    } else {
                        addressIconGenerator.createAddressIcon(accountId, AddressIconGenerator.SIZE_BIG)
                    }
                }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                showError(failure)
                emit(CapturedPortfolioDetail(TotalBalance.Unavailable, null))
            }.map { captured ->
                val ready = captured.source as? TotalBalance.Ready
                WalletItemViewState(
                    id = requestMetaId,
                    title = ready?.meta?.name.orEmpty(),
                    isSelected = false,
                    walletIcon = captured.detail ?: R.drawable.ic_wallet
                ).withAllHoldings(captured.source, resourceManager)
            }
        )
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        showError(failure)
        emit(
            WalletItemViewState(
                id = 0,
                title = "",
                isSelected = false,
                walletIcon = R.drawable.ic_wallet
            ).withAllHoldings(TotalBalance.Unavailable, resourceManager)
        )
    }

    val state = requestWalletItemFlow.map { requestWallet ->
        requestWallet?.let {
            SessionRequestViewState(
                connectionUrl = recentSession?.peerMetaData?.dappUrl,
                message = InfoItemViewState(
                    title = resourceManager.getString(R.string.common_message),
                    subtitle = recentSession?.request?.message,
                    singleLine = true
                ),
                wallet = it
            )
        } ?: SessionRequestViewState.default
    }
        .stateIn(this, SharingStarted.Eagerly, SessionRequestViewState.default)

    private var isClosing = false
    override fun onClose() {
        val requestId = recentSession?.request?.id
        val isRequestPending = requestId != null &&
            walletConnectInteractor.getPendingListOfSessionRequests(topic).pendingRequest(requestId) != null
        if (requestId == null || !isRequestPending) {
            walletConnectRouter.back()
            isClosing = false
        } else {
            walletConnectInteractor.rejectSessionRequest(
                sessionTopic = topic,
                requestId = requestId,
                onSuccess = {
                    isClosing = false

                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        walletConnectRouter.back()
                    }
                },
                onError = {
                    isClosing = false

                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        showError(text = resourceManager.getString(R.string.common_try_again))
                    }
                }
            )
        }
    }

    override fun onPreviewClick() {
        val displayedRequest = recentSession
        if (displayedRequest == null) {
            walletConnectRouter.back()
            return
        }
        // The preview and the signature are bound to the request shown here, not to the newest one.
        walletConnectRouter.openRequestPreview(topic, displayedRequest.request.id)
    }
}
