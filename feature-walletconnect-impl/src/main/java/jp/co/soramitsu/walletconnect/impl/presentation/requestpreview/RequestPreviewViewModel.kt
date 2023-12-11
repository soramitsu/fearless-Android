package jp.co.soramitsu.walletconnect.impl.presentation.requestpreview

import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import com.walletconnect.android.cacao.signature.SignatureType
import com.walletconnect.android.utils.cacao.sign
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import com.walletconnect.web3.wallet.utils.CacaoSigner
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.presentation.address
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import jp.co.soramitsu.walletconnect.impl.presentation.dappUrl
import jp.co.soramitsu.walletconnect.impl.presentation.message
import jp.co.soramitsu.walletconnect.impl.presentation.state.WalletConnectMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.StructuredDataEncoder
import org.web3j.crypto.TransactionDecoder
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric

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

    val requestChainFlow = MutableSharedFlow<Chain?>()
        .onStart {
            val value: Chain? = walletConnectInteractor.getChains().firstOrNull { chain ->
                chain.caip2id == recentSession.chainId
            }
            emit(value)
        }
        .stateIn(this, SharingStarted.Eagerly, null)

    private val requestWalletItemFlow: SharedFlow<WalletItemViewState?> = requestChainFlow.filterNotNull().map { requestChain ->
        val requestAddress = recentSession.request.address

        val requestedWallet = accountRepository.allMetaAccounts().firstOrNull { wallet ->
            wallet.address(requestChain) == requestAddress
        }

        if (requestedWallet == null) {
            launch(Dispatchers.Main) {
                println("!!! RPVM Wallet with requested address not found")
                walletConnectRouter.back()
                showError("Wallet with requested address not found")
            }
            return@map null
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

    val state = combine(requestWalletItemFlow, requestChainFlow.filterNotNull()) { requestWallet, requestChain ->
        println("!!! SessionRequestViewModel sessions = $sessions")
        val icon = GradientIconState.Remote(requestChain.icon, "EE0077")

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
                requestChain.name
            ),
            TitleValueViewState(
                "Transaction raw data",
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
        println("!!! RequestPreviewViewModel requestId: ${recentSession.request.id} message to sign = $message")

        launch(Dispatchers.IO) {
            val chain = requestChainFlow.value ?: return@launch

            val address = recentSession.request.address ?: return@launch
            val accountId = chain.accountIdOf(address)
            val metaAccount = accountRepository.findMetaAccount(accountId) ?: return@launch

            val signPrefixedMessageSignature = getSignResult(metaAccount)


            println("!!! RequestPreviewViewModel signPrefixedMessageSignature = $signPrefixedMessageSignature")

//                Sign.signPrefixedMessage(recentSession.request.message.toByteArray(), ECKeyPair(privateKeyInt, publicKeyInt)).let { it.r + it.v + it.s }.toHexString(true)

            val jsonRpcResponse = if (signPrefixedMessageSignature == null) {
                Wallet.Model.JsonRpcResponse.JsonRpcError(
                    id = recentSession.request.id,
                    code = 4001,
                    message = "Error perform sign"
                )
            } else {
                Wallet.Model.JsonRpcResponse.JsonRpcResult(
                    id = recentSession.request.id,

                    result = signPrefixedMessageSignature
                )
            }
            Web3Wallet.respondSessionRequest(
                params = Wallet.Params.SessionRequestResponse(
                    sessionTopic = topic,
                    jsonRpcResponse = jsonRpcResponse
                ),
                onSuccess = {
                    println("!!! Web3Wallet.respondSessionRequest onSuccess: $it")

                },
                onError = {
                    println("!!! Web3Wallet.respondSessionRequest onError: ${it.throwable.message}")
                    it.throwable.printStackTrace()
                }
            )
        }
    }

    private suspend fun getSignResult(metaAccount: MetaAccount) = when (recentSession.request.method) {
        WalletConnectMethod.EthereumSign.method,
        WalletConnectMethod.EthereumPersonalSign.method -> {
            getEthPersonalSignResult(metaAccount)
        }

        WalletConnectMethod.EthereumSignTransaction.method -> {
            getEthSignTransactionResult(metaAccount)
        }

        WalletConnectMethod.EthereumSignTypedData.method,
        WalletConnectMethod.EthereumSignTypedDataV4.method -> {
            getEthSignTypedResult(metaAccount)
        }

        else -> {
            null
        }
    }

    fun String.decodeNumericQuantity(): BigInteger {
        return Numeric.decodeQuantity(this)
    }

    private suspend fun getEthSignTypedResult(metaAccount: MetaAccount): String {
        val ethSignTypedMessage = recentSession.request.message
        val message = StructuredDataEncoder(ethSignTypedMessage).hashStructuredData().toHexString()

        val secrets = accountRepository.getMetaAccountSecrets(metaAccount.id) ?: error("There are no secrets for metaId: ${metaAccount.id}")
        val keypairSchema = secrets[MetaAccountSecrets.SubstrateKeypair]
        val privateKey = keypairSchema[KeyPairSchema.PrivateKey]

        return return CacaoSigner.sign(
            ethSignTypedMessage,
            privateKey,
            SignatureType.EIP191
        ).s
    }

    private suspend fun getEthSignTransactionResult(metaAccount: MetaAccount): String {
        val ethSignTransactionMessage = recentSession.request.message

        println("!!! RequestPreviewViewModel getEthSignTransactionResult = $ethSignTransactionMessage")

        val secrets = accountRepository.getMetaAccountSecrets(metaAccount.id) ?: error("There are no secrets for metaId: ${metaAccount.id}")
        val keypairSchema = secrets[MetaAccountSecrets.SubstrateKeypair]
        val privateKey = keypairSchema[KeyPairSchema.PrivateKey]

        val cred = Credentials.create(privateKey.toHexString())

        val from: String = JSONObject(ethSignTransactionMessage).getString("from")
        val to: String = JSONObject(ethSignTransactionMessage).getString("to")
        val data: String? = JSONObject(ethSignTransactionMessage).getString("data")
        val nonce: BigInteger? = JSONObject(ethSignTransactionMessage).getString("nonce").decodeNumericQuantity()
        val gasPrice: BigInteger? = JSONObject(ethSignTransactionMessage).getString("gasPrice").decodeNumericQuantity()
        val gasLimit: BigInteger? = JSONObject(ethSignTransactionMessage).getString("gasLimit").decodeNumericQuantity()
        val value: BigInteger? = JSONObject(ethSignTransactionMessage).getString("value").decodeNumericQuantity()

        val raw = RawTransaction.createTransaction(
            /* nonce = */ nonce,
            /* gasPrice = */ gasPrice,
            /* gasLimit = */ gasLimit,
            /* to = */ to,
            /* value = */ value,
            /* data = */ data.orEmpty()
        )
        val signed = TransactionEncoder.signMessage(raw, cred)
        return signed.toHexString(true)
    }

    private suspend fun getEthPersonalSignResult(metaAccount: MetaAccount): String {
        val secrets = accountRepository.getMetaAccountSecrets(metaAccount.id) ?: error("There are no secrets for metaId: ${metaAccount.id}")
        val keypairSchema = secrets[MetaAccountSecrets.EthereumKeypair]
        val privateKey = keypairSchema?.get(KeyPairSchema.PrivateKey) ?: throw IllegalArgumentException("no eth keypair")

        return CacaoSigner.sign(
            recentSession.request.message,
            privateKey,
            SignatureType.EIP191
        ).s
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