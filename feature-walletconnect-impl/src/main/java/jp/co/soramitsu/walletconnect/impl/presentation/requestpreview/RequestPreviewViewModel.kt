package jp.co.soramitsu.walletconnect.impl.presentation.requestpreview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.shared_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.shared_utils.encrypt.Signer
import jp.co.soramitsu.shared_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.toHexString
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.Sign
import org.web3j.crypto.StructuredDataEncoder
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric

@HiltViewModel
class RequestPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val walletConnectInteractor: WalletConnectInteractor,
    private val walletConnectRouter: WalletConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository,
    private val addressIconGenerator: AddressIconGenerator,
    private val extrinsicService: ExtrinsicService,
    private val chainRegistry: ChainRegistry,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory,
    private val keypairProvider: KeypairProvider
) : RequestPreviewScreenInterface, BaseViewModel() {

    private val topic: String = savedStateHandle[RequestPreviewFragment.PAYLOAD_TOPIC_KEY] ?: error("No topic provided for request preview screen")

    private val sessions: List<Wallet.Model.SessionRequest> = Web3Wallet.getPendingListOfSessionRequests(topic).also {
        if (it.isEmpty()) error("No session requests found")
    }

    private val recentSession = sessions.sortedByDescending { it.request.id }[0]

    private val requestChainFlow = MutableSharedFlow<Chain?>()
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

    override fun onClose() {
        launch(Dispatchers.Main) {
            walletConnectRouter.back()
        }
    }

    override fun onSignClick() {
        launch(Dispatchers.IO) {
            val chain = requestChainFlow.value ?: return@launch
            val address = recentSession.request.address ?: return@launch
            val accountId = chain.accountIdOf(address)
            val metaAccount = accountRepository.findMetaAccount(accountId) ?: return@launch

            val signResult = getSignResult(metaAccount)

            val jsonRpcResponse = if (signResult == null) {
                Wallet.Model.JsonRpcResponse.JsonRpcError(
                    id = recentSession.request.id,
                    code = 4001,
                    message = "Error perform sign"
                )
            } else {
                Wallet.Model.JsonRpcResponse.JsonRpcResult(
                    id = recentSession.request.id,
                    result = signResult
                )
            }
            Web3Wallet.respondSessionRequest(
                params = Wallet.Params.SessionRequestResponse(
                    sessionTopic = topic,
                    jsonRpcResponse = jsonRpcResponse
                ),
                onSuccess = {
                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        walletConnectRouter.openOperationSuccessAndPopUpToNearestRelatedScreen(
                            null,
                            null,
                            resourceManager.getString(R.string.connection_approve_success_message)
                        )
                    }
                },
                onError = {
                    viewModelScope.launch(Dispatchers.Main) {
                        showError(
                            title = resourceManager.getString(R.string.common_error_general_title),
                            message = resourceManager.getString(R.string.common_try_again) + "\n" + it.throwable.message.orEmpty(),
                            positiveButtonText = resourceManager.getString(R.string.common_ok)
                        )
                    }
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

        WalletConnectMethod.PolkadotSignTransaction.method -> {
            getPolkadotSignTransaction()
        }

        WalletConnectMethod.PolkadotSignMessage.method -> {
            getPolkadotSignMessage(metaAccount)
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
        val message = StructuredDataEncoder(ethSignTypedMessage).hashStructuredData()

        val secrets = accountRepository.getMetaAccountSecrets(metaAccount.id) ?: error("There are no secrets for metaId: ${metaAccount.id}")
        val keypairSchema = secrets[MetaAccountSecrets.EthereumKeypair]
        val privateKey = keypairSchema?.get(KeyPairSchema.PrivateKey)

        val cred = Credentials.create(privateKey?.toHexString())

        val signatureData = Sign.signMessage(message, cred.ecKeyPair, false)
        val signatureWrapper = SignatureWrapper.Ecdsa(signatureData.v, signatureData.r, signatureData.s)

        return signatureWrapper.signature.toHexString(true)
    }

    private suspend fun getEthSignTransactionResult(metaAccount: MetaAccount): String {
        val ethSignTransactionMessage = recentSession.request.message

        val secrets = accountRepository.getMetaAccountSecrets(metaAccount.id) ?: error("There are no secrets for metaId: ${metaAccount.id}")
        val keypairSchema = secrets[MetaAccountSecrets.EthereumKeypair]
        val privateKey = keypairSchema?.get(KeyPairSchema.PrivateKey)

        val cred = Credentials.create(privateKey?.toHexString())

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

    private suspend fun getPolkadotSignTransaction(): String {
        val params = JSONObject(recentSession.request.params)

        val signPayload = JSONObject(params.getString("transactionPayload"))
        val address = signPayload.getString("address")
        val genesisHash = signPayload.getString("genesisHash").drop(2)
        val tip = signPayload.getString("tip").decodeNumericQuantity()

        val chain = chainRegistry.getChain(genesisHash)
        val accountId = chain.accountIdOf(address)

        val keypair = keypairProvider.getKeypairFor(chain, accountId)
        val cryptoType = keypairProvider.getCryptoTypeFor(chain, accountId)
        val extrinsicBuilder = extrinsicBuilderFactory.create(chain, keypair, cryptoType, tip)

        val signature = extrinsicBuilder.build()

        return JSONObject().apply {
            put("id", 0)
            put("signature", signature)
        }.toString()
    }

    private suspend fun getPolkadotSignMessage(metaAccount: MetaAccount): String {
        val signPayload = JSONObject(recentSession.request.params)
        val address = signPayload.getString("address")
        val data = signPayload.getString("message")

        val chain = walletConnectInteractor.getChains().firstOrNull { chain ->
            chain.caip2id == recentSession.chainId
        }!!
        val accountId = chain.accountIdOf(address)

        val keypair = keypairProvider.getKeypairFor(chain, accountId)
        val cryptoType = keypairProvider.getCryptoTypeFor(chain, accountId)
        val multiChainEncryption = if (chain.isEthereumBased) {
            MultiChainEncryption.Ethereum
        } else {
            MultiChainEncryption.Substrate(
                mapCryptoTypeToEncryption(cryptoType)
            )
        }
        val signature = Signer.sign(multiChainEncryption, data.toByteArray(), keypair)

        return JSONObject().apply {
            put("id", 0)
            put("signature", signature)
        }.toString()
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