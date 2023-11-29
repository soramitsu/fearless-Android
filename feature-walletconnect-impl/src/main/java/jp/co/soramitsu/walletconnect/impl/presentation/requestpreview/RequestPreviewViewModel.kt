package jp.co.soramitsu.walletconnect.impl.presentation.requestpreview

import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.presentation.address
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import jp.co.soramitsu.walletconnect.impl.presentation.dappUrl
import jp.co.soramitsu.walletconnect.impl.presentation.message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RequestPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTotalBalance: TotalBalanceUseCase,
    private val walletConnectInteractor: WalletConnectInteractor,
    private val walletConnectRouter: WalletConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository,
    private val addressIconGenerator: AddressIconGenerator,
    private val extrinsicService: ExtrinsicService
) : RequestPreviewScreenInterface, BaseViewModel() {

    private val topic: String = savedStateHandle[RequestPreviewFragment.PAYLOAD_TOPIC_KEY] ?: error("No topic provided for request preview screen")

    private val sessions: List<Wallet.Model.SessionRequest> = Web3Wallet.getPendingListOfSessionRequests(topic).also {
        if (it.isEmpty()) error("No session requests found")
    }

    private val recentSession = sessions.sortedByDescending { it.request.id }[0]

    val requestChainFlow = flowOf {
        walletConnectInteractor.getChains().firstOrNull { chain ->
            chain.caip2id == recentSession.chainId
        }
    }

    val allMetaAccountsFlow = flowOf {
        accountRepository.allMetaAccounts()
    }

    private val requestWalletItemFlow: SharedFlow<WalletItemViewState?> = combine(
        allMetaAccountsFlow,
        requestChainFlow
    ) { allMetaAccounts, requestChain ->
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
            return@combine null
        }

        val requestedWalletIcon = addressIconGenerator.createAddressIcon(
            requestedWallet.substrateAccountId,
            AddressIconGenerator.SIZE_BIG
        )

        WalletItemViewState(
            id = requestedWallet.id,
            title = requestedWallet.name,
            isSelected = false,
            walletIcon = requestedWalletIcon
        )
    }
        .inBackground()
        .share()

    val state = combine(requestWalletItemFlow, requestChainFlow) { requestWallet, requestChain ->
        println("!!! SessionRequestViewModel sessions = $sessions")
        val requestChainIcon = requestChain?.icon
        val icon = if (requestChainIcon == null) {
            GradientIconState.Local(R.drawable.ic_fearless_logo)
        } else {
            GradientIconState.Remote(requestChainIcon, "EE0077")
        }

        val tableItems = listOf(
            TitleValueViewState(
                "dApp",
                recentSession.peerMetaData?.name
            ),
            TitleValueViewState(
                "Host",
                recentSession.peerMetaData?.dappUrl
            ),
            TitleValueViewState(
                "Network",
                requestChain?.name
            ),
            TitleValueViewState(
                "Transaction raw data",
                value = "",
                clickState =  TitleValueViewState.ClickState.Value(R.drawable.ic_right_arrow_24_align_right, 1)
            )
        )

        requestWallet?.let {
            RequestPreviewViewState(
                chainIcon = icon,
                method = recentSession.request.method,
                tableItems = tableItems,
                wallet = it,
            )
        } ?: RequestPreviewViewState.default
    }
        .stateIn(this, SharingStarted.Eagerly, RequestPreviewViewState.default)

    init {
        WCDelegate.walletEvents.onEach {
            println("!!! RequestPreviewViewModel WCDelegate.walletEvents: $it")
        }.stateIn(this, SharingStarted.Eagerly, null)

        println("!!! RequestPreviewViewModel some WC: session.topic = ${sessions[0].topic}")
        println("!!! RequestPreviewViewModel some WC:         topic = $topic")
    }

    override fun onClose() {
        println("!!! RequestPreviewViewModel onClose")

        launch(Dispatchers.Main) {
            walletConnectRouter.back()
        }
    }

    override fun onSignClick() {
        println("!!! RequestPreviewViewModel onSignClick")

        val message = recentSession.request.message
        println("!!! RequestPreviewViewModel message to sign = $message")

        launch(Dispatchers.IO) {
            requestChainFlow.firstOrNull()?.let { chain ->
                val address = recentSession.request.address ?: return@launch
                val accountId = chain.accountIdOf(address)
                val metaAccount = accountRepository.findMetaAccount(accountId) ?: return@launch
                val encryption = mapCryptoTypeToEncryption(metaAccount.substrateCryptoType)

                val secrets = accountRepository.getMetaAccountSecrets(metaAccount.id) ?: error("There are no secrets for metaId: ${metaAccount.id}")
                val keypairSchema = secrets[MetaAccountSecrets.SubstrateKeypair]
                val publicKey = keypairSchema[KeyPairSchema.PublicKey]
                val privateKey = keypairSchema[KeyPairSchema.PrivateKey]
                val nonce = keypairSchema[KeyPairSchema.Nonce]

                val keypair = Keypair(publicKey, privateKey, nonce)

                val result = extrinsicService.createSignature(
                    encryption = encryption,
                    keypair = keypair,
                    message = recentSession.request.message.toByteArray().toHexString(true)
                )

                println("!!! RequestPreviewViewModel sign result = $result")

                Web3Wallet.respondSessionRequest(
                    params = Wallet.Params.SessionRequestResponse(
                        sessionTopic = topic,
                        jsonRpcResponse = Wallet.Model.JsonRpcResponse.JsonRpcResult(
                            id = recentSession.request.id,
                            result = result
                        )
                    ),
                    onSuccess = {
                        println("!!! Web3Wallet.respondSessionRequest onSuccess")

                    },
                    onError = {
                        println("!!! Web3Wallet.respondSessionRequest onError: ${it.throwable.message}")
                        it.throwable.printStackTrace()
                    }
                )
            }
        }
    }

    override fun onTableItemClick(id: Int) {
        if (id == 1) {
            println("!!! RequestPreviewViewModel onTransactionRawDataClick")
        }
    }
}