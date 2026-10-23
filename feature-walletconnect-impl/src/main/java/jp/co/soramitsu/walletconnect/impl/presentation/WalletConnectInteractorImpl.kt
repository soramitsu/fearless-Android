package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import com.reown.android.cacao.signature.SignatureType
import com.reown.android.utils.cacao.sign
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import com.reown.walletkit.utils.CacaoSigner
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.Sign
import org.web3j.crypto.StructuredDataEncoder
import java.math.BigInteger
import java.util.concurrent.CancellationException

@Suppress("LargeClass")
class WalletConnectInteractorImpl internal constructor(
    private val chainsRepository: ChainsRepository,
    private val ethereumSource: EthereumRemoteSource,
    private val accountRepository: AccountRepository,
    private val keypairProvider: KeypairProvider,
    private val extrinsicService: ExtrinsicService,
    private val transactionSender: EvmTransactionSender,
    private val sessionClient: WalletConnectSessionClient = WalletKitSessionClient,
    private val signedRequests: WalletConnectSignedRequests = WalletConnectSignedRequests.processWide
) : WalletConnectInteractor {

    @Suppress("MagicNumber")
    val Chain.caip2id: String
        get() {
            val namespace = if (isEthereumChain) Caip2Namespace.EIP155 else Caip2Namespace.POLKADOT
            val chainId = id.substring(0, Integer.min(id.length, 32))
            return namespace.value + ":" + chainId
        }

    private val Wallet.Model.SessionRequest.signedMessage: String
        get() = request.message ?: rejectRequest("The request params are malformed")

    private val Wallet.Model.SessionRequest.eip155ChainId: BigInteger
        get() = chainId?.removePrefix("${Caip2Namespace.EIP155.value}:")?.toBigIntegerOrNull()
            ?: rejectRequest("The request names no eip155 chain")

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

        callWalletConnect(onError) {
            WalletKit.approveSession(
                params = Wallet.Params.SessionApprove(
                    proposerPublicKey = proposal.proposerPublicKey,
                    namespaces = sessionNamespaces,
                    relayProtocol = proposal.relayProtocol
                ),
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    override fun rejectSession(
        proposal: Wallet.Model.SessionProposal,
        onSuccess: (Wallet.Params.SessionReject) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        callWalletConnect(onError) {
            WalletKit.rejectSession(
                params = Wallet.Params.SessionReject(
                    proposal.proposerPublicKey,
                    "User rejected"
                ),
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    override fun silentRejectSession(
        proposal: Wallet.Model.SessionProposal,
        onSuccess: (Wallet.Params.SessionReject) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        callWalletConnect(onError) {
            WalletKit.rejectSession(
                params = Wallet.Params.SessionReject(
                    proposal.proposerPublicKey,
                    "Blockchain not supported by wallet"
                ),
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    override suspend fun handleSignAction(
        chain: Chain,
        topic: String,
        recentSession: Wallet.Model.SessionRequest,
        onSignError: (Exception) -> Unit,
        onRequestSuccess: (operationHash: String?, chainId: ChainId?) -> Unit,
        onRequestError: (Wallet.Model.Error) -> Unit
    ) {
        // Every read of the dApp's params happens in here: a malformed request fails, it never throws.
        // A retry after a failed response re-sends what was signed: nothing is signed or broadcast twice.
        val signResult = try {
            signedRequests.responseFor(
                request = recentSession,
                sign = { signRequest(chain, topic, recentSession) },
                broadcast = { transaction ->
                    transactionSender.send(transaction.chainId, transaction.signedTransaction)
                }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (rejection: WalletConnectRequestRejectedException) {
            respondRejected(topic, recentSession.request.id, rejection)
            onSignError(rejection)
            return
        } catch (e: Exception) {
            onSignError(e)
            return
        }

        callWalletConnect(onRequestError) {
            sessionClient.respondSessionRequest(
                params = Wallet.Params.SessionRequestResponse(
                    sessionTopic = topic,
                    jsonRpcResponse = Wallet.Model.JsonRpcResponse.JsonRpcResult(
                        id = recentSession.request.id,
                        result = signResult
                    )
                ),
                onSuccess = {
                    val operationHash = signResult.takeIf {
                        recentSession.request.method == WalletConnectMethod.EthereumSendTransaction.method
                    }
                    val chainId = chain.id.takeIf {
                        recentSession.request.method == WalletConnectMethod.EthereumSendTransaction.method
                    }
                    onRequestSuccess(operationHash, chainId)
                },
                onError = onRequestError
            )
        }
    }

    // Signs with the key of exactly the account the request names, once the user approved that
    // account for this session on the request's network. No other wallet's key is ever used.
    private suspend fun signRequest(
        chain: Chain,
        topic: String,
        recentSession: Wallet.Model.SessionRequest
    ): SignedRequest {
        val method = WalletConnectMethod.values().firstOrNull { it.method == recentSession.request.method }
            ?: rejectRequest("The method is not supported")
        requireRequest(method.isAvailableOn(chain)) { "The method cannot sign on the request network" }
        val verifyContext = walletConnectRuntimeBoundary {
            sessionClient.getVerifyContext(recentSession.request.id)
        }.getOrNull()
        requireRequest(!verifyContext.isFlaggedAsScam) { "Reown Verify reports the dApp as a scam" }
        val account = SigningAccount(keypairProvider, chain, approvedAccountId(chain, topic, recentSession))

        return when (method) {
            WalletConnectMethod.EthereumSign,
            WalletConnectMethod.EthereumPersonalSign ->
                SignedRequest.Response(getEthPersonalSignResult(account, recentSession))
            WalletConnectMethod.EthereumSignTransaction ->
                SignedRequest.Response(getEthSignTransactionResult(account, recentSession))
            WalletConnectMethod.EthereumSendTransaction -> getEthSendTransaction(account, recentSession)
            WalletConnectMethod.EthereumSignTypedData,
            WalletConnectMethod.EthereumSignTypedDataV4 ->
                SignedRequest.Response(getEthSignTypedResult(account, recentSession))
            WalletConnectMethod.PolkadotSignTransaction ->
                SignedRequest.Response(getPolkadotSignTransaction(account, recentSession))
            WalletConnectMethod.PolkadotSignMessage ->
                SignedRequest.Response(getPolkadotSignMessage(account, recentSession))
        }
    }

    private fun approvedAccountId(
        chain: Chain,
        topic: String,
        recentSession: Wallet.Model.SessionRequest
    ): ByteArray {
        val requestChainId = recentSession.chainId?.takeIf { it == chain.caip2id }
            ?: rejectRequest("The request is for another network")
        val address = recentSession.request.address ?: rejectRequest("The request names no valid account")
        val accountId = runCatching { chain.accountIdOf(address) }.getOrNull()
            ?: rejectRequest("The request account is malformed")
        val session = getActiveSessionByTopic(topic) ?: rejectRequest("The session is no longer active")
        requireRequest(session.approvesAccount(requestChainId, accountId, chain::accountIdOf)) {
            "The account is not approved for this session"
        }
        return accountId
    }

    private fun respondRejected(
        topic: String,
        requestId: Long,
        rejection: WalletConnectRequestRejectedException
    ) {
        // Best effort: if this fails the request stays pending and the user can still reject it.
        callWalletConnect(onError = {}) {
            sessionClient.respondSessionRequest(
                params = Wallet.Params.SessionRequestResponse(
                    sessionTopic = topic,
                    jsonRpcResponse = Wallet.Model.JsonRpcResponse.JsonRpcError(
                        id = requestId,
                        code = REQUEST_REJECTED_CODE,
                        message = "Request rejected: ${rejection.message.orEmpty()}"
                    )
                ),
                onSuccess = {},
                onError = {}
            )
        }
    }

    private suspend fun getEthSignTypedResult(
        account: SigningAccount,
        recentSession: Wallet.Model.SessionRequest
    ): String {
        val typedData = recentSession.signedMessage
        typedDataChainId(typedData)?.let { domainChainId ->
            requireRequest(domainChainId == recentSession.eip155ChainId) { "The typed data is for another chain" }
        }
        val message = StructuredDataEncoder(typedData).hashStructuredData()

        val signatureData = Sign.signMessage(message, ECKeyPair.create(account.evmKeypair().privateKey), false)
        val signatureWrapper = SignatureWrapper.Ecdsa(signatureData.v, signatureData.r, signatureData.s)

        return signatureWrapper.signature.toHexString(true)
    }

    // Signed here, broadcast by the caller: these exact bytes are what any retry broadcasts again.
    private suspend fun getEthSendTransaction(
        account: SigningAccount,
        recentSession: Wallet.Model.SessionRequest
    ): SignedRequest.Transaction {
        val chainId = recentSession.chainId?.removePrefix("${Caip2Namespace.EIP155.value}:") ?: error("No chain")
        val raw = checkedTransaction(account, recentSession)

        val signedTransaction = ethereumSource.signRawTransaction(
            chainId,
            raw,
            account.evmKeypair().privateKey.toHexString()
        ).getOrThrow()
        return SignedRequest.Transaction(chainId, signedTransaction)
    }

    private suspend fun getEthSignTransactionResult(
        account: SigningAccount,
        recentSession: Wallet.Model.SessionRequest
    ): String {
        val chainId = recentSession.chainId?.removePrefix("${Caip2Namespace.EIP155.value}:") ?: error("No chain")
        val raw = checkedTransaction(account, recentSession)

        return ethereumSource.signRawTransaction(
            chainId,
            raw,
            account.evmKeypair().privateKey.toHexString()
        ).getOrThrow()
    }

    private suspend fun getEthPersonalSignResult(
        account: SigningAccount,
        recentSession: Wallet.Model.SessionRequest
    ): String {
        val message = recentSession.signedMessage

        return CacaoSigner.sign(
            message,
            account.evmKeypair().privateKey,
            SignatureType.EIP191
        ).s
    }

    private suspend fun getPolkadotSignTransaction(
        account: SigningAccount,
        recentSession: Wallet.Model.SessionRequest
    ): String {
        // The payload exactly as the user saw it; every signed byte below is read from this object.
        val signPayload = PolkadotSignPayload.parse(recentSession.signedMessage)
        requireGenesisHash(signPayload.genesisHash, account.chain)
        signPayload.address?.let { payloadAddress ->
            requireRequest(account.isAccount(payloadAddress)) { "The transaction is for another account" }
        }

        val payloadBytes = signPayload.signingPayload()

        val messageToSign = if (payloadBytes.size > PAYLOAD_HASH_THRESHOLD) {
            payloadBytes.blake2b256()
        } else {
            payloadBytes
        }

        val signature = extrinsicService.createSignature(
            mapCryptoTypeToEncryption(account.cryptoType()),
            account.keypair(),
            messageToSign.toHexString()
        )

        return polkadotSignatureResponse(recentSession.request.id, signature)
    }

    private suspend fun getPolkadotSignMessage(
        account: SigningAccount,
        recentSession: Wallet.Model.SessionRequest
    ): String {
        val data = recentSession.signedMessage

        val (multiChainEncryption, keypair) = if (account.chain.isEthereumBased) {
            MultiChainEncryption.Ethereum to account.evmKeypair()
        } else {
            MultiChainEncryption.Substrate(mapCryptoTypeToEncryption(account.cryptoType())) to account.keypair()
        }

        // Never sign the dApp's bytes raw: they could be an extrinsic payload or, with the EVM key, a transaction.
        val signature = signPolkadotMessage(data, multiChainEncryption, keypair)

        return polkadotSignatureResponse(recentSession.request.id, signature.signature.toHexString(true))
    }

    private fun polkadotSignatureResponse(requestId: Long, signature: String): String = buildJsonObject {
        put("id", requestId)
        put("signature", signature)
    }.toString()

    // The transaction to sign, refused unless it is for the request's chain and from the request's account.
    private fun checkedTransaction(
        account: SigningAccount,
        recentSession: Wallet.Model.SessionRequest
    ): RawTransaction {
        // Read as the preview read it: the recipient, value and data signed are the ones shown.
        val transaction = WalletConnectEvmTransaction.parse(recentSession.signedMessage)
        transaction.chainId?.let { transactionChainId ->
            requireRequest(transactionChainId == recentSession.eip155ChainId) { "The transaction is for another chain" }
        }
        requireRequest(account.isAccount(transaction.from)) {
            "The transaction is from another account"
        }

        return transaction.rawTransaction()
    }

    override fun rejectSessionRequest(
        sessionTopic: String,
        requestId: Long,
        onSuccess: (Wallet.Params.SessionRequestResponse) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        callWalletConnect(onError) {
            sessionClient.respondSessionRequest(
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
    }

    override fun getActiveSessionByTopic(topic: String) = walletConnectRuntimeBoundary {
        sessionClient.getActiveSessionByTopic(topic)
    }.getOrElse { error ->
        error.printStackTrace()
        null
    }

    override fun getPendingListOfSessionRequests(topic: String) = walletConnectRuntimeBoundary {
        sessionClient.getPendingListOfSessionRequests(topic)
    }.getOrElse { error ->
        error.printStackTrace()
        emptyList()
    }

    override fun disconnectSession(
        topic: String,
        onSuccess: (Wallet.Params.SessionDisconnect) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        callWalletConnect(onError) {
            WalletKit.disconnectSession(
                params = Wallet.Params.SessionDisconnect(topic),
                onSuccess = {
                    WCDelegate.refreshConnections()
                    onSuccess(it)
                },
                onError = onError
            )
        }
    }

    override fun pair(
        pairingUri: String,
        onSuccess: (Wallet.Params.Pair) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        val pairingParams = Wallet.Params.Pair(pairingUri)
        callWalletConnect(onError) {
            WalletKit.pair(
                params = pairingParams,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    companion object {
        private const val PAYLOAD_HASH_THRESHOLD = 256
        private const val REQUEST_REJECTED_CODE = 4001
    }
}

/** The account a request signs for, approved by its session. Its key is read only once signing starts. */
private class SigningAccount(
    private val keypairProvider: KeypairProvider,
    val chain: Chain,
    val accountId: ByteArray
) {
    suspend fun keypair(): Keypair = withContext(Dispatchers.IO) {
        keypairProvider.getKeypairFor(chain, accountId)
    }

    suspend fun cryptoType(): CryptoType = withContext(Dispatchers.IO) {
        keypairProvider.getCryptoTypeFor(chain, accountId)
    }

    fun isAccount(address: String?): Boolean =
        address != null && runCatching { chain.accountIdOf(address) }.getOrNull()?.contentEquals(accountId) == true

    /** The EVM key, checked to be the key of exactly this account before it signs anything. */
    suspend fun evmKeypair(): Keypair = keypair().also { keypair ->
        val keyAddress = Credentials.create(keypair.privateKey.toHexString()).address
        check(keyAddress.equals(accountId.toHexString(withPrefix = true), ignoreCase = true)) {
            "The signing key does not belong to the request account"
        }
    }
}
