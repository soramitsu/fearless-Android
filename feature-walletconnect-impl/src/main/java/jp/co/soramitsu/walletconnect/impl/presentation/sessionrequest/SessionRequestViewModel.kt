package jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest

import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ipfs.multibase.CharEncoding
import java.nio.charset.Charset
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.presentation.address
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import jp.co.soramitsu.walletconnect.impl.presentation.dappUrl
import jp.co.soramitsu.walletconnect.impl.presentation.message
import jp.co.soramitsu.walletconnect.impl.presentation.state.WalletConnectMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@HiltViewModel
class SessionRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTotalBalance: TotalBalanceUseCase,
    private val walletConnectInteractor: WalletConnectInteractor,
    private val walletConnectRouter: WalletConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository,
    private val addressIconGenerator: AddressIconGenerator
) : SessionRequestScreenInterface, BaseViewModel() {
    private val topic: String = savedStateHandle[SessionRequestFragment.SESSION_REQUEST_TOPIC_KEY] ?: error("No session info provided")
    private val sessions: List<Wallet.Model.SessionRequest> = Web3Wallet.getPendingListOfSessionRequests(topic).also {
        if (it.isEmpty()) error("No session requests found")
    }

    private val recentSession = sessions.sortedByDescending { it.request.id }[0]

    private val requestWalletItemFlow: SharedFlow<WalletItemViewState?> = flowOf {
        accountRepository.allMetaAccounts()
    }.map { allMetaAccounts ->
        val requestChain = walletConnectInteractor.getChains().firstOrNull { chain ->
            chain.caip2id == recentSession.chainId
        }
        val requestAddress = recentSession.request.address

        val requestedWallet = requestChain?.let {
            allMetaAccounts.firstOrNull { wallet ->
                wallet.address(requestChain) == requestAddress
            }
        }

        if (requestedWallet == null) {
            launch(Dispatchers.Main) {
                walletConnectRouter.back()
                showError("Wallet with requested address not found")
            }
            return@map null
        }

        val requestedWalletIcon = addressIconGenerator.createAddressIcon(
            requestedWallet.substrateAccountId,
            AddressIconGenerator.SIZE_BIG
        )

        val balanceModel = getTotalBalance(requestedWallet.id)

        WalletItemViewState(
            id = requestedWallet.id,
            title = requestedWallet.name,
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
        println("!!! SessionRequestViewModel sessions = $sessions")

        requestWallet?.let {
            SessionRequestViewState(
                connectionUrl = recentSession.peerMetaData?.dappUrl,
                message = InfoItemViewState(
                    title = resourceManager.getString(R.string.common_message),
                    subtitle = recentSession.request.message,
                    singleLine = true
                ),

                wallet = it
            )
        } ?: SessionRequestViewState.default
    }
        .stateIn(this, SharingStarted.Eagerly, SessionRequestViewState.default)

    init {
        WCDelegate.walletEvents.onEach {
            println("!!! SessionRequestViewModel WCDelegate.walletEvents: $it")
        }.stateIn(this, SharingStarted.Eagerly, null)

        println("!!! SessionRequestViewModel some WC: session.topic = ${sessions[0].topic}")
        println("!!! SessionRequestViewModel some WC:         topic = $topic")
    }

    private var isClosing = false
    override fun onClose() {
        println("!!! SessionRequestViewModel onClose, isClosing = $isClosing")
        if (isClosing) return

        isClosing = true

        Web3Wallet.respondSessionRequest(
            params = Wallet.Params.SessionRequestResponse(
                sessionTopic = topic,
                jsonRpcResponse = Wallet.Model.JsonRpcResponse.JsonRpcError(
                    id = recentSession.request.id,
                    code = 4001,
                    message = "User rejected request"
                )

            ),
            onSuccess = {
                println("!!! Web3Wallet.respondSessionRequest onSuccess")
                isClosing = false

            },
            onError = {
                isClosing = false
                println("!!! Web3Wallet.respondSessionRequest onError: ${it.throwable.message}")
                it.throwable.printStackTrace()
            }
        )

        launch(Dispatchers.Main) {
            walletConnectRouter.back()
        }
    }

    override fun onPreviewClick() {
        walletConnectRouter.openRequestPreview(topic)
        println("!!! SessionRequestViewModel onPreviewClick")
    }
}