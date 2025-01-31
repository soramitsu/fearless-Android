package jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.http.Url
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_tonconnect_impl.R
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class TonSignRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTotalBalance: TotalBalanceUseCase,
    private val tonConnectRouter: TonConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository,
    private val addressIconGenerator: AddressIconGenerator,
    private val tonConnectInteractor: TonConnectInteractor
) : TonSignRequestScreenInterface, TonSignPreviewScreenInterface, BaseViewModel() {

    private val signRequest: TonConnectSignRequest =
        savedStateHandle[TonSignRequestFragment.TON_SIGN_REQUEST_KEY]
            ?: error("No sign request provided")
    private val dApp: DappModel =
        savedStateHandle[TonSignRequestFragment.DAPP_KEY] ?: error("No dApp info provided")
    private val method: String =
        savedStateHandle.get<String>(TonSignRequestFragment.METHOD_KEY).orEmpty()

    val state: MutableStateFlow<TonSignRequestFlowState?> = MutableStateFlow(null)

    init {
        buildInitialState()
    }

    private fun buildInitialState() = viewModelScope.launch {
        state.update { prevState ->
            prevState
                ?: TonSignRequestViewState(
                    connectionUrl = dApp.url,
                    message = InfoItemViewState(
                        title = resourceManager.getString(R.string.common_message),
                        subtitle = method,
                        singleLine = true
                    ),
                    wallet = WalletItemViewState(
                        1,
                        isSelected = false,
                        title = "My wallet",
                        walletIcon = Unit
                    )
                )
        }

        val selectedMetaAccount =
            withContext(Dispatchers.Default) { accountRepository.getSelectedMetaAccount() }
        if (selectedMetaAccount.tonPublicKey == null) {
            launch(Dispatchers.Main.immediate) {
                showError(
                    title = resourceManager.getString(R.string.common_error_general_title),
                    message = resourceManager.getString(R.string.connection_account_not_supported_warning),
                    positiveButtonText = resourceManager.getString(R.string.common_close),
                    positiveClick = {
                        tonConnectRouter.backWithResult(
                            TonSignRequestFragment.TON_SIGN_REQUEST_KEY to Result.failure<String>(
                                IllegalStateException(resourceManager.getString(R.string.connection_account_not_supported_warning))
                            )
                        )
                    }
                )
            }
            return@launch
        }

        val requestedWalletIcon = addressIconGenerator.createWalletIcon(
            WalletEcosystem.Ton,
            AddressIconGenerator.SIZE_BIG
        )

        val balanceModel = getTotalBalance(selectedMetaAccount.id)

        val walletItemState = WalletItemViewState(
            id = selectedMetaAccount.id,
            title = selectedMetaAccount.name,
            isSelected = false,
            walletIcon = requestedWalletIcon,
            balance = balanceModel.balance.formatFiat(balanceModel.fiatSymbol),
            changeBalanceViewState = ChangeBalanceViewState(
                percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                fiatChange = balanceModel.balanceChange.abs().formatFiat(balanceModel.fiatSymbol)
            )
        )

        state.update { prevState ->
            if (prevState != null && prevState is TonSignRequestViewState) {
                prevState.copy(wallet = walletItemState)
            } else {
                prevState
            }
        }
    }

    override fun onClose() {
        tonConnectRouter.backWithResult(
            TonSignRequestFragment.TON_SIGN_REQUEST_KEY to Result.failure<String>(
                IllegalStateException("User declined request")
            )
        )
    }

    override fun onPreviewClick() {
        viewModelScope.launch {
            val chain = tonConnectInteractor.getChain()
            val metaAccount = accountRepository.getSelectedMetaAccount()

            val requestedWalletIcon = addressIconGenerator.createWalletIcon(
                WalletEcosystem.Ton,
                AddressIconGenerator.SIZE_BIG
            )

            val walletItemState = WalletNameItemViewState(
                id = metaAccount.id,
                title = metaAccount.name,
                isSelected = false,
                walletIcon = requestedWalletIcon
            )

            val icon = GradientIconState.Remote(chain.icon, "0098ED")

            val tableItems = listOf(
                TitleValueViewState(
                    resourceManager.getString(R.string.common_dapp),
                    dApp.name
                ),
                TitleValueViewState(
                    resourceManager.getString(R.string.common_host),
                    kotlin.runCatching { Url(dApp.url.orEmpty()).host }.getOrNull()
                ),
                TitleValueViewState(
                    resourceManager.getString(R.string.common_network),
                    chain.name
                ),
                TitleValueViewState(
                    resourceManager.getString(R.string.common_transaction_raw_data),
                    value = "",
                    clickState = TitleValueViewState.ClickState.Value(
                        R.drawable.ic_right_arrow_24_align_right,
                        TRANSACTION_RAW_DATA_CLICK_ID
                    )
                )
            )

            val previewState = TonSignPreviewViewState(
                chainIcon = icon,
                method = method,
                tableItems = tableItems,
                wallet = walletItemState,
                loading = false
            )

            state.update { prevState ->
                if (prevState != null && prevState is TonSignRequestViewState) {
                    previewState
                } else {
                    prevState
                }
            }
        }
    }

    override fun onSignClick() {
        viewModelScope.launch {
            if ((state.value as? TonSignPreviewViewState)?.loading == true) return@launch
            val chain = tonConnectInteractor.getChain()

            state.update { prevState ->
                if (prevState != null && prevState is TonSignPreviewViewState) {
                    prevState.copy(loading = true)
                } else {
                    prevState
                }
            }

            runCatching { tonConnectInteractor.signMessage(chain, method, signRequest) }
                .onSuccess { tonConnectRouter.backWithResult(TonSignRequestFragment.TON_SIGN_REQUEST_KEY to Result.success(it)) }
                .onFailure { showError(it) }

            state.update { prevState ->
                if (prevState != null && prevState is TonSignPreviewViewState) {
                    prevState.copy(loading = true)
                } else {
                    prevState
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
