package jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.presentation.account.CapturedPortfolioDetail
import jp.co.soramitsu.account.api.presentation.account.admitRequestPortfolio
import jp.co.soramitsu.account.api.presentation.account.capturedPortfolioDetailFlow
import jp.co.soramitsu.account.api.presentation.account.isCurrentRequestPortfolio
import jp.co.soramitsu.account.api.presentation.account.withAllHoldings
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.SigningSummaryViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.feature_tonconnect_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class TonSignRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTotalBalance: TotalBalanceUseCase,
    private val tonConnectRouter: TonConnectRouter,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val tonConnectInteractor: TonConnectInteractor,
    private val summaries: TonSignRequestSummaries
) : TonSignRequestScreenInterface, TonSignPreviewScreenInterface, BaseViewModel() {

    private val signRequest: TonConnectSignRequest =
        savedStateHandle[TonSignRequestFragment.TON_SIGN_REQUEST_KEY]
            ?: error("No sign request provided")
    private val dApp: DappModel =
        savedStateHandle[TonSignRequestFragment.DAPP_KEY] ?: error("No dApp info provided")
    private val method: String =
        savedStateHandle.get<String>(TonSignRequestFragment.METHOD_KEY).orEmpty()

    private var requestMetaId: Long? = null
    private var requestBindingInvalidated = false
    private var requestPortfolioSource: TotalBalance = TotalBalance.AwaitingSelection
    private var requestWalletItem = WalletItemViewState(
        id = 0,
        title = "",
        walletIcon = R.drawable.ic_wallet,
        isSelected = false
    ).withAllHoldings(TotalBalance.AwaitingSelection, resourceManager)

    val state: MutableStateFlow<TonSignRequestFlowState?> = MutableStateFlow(null)

    // What the request signs, decoded once its wallet is known. Nothing can be signed before.
    private val summary = MutableStateFlow<SigningSummaryViewState?>(null)
    private var summaryJob: Job? = null

    init {
        buildInitialState()
    }

    private fun buildInitialState() = viewModelScope.launch {
        state.value = TonSignRequestViewState(
            connectionUrl = dApp.url,
            message = InfoItemViewState(
                title = resourceManager.getString(R.string.common_transaction),
                subtitle = "",
                singleLine = true
            ),
            wallet = requestWalletItem
        )
        try {
            val connections = if (dApp.metaId == null) {
                tonConnectInteractor.getConnectedDapps(ConnectionSource.WEB).apps
                    .map { it.url to it.metaId }
            } else {
                emptyList()
            }
            val targetId = tonRequestWalletId(dApp.metaId, dApp.url, connections)
            requestMetaId = targetId
            if (targetId == null) {
                invalidateRequestPortfolio()
                showError(
                    title = resourceManager.getString(R.string.common_error_general_title),
                    message = resourceManager.getString(R.string.connection_account_not_supported_warning),
                    positiveButtonText = resourceManager.getString(R.string.common_close),
                    positiveClick = {
                        tonConnectRouter.backWithResult(
                            TonSignRequestFragment.TON_SIGN_RESULT_KEY to Result.failure<String>(
                                IllegalStateException(resourceManager.getString(R.string.connection_account_not_supported_warning))
                            )
                        )
                    }
                )
                return@launch
            }
            val requestChain = tonConnectInteractor.getChain()
            val bindings = if (dApp.metaId != null) {
                flowOf(dApp.metaId)
            } else {
                tonConnectInteractor.getConnectedDappsFlow(ConnectionSource.WEB).map { config ->
                    tonRequestWalletId(null, dApp.url, config.apps.map { it.url to it.metaId })
                }
            }
            val admitted = combine(getTotalBalance.observe(targetId), bindings) { source, bindingId ->
                if (bindingId != targetId) requestBindingInvalidated = true
                admitRequestPortfolio(source, targetId, bindingId.takeUnless { requestBindingInvalidated }) { ready ->
                    val chain = ready.chainsById[requestChain.id]
                    ready.meta.tonPublicKey != null && chain?.ecosystem == Ecosystem.Ton &&
                        chain.isTestNet == requestChain.isTestNet
                }
            }
            capturedPortfolioDetailFlow(admitted) {
                addressIconGenerator.createWalletIcon(WalletEcosystem.Ton, AddressIconGenerator.SIZE_BIG)
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                emit(CapturedPortfolioDetail(TotalBalance.Unavailable, null))
            }.collect { captured ->
                requestPortfolioSource = captured.source
                val ready = captured.source as? TotalBalance.Ready
                ready?.meta?.tonPublicKey?.let { tonPublicKey -> decodeSummaryOnce(requestChain, tonPublicKey) }
                requestWalletItem = WalletItemViewState(
                    id = targetId,
                    title = ready?.meta?.name.orEmpty(),
                    walletIcon = captured.detail ?: R.drawable.ic_wallet,
                    isSelected = false
                ).withAllHoldings(captured.source, resourceManager)
                state.update { previous ->
                    when (previous) {
                        is TonSignRequestViewState -> previous.copy(wallet = requestWalletItem)
                        is TonSignPreviewViewState -> previous.copy(
                            wallet = WalletNameItemViewState(
                                id = requestWalletItem.id,
                                title = requestWalletItem.title,
                                walletIcon = requestWalletItem.walletIcon,
                                isSelected = false
                            )
                        )
                        else -> previous
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            invalidateRequestPortfolio()
        }
    }

    // The decode reads at most four messages, and its only I/O is a suspending jetton lookup.
    private fun decodeSummaryOnce(chain: Chain, tonPublicKey: ByteArray) {
        if (summaryJob != null) return
        summaryJob = viewModelScope.launch {
            try {
                showSummary(summaries.summary(chain, signRequest, tonPublicKey))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Without a summary the request cannot be signed; the user can only close it.
                showError(failure)
            }
        }
    }

    private fun showSummary(requestSummary: SigningSummaryViewState) {
        summary.value = requestSummary
        state.update { prevState ->
            when (prevState) {
                is TonSignRequestViewState -> prevState.copy(message = prevState.message.copy(subtitle = requestSummary.headline))
                is TonSignPreviewViewState -> prevState.copy(
                    tableItems = previewTableItems(prevState.tableItems, requestSummary),
                    summary = requestSummary
                )
                else -> prevState
            }
        }
    }

    // Raw data is the fallback for what could not be decoded, not the way a request is shown.
    private fun previewTableItems(requestItems: List<TitleValueViewState>, requestSummary: SigningSummaryViewState?) =
        requestItems.filterNot { it.clickState?.identifier == TRANSACTION_RAW_DATA_CLICK_ID } + listOfNotNull(
            TitleValueViewState(
                resourceManager.getString(R.string.common_transaction_raw_data),
                value = "",
                clickState = TitleValueViewState.ClickState.Value(
                    R.drawable.ic_right_arrow_24_align_right,
                    TRANSACTION_RAW_DATA_CLICK_ID
                )
            ).takeIf { requestSummary?.showsRawData == true }
        )

    private fun invalidateRequestPortfolio() {
        requestBindingInvalidated = true
        requestPortfolioSource = TotalBalance.Unavailable
        requestWalletItem = requestWalletItem.copy(title = "", walletIcon = R.drawable.ic_wallet)
            .withAllHoldings(TotalBalance.Unavailable, resourceManager)
        state.update { previous ->
            when (previous) {
                is TonSignRequestViewState -> previous.copy(wallet = requestWalletItem)
                is TonSignPreviewViewState -> previous.copy(
                    wallet = WalletNameItemViewState(
                        id = requestWalletItem.id,
                        title = "",
                        walletIcon = R.drawable.ic_wallet,
                        isSelected = false
                    )
                )
                else -> previous
            }
        }
    }

    override fun onClose() {
        tonConnectRouter.backWithResult(
            TonSignRequestFragment.TON_SIGN_RESULT_KEY to Result.failure<String>(
                IllegalStateException("User declined request")
            )
        )
    }

    override fun onPreviewClick() {
        viewModelScope.launch {
            try {
                val captured = requestPortfolioSource as? TotalBalance.Ready ?: return@launch
                val chain = tonConnectInteractor.getChain()
                if (!isCurrentRequestPortfolio(captured, requestPortfolioSource, requestMetaId, captured.meta.id)) return@launch
                val capturedChain = captured.chainsById[chain.id] ?: return@launch
                if (capturedChain.ecosystem != Ecosystem.Ton || capturedChain.isTestNet != chain.isTestNet) return@launch

                val walletItemState = WalletNameItemViewState(
                    id = captured.meta.id,
                    title = captured.meta.name,
                    isSelected = false,
                    walletIcon = requestWalletItem.walletIcon
                )

                val icon = GradientIconState.Remote(chain.icon, "0098ED")

                val tableItems = listOf(
                    TitleValueViewState(
                        resourceManager.getString(R.string.common_dapp),
                        dApp.name
                    ),
                    TitleValueViewState(
                        resourceManager.getString(R.string.common_host),
                        kotlin.runCatching { URL(dApp.url.orEmpty()).host }.getOrNull()
                    ),
                    TitleValueViewState(
                        resourceManager.getString(R.string.common_network),
                        chain.name
                    )
                )

                val previewState = TonSignPreviewViewState(
                    chainIcon = icon,
                    method = method,
                    tableItems = previewTableItems(tableItems, summary.value),
                    wallet = walletItemState,
                    loading = false,
                    summary = summary.value
                )

                state.update { prevState ->
                    if (prevState != null && prevState is TonSignRequestViewState) {
                        previewState
                    } else {
                        prevState
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                showError(failure)
            }
        }
    }

    override fun onSignClick() {
        viewModelScope.launch {
            val preview = state.value as? TonSignPreviewViewState ?: return@launch
            if (preview.loading || summary.value == null) return@launch
            val captured = requestPortfolioSource as? TotalBalance.Ready ?: return@launch
            // Own the in-flight confirmation before the first suspension, including binding lookups.
            state.value = preview.copy(loading = true)
            try {
                val connectedDapps = tonConnectInteractor.getConnectedDapps(ConnectionSource.WEB).apps
                val metaId = tonRequestWalletId(dApp.metaId, dApp.url, connectedDapps.map { it.url to it.metaId })
                if (metaId == null || metaId != requestMetaId) {
                    invalidateRequestPortfolio()
                    return@launch
                }
                if (!isCurrentRequestPortfolio(captured, requestPortfolioSource, requestMetaId, metaId)) return@launch
                val chain = tonConnectInteractor.getChain()
                if (!isCurrentRequestPortfolio(captured, requestPortfolioSource, requestMetaId, metaId)) return@launch
                val capturedChain = captured.chainsById[chain.id] ?: return@launch
                if (capturedChain.ecosystem != Ecosystem.Ton || capturedChain.isTestNet != chain.isTestNet) return@launch
                val signed = tonConnectInteractor.signMessage(chain, method, signRequest, metaId)
                tonConnectRouter.backWithResult(TonSignRequestFragment.TON_SIGN_RESULT_KEY to Result.success(signed))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                showError(failure)
            } finally {
                state.update { previous ->
                    (previous as? TonSignPreviewViewState)?.copy(loading = false) ?: previous
                }
            }
        }
    }

    override fun onTableItemClick(id: Int) {
        if (id == TRANSACTION_RAW_DATA_CLICK_ID) {
            tonConnectRouter.openRawData(Gson().toJson(signRequest))
        }
    }

    override fun onTableRowClick(id: Int) {
        if (id == TRANSACTION_RAW_DATA_CLICK_ID) {
            tonConnectRouter.openRawData(Gson().toJson(signRequest))
        }
    }

    companion object {
        private const val TRANSACTION_RAW_DATA_CLICK_ID = 1
    }
}
