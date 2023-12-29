package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import com.walletconnect.android.cacao.signature.SignatureType
import com.walletconnect.android.internal.common.scope
import com.walletconnect.android.utils.cacao.sign
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import com.walletconnect.web3.wallet.utils.CacaoSigner
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.shared_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.shared_utils.encrypt.Signer
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.Sign
import org.web3j.crypto.StructuredDataEncoder
import org.web3j.utils.Numeric
import java.math.BigInteger

@Suppress("LargeClass")
class WalletConnectInteractorImpl(
    private val chainsRepository: ChainsRepository,
    private val ethereumSource: EthereumRemoteSource,
    private val accountRepository: AccountRepository,
    private val keypairProvider: KeypairProvider,
    private val chainRegistry: ChainRegistry,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) : WalletConnectInteractor {

    @Suppress("MagicNumber")
    val Chain.caip2id: String
        get() {
            val namespace = if (isEthereumChain) Caip2Namespace.EIP155 else Caip2Namespace.POLKADOT
            val chainId = id.substring(0, Integer.min(id.length, 32))
            return namespace.value + ":" + chainId
        }

    override suspend fun getChains(): List<Chain> = chainsRepository.getChains()

    override suspend fun checkChainsSupported(proposal: Wallet.Model.SessionProposal): Result<Boolean> {
        val chains = getChains()

        val proposalRequiredChains = proposal.requiredNamespaces.flatMap { it.value.chains.orEmpty() }
        val supportedChains = chains.filter {
            it.caip2id in proposalRequiredChains
        }
        val isSupported = supportedChains.size >= proposalRequiredChains.size
        return Result.success(isSupported)
    }

    override suspend fun approveSession(
        proposal: Wallet.Model.SessionProposal,
        selectedWalletIds: Set<Long>,
        selectedOptionalChainIds: Set<String>,
        onSuccess: (Wallet.Params.SessionApprove) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        val chains = getChains()
        val allMetaAccounts = withContext(Dispatchers.IO) { accountRepository.allMetaAccounts() }

        val requiredSessionNamespaces = proposal.requiredNamespaces.mapValues { proposal ->
            val requiredNamespaceChains = chains.filter { chain ->
                chain.caip2id in proposal.value.chains.orEmpty()
            }

            val requiredAccounts = selectedWalletIds.flatMap { walletId ->
                requiredNamespaceChains.mapNotNull { chain ->
                    allMetaAccounts.firstOrNull { it.id == walletId }?.address(chain)?.let { address ->
                        chain.caip2id + ":" + address
                    }
                }
            }

            Wallet.Model.Namespace.Session(
                chains = proposal.value.chains,
                accounts = requiredAccounts,
                events = proposal.value.events,
                methods = proposal.value.methods
            )
        }

        val optionalSessionNamespaces = if (selectedOptionalChainIds.isEmpty()) {
            mapOf()
        } else {
            proposal.optionalNamespaces.mapValuesNotNull { optional ->
                val optionalNamespaceSelectedChains = chains.filter { chain ->
                    chain.caip2id in optional.value.chains.orEmpty() && chain.caip2id in selectedOptionalChainIds
                }

                if (optionalNamespaceSelectedChains.isEmpty()) return@mapValuesNotNull null

                val optionalAccounts = selectedWalletIds.flatMap { walletId ->
                    optionalNamespaceSelectedChains.mapNotNull { chain ->
                        allMetaAccounts.firstOrNull { it.id == walletId }?.address(chain)?.let { address ->
                            chain.caip2id + ":" + address
                        }
                    }
                }

                val sessionChains = optionalNamespaceSelectedChains.map { it.caip2id }

                Wallet.Model.Namespace.Session(
                    chains = sessionChains,
                    accounts = optionalAccounts,
                    events = optional.value.events,
                    methods = optional.value.methods
                )
            }
        }

        val sessionNamespaces = requiredSessionNamespaces.mapValues { required ->
            val optional = optionalSessionNamespaces[required.key]

            Wallet.Model.Namespace.Session(
                chains = (required.value.chains.orEmpty() + optional?.chains.orEmpty()).distinct(),
                accounts = (required.value.accounts + optional?.accounts.orEmpty()).distinct(),
                events = (required.value.events + optional?.events.orEmpty()).distinct(),
                methods = (required.value.methods + optional?.methods.orEmpty()).distinct()
            )
        } + optionalSessionNamespaces.filter { it.key !in requiredSessionNamespaces.keys }

        Web3Wallet.approveSession(
            params = Wallet.Params.SessionApprove(
                proposerPublicKey = proposal.proposerPublicKey,
                namespaces = sessionNamespaces,
                relayProtocol = proposal.relayProtocol
            ),
            onSuccess = onSuccess,
            onError = onError
        )
    }

    override fun rejectSession(
        proposal: Wallet.Model.SessionProposal,
        onSuccess: (Wallet.Params.SessionReject) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        Web3Wallet.rejectSession(
            params = Wallet.Params.SessionReject(
                proposal.proposerPublicKey,
                "User rejected"
            ),
            onSuccess = onSuccess,
            onError = onError
        )
    }

    override fun silentRejectSession(
        proposal: Wallet.Model.SessionProposal,
        onSuccess: (Wallet.Params.SessionReject) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        Web3Wallet.rejectSession(
            params = Wallet.Params.SessionReject(
                proposal.proposerPublicKey,
                "Blockchain not supported by wallet"
            ),
            onSuccess = onSuccess,
            onError = onError
        )
    }

    override suspend fun handleSignAction(
        chain: Chain,
        topic: String,
        recentSession: Wallet.Model.SessionRequest,
        onSignError: (Exception) -> Unit,
        onRequestSuccess: (operationHash: String?, chainId: ChainId?) -> Unit,
        onRequestError: (Wallet.Model.Error) -> Unit
    ) {
        val address = recentSession.request.address ?: return
        val accountId = chain.accountIdOf(address)
        val metaAccount = withContext(Dispatchers.IO) { accountRepository.findMetaAccount(accountId) } ?: return

        val signResult = try {
            getSignResult(metaAccount, recentSession)
        } catch (e: Exception) {
            onSignError(e)
            return
        }

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
                val operationHash = signResult.takeIf {
                    recentSession.request.method == WalletConnectMethod.EthereumSendTransaction.method
                }
                val chainId = chain.id.takeIf {
                    recentSession.request.method == WalletConnectMethod.EthereumSendTransaction.method
                }
                if (chain.isEthereumChain) {
                    scope.launch {
                        BalanceUpdateTrigger.invoke(chainId, true)
                    }
                }
                onRequestSuccess(operationHash, chainId)
            },
            onError = onRequestError
        )
    }

    private suspend fun getSignResult(metaAccount: MetaAccount, recentSession: Wallet.Model.SessionRequest): String? =
        when (recentSession.request.method) {
            WalletConnectMethod.EthereumSign.method,
            WalletConnectMethod.EthereumPersonalSign.method -> {
                getEthPersonalSignResult(metaAccount, recentSession)
            }

            WalletConnectMethod.EthereumSignTransaction.method -> {
                getEthSignTransactionResult(metaAccount, recentSession)
            }

            WalletConnectMethod.EthereumSendTransaction.method -> {
                getEthSendTransactionResult(metaAccount, recentSession)
            }

            WalletConnectMethod.EthereumSignTypedData.method,
            WalletConnectMethod.EthereumSignTypedDataV4.method -> {
                getEthSignTypedResult(metaAccount, recentSession)
            }

            WalletConnectMethod.PolkadotSignTransaction.method -> {
                getPolkadotSignTransaction(recentSession)
            }

            WalletConnectMethod.PolkadotSignMessage.method -> {
                getPolkadotSignMessage(recentSession)
            }

            else -> null
        }

    private suspend fun getEthSignTypedResult(
        metaAccount: MetaAccount,
        recentSession: Wallet.Model.SessionRequest
    ): String {
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

    private suspend fun getEthSendTransactionResult(
        metaAccount: MetaAccount,
        recentSession: Wallet.Model.SessionRequest
    ): String {
        val chainId = recentSession.chainId?.removePrefix("${Caip2Namespace.EIP155.value}:") ?: error("No chain")

        val secrets = accountRepository.getMetaAccountSecrets(metaAccount.id) ?: error("There are no secrets for metaId: ${metaAccount.id}")
        val keypairSchema = secrets[MetaAccountSecrets.EthereumKeypair] ?: error("There are no secrets for metaId: ${metaAccount.id}")
        val privateKey = keypairSchema[KeyPairSchema.PrivateKey]

        val raw = mapToRawTransaction(recentSession.request.message)

        return ethereumSource.sendRawTransaction(
            chainId,
            raw,
            privateKey.toHexString()
        ).getOrThrow()
    }

    private suspend fun getEthSignTransactionResult(
        metaAccount: MetaAccount,
        recentSession: Wallet.Model.SessionRequest
    ): String {
        val chainId = recentSession.chainId?.removePrefix("${Caip2Namespace.EIP155.value}:") ?: error("No chain")
        val secrets = accountRepository.getMetaAccountSecrets(metaAccount.id) ?: error("There are no secrets for metaId: ${metaAccount.id}")
        val keypairSchema = secrets[MetaAccountSecrets.EthereumKeypair] ?: error("There are no secrets for metaId: ${metaAccount.id}")
        val privateKey = keypairSchema[KeyPairSchema.PrivateKey]

        val raw = mapToRawTransaction(recentSession.request.message)

        return ethereumSource.signRawTransaction(
            chainId,
            raw,
            privateKey.toHexString()
        ).getOrThrow()
    }

    private suspend fun getEthPersonalSignResult(
        metaAccount: MetaAccount,
        recentSession: Wallet.Model.SessionRequest
    ): String {
        val secrets = accountRepository.getMetaAccountSecrets(metaAccount.id) ?: error("There are no secrets for metaId: ${metaAccount.id}")
        val keypairSchema = secrets[MetaAccountSecrets.EthereumKeypair]
        val privateKey = keypairSchema?.get(KeyPairSchema.PrivateKey) ?: throw IllegalArgumentException("no eth keypair")

        return CacaoSigner.sign(
            recentSession.request.message,
            privateKey,
            SignatureType.EIP191
        ).s
    }

    private suspend fun getPolkadotSignTransaction(recentSession: Wallet.Model.SessionRequest): String {
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

    private suspend fun getPolkadotSignMessage(recentSession: Wallet.Model.SessionRequest): String {
        val address = recentSession.request.address ?: error("No address")
        val data = recentSession.request.message

        val allChains = getChains()

        val chain = allChains.first { chain -> chain.caip2id == recentSession.chainId }
        val accountId = chain.accountIdOf(address)
        val keypair = keypairProvider.getKeypairFor(chain, accountId)

        val multiChainEncryption = if (chain.isEthereumBased) {
            MultiChainEncryption.Ethereum
        } else {
            val cryptoType = keypairProvider.getCryptoTypeFor(chain, accountId)
            val encryption = mapCryptoTypeToEncryption(cryptoType)
            MultiChainEncryption.Substrate(encryption)
        }

        val dataAsByteArray = runCatching {
            data.fromHex()
        }.getOrElse {
            data.encodeToByteArray()
        }

        val signature = Signer.sign(multiChainEncryption, dataAsByteArray, keypair)

        return JSONObject().apply {
            put("id", recentSession.request.id)
            put("signature", signature.signature.toHexString(true))
        }.toString()
    }

    private fun mapToRawTransaction(request: String): RawTransaction = with(JSONObject(request)) {
        RawTransaction.createTransaction(
            optString("nonce").safeDecodeNumericQuantity(),
            optString("gasPrice").safeDecodeNumericQuantity(),
            optString("gasLimit").safeDecodeNumericQuantity(),
            getString("to"),
            optString("value").safeDecodeNumericQuantity(),
            optString("data")
        )
    }

    private fun String.safeDecodeNumericQuantity(): BigInteger? {
        return kotlin.runCatching { Numeric.decodeQuantity(this) }.getOrNull()
    }

    private fun String.decodeNumericQuantity(): BigInteger {
        return Numeric.decodeQuantity(this)
    }

    override fun rejectSessionRequest(
        sessionTopic: String,
        requestId: Long,
        onSuccess: (Wallet.Params.SessionRequestResponse) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        Web3Wallet.respondSessionRequest(
            params = Wallet.Params.SessionRequestResponse(
                sessionTopic = sessionTopic,
                jsonRpcResponse = Wallet.Model.JsonRpcResponse.JsonRpcError(
                    id = requestId,
                    code = 4001,
                    message = "User rejected request"
                )
            ),
            onSuccess = onSuccess,
            onError = onError
        )
    }

    override fun getActiveSessionByTopic(topic: String) = Web3Wallet.getActiveSessionByTopic(topic)

    override fun getPendingListOfSessionRequests(topic: String) = Web3Wallet.getPendingListOfSessionRequests(topic)

    override fun disconnectSession(
        topic: String,
        onSuccess: (Wallet.Params.SessionDisconnect) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        Web3Wallet.disconnectSession(
            params = Wallet.Params.SessionDisconnect(topic),
            onSuccess = {
                WCDelegate.refreshConnections()
                onSuccess(it)
            },
            onError = onError
        )
    }

    override fun pair(
        pairingUri: String,
        onSuccess: (Wallet.Params.Pair) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        val pairingParams = Wallet.Params.Pair(pairingUri)
        Web3Wallet.pair(
            params = pairingParams,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}
