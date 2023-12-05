package jp.co.soramitsu.walletconnect.impl.presentation.requestpreview

import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ipfs.multibase.CharEncoding
import java.math.BigInteger
import java.nio.charset.Charset
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
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.shared_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.presentation.address
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import jp.co.soramitsu.walletconnect.impl.presentation.dappUrl
import jp.co.soramitsu.walletconnect.impl.presentation.message
import jp.co.soramitsu.walletconnect.impl.presentation.state.WalletConnectMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.bouncycastle.util.encoders.Hex
import org.json.JSONObject
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.Sign
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.core.methods.request.Transaction
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
                clickState =  TitleValueViewState.ClickState.Value(R.drawable.ic_right_arrow_24_align_right, TRANSACTION_RAW_DATA_CLICK_ID)
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
            requestChainFlow.firstOrNull()?.let { chain ->

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
    }

    private suspend fun getSignResult(metaAccount: MetaAccount) = when (recentSession.request.method) {
        WalletConnectMethod.EthereumPersonalSign.method -> {
            getEthPersonalSignResult(metaAccount)
        }
        WalletConnectMethod.EthereumSignTransaction.method -> {
            getEthSignTransactionResult(metaAccount)
        }

        else -> {
            null
        }
    }

    fun String.decodeNumericQuantity(): BigInteger {
        return Numeric.decodeQuantity(this)
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

        requestChainFlow.firstOrNull()?.let { chain ->

            val raw = if (false) {
                RawTransaction.createEtherTransaction(
                    /* chainId = */ chain.id.toLong(),
                    /* nonce = */ nonce,
                    /* gasLimit = */ gasLimit,
                    /* to = */ to,
                    /* value = */ value,
                    /* maxPriorityFeePerGas = */ gasPrice, //maxPriorityFeePerGas
                    /* maxFeePerGas = */ gasPrice //maxFeePerGas
                )
            } else {
                RawTransaction.createTransaction(
                    /* nonce = */ nonce,
                    /* gasPrice = */ gasPrice,
                    /* gasLimit = */ gasLimit,
                    /* to = */ to,
                    /* value = */ value,
                    /* data = */ data.orEmpty()
                )
//                RawTransaction.createTransaction(
//                    /* chainId = */ chain.id.toLong(),
//                    /* nonce = */ nonce,
//                    /* gasLimit = */ gasLimit,
//                    /* to = */ to,
//                    /* value = */ value,
//                    /* data = */ data,
//                    /* maxPriorityFeePerGas = */ gasPrice,
//                    /* maxFeePerGas = */ gasPrice
//                )
            }
            val signed = TransactionEncoder.signMessage(raw, cred)
            return signed.toHexString(true)
        }
        return "signed.toHexString(true)"
    }

    private fun s(ethSignTransactionMessage: String): String = JSONObject(ethSignTransactionMessage).getString("nonce")

    private suspend fun getEthPersonalSignResult(metaAccount: MetaAccount): String {
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

        val ethPersonalSignMessage = "\u0019Ethereum Signed Message:\n" + recentSession.request.message.length + recentSession.request.message
        println("!!! RequestPreviewViewModel ethPersonalSignMessage = $ethPersonalSignMessage")
        println("!!! RequestPreviewViewModel ethPersonalSignMessageBA = ${ethPersonalSignMessage.toByteArray().joinToString { it.toString() }}")
        val getEthereumMessageHash = Sign.getEthereumMessageHash(recentSession.request.message.toByteArray())
        println("!!! RequestPreviewViewModel getEthereumMessageHashBA = ${getEthereumMessageHash.joinToString { it.toString() }}")
        println("!!! RequestPreviewViewModel getEthereumMessageHash = ${getEthereumMessageHash.toString(Charset.forName(CharEncoding.UTF_8))}")
        println("!!! RequestPreviewViewModel getEthereumMessageHash hex = ${getEthereumMessageHash.toHexString(true)}")

        val privateKeyInt = BigInteger(Hex.toHexString(keypair.privateKey), 16)
        val publicKeyInt = Sign.publicKeyFromPrivate(privateKeyInt)

        val signatureData = Sign.signPrefixedMessage(recentSession.request.message.toByteArray(), ECKeyPair(privateKeyInt, publicKeyInt))
        val signaturePrefixed = SignatureWrapper.Ecdsa(v = signatureData.v, r = signatureData.r, s = signatureData.s)
        return signaturePrefixed.signature.toHexString(true)
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