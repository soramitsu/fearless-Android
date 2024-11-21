package jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest

import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.feature_tonconnect_impl.R
import co.jp.soramitsu.tonconnect.domain.TonConnectRouter
import co.jp.soramitsu.tonconnect.model.DappModel
import co.jp.soramitsu.tonconnect.model.SignRequestEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TonSignRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTotalBalance: TotalBalanceUseCase,
    private val tonConnectRouter: TonConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository,
    private val addressIconGenerator: AddressIconGenerator
) : TonSignRequestScreenInterface, BaseViewModel() {
    private val signRequest: SignRequestEntity = savedStateHandle[TonSignRequestFragment.TON_SIGN_REQUEST_KEY] ?: error("No sign request provided")
    private val dApp: DappModel = savedStateHandle[TonSignRequestFragment.DAPP_KEY] ?: error("No dApp info provided")
    private val method: String = savedStateHandle.get<String>(TonSignRequestFragment.METHOD_KEY).orEmpty()

    private val requestWalletItemFlow: SharedFlow<WalletItemViewState?> =
        accountRepository.selectedMetaAccountFlow().map { wallet ->
            if (wallet == null) {
                launch(Dispatchers.Main.immediate) {
                    showError(
                        title = resourceManager.getString(R.string.common_error_general_title),
                        message = resourceManager.getString(R.string.connection_account_not_supported_warning),
                        positiveButtonText = resourceManager.getString(R.string.common_close),
                        positiveClick = { tonConnectRouter.back() }
                    )
                }
                return@map null
            }

            val requestedWalletIcon = addressIconGenerator.createAddressIcon(
                wallet.substrateAccountId,
                AddressIconGenerator.SIZE_BIG
            )

            val balanceModel = getTotalBalance(wallet.id)

            WalletItemViewState(
                id = wallet.id,
                title = wallet.name,
                isSelected = false,
                walletIcon = requestedWalletIcon,
                balance = balanceModel.balance.formatFiat(balanceModel.fiatSymbol),
                changeBalanceViewState = ChangeBalanceViewState(
                    percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                    fiatChange = balanceModel.balanceChange.abs().formatFiat(balanceModel.fiatSymbol)
                )
            )
        }
            .inBackground()
            .share()

    val state = requestWalletItemFlow.map { requestWallet ->
        requestWallet?.let {
            TonSignRequestViewState(
                connectionUrl = dApp.url,
                message = InfoItemViewState(
                    title = resourceManager.getString(R.string.common_message),
                    subtitle = method,
                    singleLine = true
                ),
                wallet = it
            )
        } ?: TonSignRequestViewState.default
    }
        .stateIn(this, SharingStarted.Eagerly, TonSignRequestViewState.default)

    private var isClosing = false
    override fun onClose() {
//        val requestId = recentSession?.request?.id
//        val isRequestNotExist = walletConnectInteractor.getPendingListOfSessionRequests(topic).isEmpty()
//        if (requestId == null || isRequestNotExist) {
//            tonConnectRouter.back()
//            isClosing = false
//        } else {
//            walletConnectInteractor.rejectSessionRequest(
//                sessionTopic = topic,
//                requestId = requestId,
//                onSuccess = {
//                    isClosing = false
//
//                    viewModelScope.launch(Dispatchers.Main.immediate) {
//                        walletConnectRouter.back()
//                    }
//                },
//                onError = {
//                    isClosing = false
//
//                    viewModelScope.launch(Dispatchers.Main.immediate) {
//                        showError(text = resourceManager.getString(R.string.common_try_again))
//                    }
//                }
//            )
//        }
    }

    override fun onPreviewClick() {
        println("!!! TonSignRequest onPreviewClick")
        tonConnectRouter.openTonSignPreview(dApp, method, signRequest)
    }
}
