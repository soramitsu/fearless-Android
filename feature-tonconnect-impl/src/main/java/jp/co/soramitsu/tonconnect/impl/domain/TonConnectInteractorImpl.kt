package jp.co.soramitsu.tonconnect.impl.domain

import android.util.Base64
import io.ktor.util.encodeBase64
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.network.ton.SendBlockchainMessageRequest
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.utils.base64
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.fearless_utils.encrypt.json.copyBytes
import jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305.Keys
import jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305.SecretBox
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonMainnetChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.V4R2WalletContract
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.hex
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.isBounce
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRepository
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.BridgeEvent
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.DappConfig
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.JsonBuilder
import jp.co.soramitsu.tonconnect.api.model.SSEvent
import jp.co.soramitsu.tonconnect.api.model.Security.secureRandom
import jp.co.soramitsu.tonconnect.api.model.TONProof
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import jp.co.soramitsu.tonconnect.api.model.TonConnectUrlValidator
import jp.co.soramitsu.tonconnect.api.model.TonConnectionIdentity
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import jp.co.soramitsu.tonconnect.api.model.optStringCompat
import jp.co.soramitsu.tonconnect.api.model.optStringCompatJS
import jp.co.soramitsu.tonconnect.api.model.post
import jp.co.soramitsu.tonconnect.api.model.sse
import jp.co.soramitsu.tonconnect.api.model.toDomain
import jp.co.soramitsu.tonconnect.impl.data.TonConnectBridgeEventLedger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.bitstring.BitString
import org.ton.block.AddrNone
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.Either
import org.ton.block.ExtInMsgInfo
import org.ton.block.Maybe
import org.ton.block.Message
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeTlb
import javax.inject.Named

@Suppress("LargeClass")
class TonConnectInteractorImpl(
    private val chainsRepository: ChainsRepository,
    private val accountRepository: AccountRepository,
    private val keypairProvider: KeypairProvider,
    private val tonApi: TonApi,
    private val tonConnectRepository: TonConnectRepository,
    private val tonRemoteSource: TonRemoteSource,
    private val keyPairRepository: KeypairProvider,
    @Named("TonSseClient") private val tonSseClient: OkHttpClient,
    @Named("tonApiHttpClient") private val tonApiHttpClient: OkHttpClient,
    private val bridgeEventLedger: TonConnectBridgeEventLedger,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : TonConnectInteractor {

    override suspend fun getChain(): Chain {
        return chainsRepository.getChain(tonMainnetChainId)
    }

    override suspend fun respondDappConnectRequest(
        clientId: String?,
        request: ConnectRequest,
        signedRequest: JSONObject,
        app: AppEntity
    ): TonDappConnection? {
        val isConnectError = signedRequest.optString("event") == CONNECT_ERROR_EVENT
        // The connection belongs to the wallet whose key the reply gives the dApp. It is
        // resolved before anything reaches the dApp, so no reply is sent for a connection
        // that could not be stored under that wallet.
        val boundWalletId = if (isConnectError) null else boundWalletId(signedRequest)

        val privateKey = Keys.generatePrivateKey()
        val publicKey = Keys.generatePublicKey(privateKey)

        val connectResult = clientId?.let {
            withContext(Dispatchers.IO) {
                val encryptedMessage = encryptMessage(
                    remotePublicKey = clientId.hex(),
                    localPrivateKey = privateKey,
                    body = signedRequest.toString().toByteArray()
                )
                val publicKeyHex = hex(publicKey)

                sendDappMessage(encryptedMessage, publicKeyHex, clientId)
            }
        }
        if (boundWalletId == null) {
            return null
        }
        val connection = if (clientId == null) {
            TonConnectionLocal(
                boundWalletId,
                newDappBrowserClientId(),
                app.name,
                app.iconUrl,
                app.url,
                ConnectionSource.WEB
            )
        } else if (connectResult?.isSuccessful == true) {
            TonConnectionLocal(
                boundWalletId,
                clientId,
                app.name,
                app.iconUrl,
                app.url,
                ConnectionSource.QR
            )
        } else {
            return null
        }
        tonConnectRepository.saveConnection(connection, Keypair(publicKey, privateKey))
        return TonDappConnection(connection)
    }

    @Suppress("MagicNumber")
    private fun newDappBrowserClientId(): String {
        val bytes = ByteArray(16)
        secureRandom().nextBytes(bytes)
        return bytes.toHexString(false)
    }

    private suspend fun boundWalletId(signedRequest: JSONObject): Long {
        val replyPublicKey = connectReplyPublicKey(signedRequest)
        val wallets = accountRepository.allMetaAccounts().map { it.id to it.tonPublicKey }
        val selectedWalletId = accountRepository.getSelectedMetaAccount().id
        return TonConnectWalletBinding.boundWalletId(replyPublicKey, wallets, selectedWalletId)
            ?: error("The connect reply names no wallet of this app")
    }

    private fun connectReplyPublicKey(signedRequest: JSONObject): ByteArray {
        val items = signedRequest.getJSONObject("payload").getJSONArray("items")
        val tonAddressItem = (0 until items.length())
            .map(items::getJSONObject)
            .single { it.optString("name") == TON_ADDRESS_ITEM }
        return tonAddressItem.getString("publicKey").fromHex()
    }

    override suspend fun getDappsConfig(): List<DappConfig> {
        return tonApi.getDappsConfig().map {
            it.toDomain()
        }
    }

    fun encryptMessage(
        remotePublicKey: ByteArray, // /clientId.hex(),
        localPrivateKey: ByteArray, // /keypair.privateKey,
        body: ByteArray // /result.toString().toByteArray()
    ): ByteArray {
        val secretBox = SecretBox(remotePublicKey, localPrivateKey)
        val nonce = secretBox.nonce(body)

        val secret = secretBox.seal(nonce, body)

        val encrypt = nonce + secret

        return encrypt
    }

    override suspend fun requestProof(
        selectedWalletId: Long,
        app: AppEntity,
        proofPayload: String
    ): TONProof.Result {
        val wallet = accountRepository.getMetaAccount(selectedWalletId)

        val chain = getChain()
        val keypair = keypairProvider.getKeypairFor(chain, wallet.tonPublicKey!!)
        val privateKey = PrivateKeyEd25519.of(keypair.privateKey)

        return TONProof.sign(
            address = AddrStd(wallet.tonPublicKey!!.tonAccountId(chain.isTestNet)),
            secretKey = privateKey,
            payload = proofPayload,
            domain = TonConnectUrlValidator.host(app.url) ?: error("Invalid dApp URL")
        )
    }

    override suspend fun readManifest(url: String): AppEntity {
        val normalizedManifestUrl = TonConnectUrlValidator.normalizeManifestUrl(url)
        val manifestOrigin = TonConnectUrlValidator.origin(normalizedManifestUrl)
        val response = tonApi.getManifest(normalizedManifestUrl)

        val trustedDappUrl = runCatching {
            TonConnectUrlValidator.normalizeDappUrl(response.url)
        }.getOrNull()?.takeIf {
            TonConnectUrlValidator.isSameOrigin(it, manifestOrigin)
        } ?: manifestOrigin

        return AppEntity(
            trustedDappUrl,
            response.name,
            response.iconUrl,
            response.termsOfUseUrl,
            response.privacyPolicyUrl
        )
    }

    override suspend fun disconnect(identity: TonConnectionIdentity) = withContext(Dispatchers.IO) {
        tonConnectRepository.deleteConnection(identity)
    }

    override fun getConnectedDappsFlow(source: ConnectionSource): Flow<DappConfig> {
        return accountRepository.selectedMetaAccountFlow()
            .flatMapLatest { metaAccount ->
                tonConnectRepository.observeConnections(metaAccount.id, source)
            }
            .map { list ->
                DappConfig(
                    type = null,
                    apps = list.map { DappModel(it) }
                )
            }
    }

    override suspend fun getConnectedDapps(source: ConnectionSource): DappConfig = withContext(Dispatchers.Default) {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val connections = tonConnectRepository.getConnections(metaAccount.id, source)
        return@withContext DappConfig(
            type = null,
            apps = connections.map { DappModel(it) }
        )
    }

    override fun eventsFlow(): Flow<BridgeEvent> {
        return accountRepository.selectedMetaAccountFlow()
            .flatMapLatest { metaAccount ->
                tonConnectRepository.observeConnections(
                    metaId = metaAccount.id,
                    source = ConnectionSource.QR
                )
            }
            .flatMapLatest { connections -> bridgeEvents(connections) }
            .flowOn(Dispatchers.Default)
    }

    private suspend fun bridgeEvents(connections: List<TonDappConnection>): Flow<BridgeEvent> {
        val subscribed = connections.mapNotNull { connection ->
            tonConnectRepository.getConnectionKeypair(connection.identity())?.let { keypair ->
                connection to keypair.publicKey.toHexString(false)
            }
        }
        if (subscribed.isEmpty()) {
            return emptyFlow()
        }
        val chain = getChain()
        val bridgeUrl = chain.tonBridgeUrl ?: error("Chain ${chain.name} doesn't support Ton Connect")
        val url = "$bridgeUrl/events?client_id=${subscribed.joinToString(",") { it.second }}"
        val subscribedConnections = subscribed.map { it.first }
        val clientIds = subscribedConnections.map { it.clientId }

        // Every (re)connect resumes after the last recorded event instead of replaying the queue.
        return tonSseClient.sse(url) { bridgeEventLedger.resumeEventId(bridgeUrl, clientIds) }
            .filter { it.type == "message" }
            .mapNotNull { event -> admitBridgeEvent(bridgeUrl, event, subscribedConnections) }
    }

    // A bridge event becomes a request for the wallet at most once per connection: events at
    // or before the connection's last recorded event and requests already handed over are dropped.
    private suspend fun admitBridgeEvent(
        bridgeUrl: String,
        event: SSEvent,
        connections: List<TonDappConnection>
    ): BridgeEvent? {
        val eventId = event.id?.toLongOrNull()
        val connection = event.json.optStringCompat("from")?.let { from ->
            connections.singleOrNull { it.clientId == from }
        }
        if (eventId == null || connection == null) {
            return null
        }
        if (!bridgeEventLedger.isNewEvent(bridgeUrl, connection.clientId, eventId)) {
            return null
        }

        val message = readBridgeMessage(event, connection)
        if (message == null) {
            bridgeEventLedger.recordEvent(bridgeUrl, connection.clientId, eventId)
            return null
        }
        val isFirstDelivery = bridgeEventLedger.admitRequest(bridgeUrl, connection.clientId, eventId, message.id)
        return BridgeEvent(eventId = eventId, message = message, connection = connection).takeIf { isFirstDelivery }
    }

    private suspend fun readBridgeMessage(event: SSEvent, connection: TonDappConnection): BridgeEvent.Message? {
        return try {
            val encryptedMessage = event.json.optStringCompatJS("message") ?: return null
            val keyPair = tonConnectRepository.getConnectionKeypair(connection.identity()) ?: return null
            val decryptedMessage = decryptEventMessage(connection.clientId, keyPair.privateKey, encryptedMessage)
            BridgeEvent.Message(JSONObject(decryptedMessage))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ignored: Exception) {
            // A message that cannot be decrypted or parsed is dropped; it must not end the subscription.
            null
        }
    }

    private fun decryptEventMessage(
        clientId: String,
        privateKey: ByteArray,
        message: String
    ): String {
        val bytes = Base64.decode(message, Base64.NO_WRAP)
        val decrypted = decryptMessage(clientId, privateKey, bytes)
        val result = decrypted.toString(Charsets.UTF_8)
        return result
    }

    @Suppress("MagicNumber")
    private fun decryptMessage(
        clientId: String,
        privateKey: ByteArray,
        body: ByteArray
    ): ByteArray {
        val nonce = body.copyBytes(0, 24)
        val encryptedData = body.copyOfRange(24, body.size)
        val secretBox = SecretBox(clientId.hex(), privateKey)
        val secret = secretBox.open(nonce, encryptedData)
        return secret
    }

    override suspend fun prepareSignRequest(params: List<String>, metaId: Long): TonConnectSignRequest =
        withContext(Dispatchers.Default) {
            val signRequest = params.singleOrNull()
                ?.let { param -> runCatching { TonConnectSignRequest(param) }.getOrNull() }
                ?: throw BridgeError.Exception(BridgeError.BAD_REQUEST)
            validUntilToSign(signRequest, V4R2WalletContract(tonPublicKey(metaId)))
            signRequest
        }

    private suspend fun tonPublicKey(metaId: Long): ByteArray {
        return accountRepository.getMetaAccount(metaId).tonPublicKey
            ?: throw BridgeError.Exception(BridgeError.BAD_REQUEST)
    }

    private fun validUntilToSign(signRequest: TonConnectSignRequest, contract: V4R2WalletContract): Long {
        return TonConnectSendTransactionPolicy.validUntilToSign(
            request = signRequest,
            wallet = contract.address,
            nowSeconds = currentTimeMillis() / MILLIS_PER_SECOND,
            maxMessages = contract.maxMessages
        )
    }

    override suspend fun signMessage(
        chain: Chain,
        method: String,
        signRequest: TonConnectSignRequest,
        metaId: Long
    ) = withContext(Dispatchers.Default) {
        val tonPublicKey = tonPublicKey(metaId)
        val contract = V4R2WalletContract(tonPublicKey)
        val senderAccountId = contract.getAccountId(chain.isTestNet)

        // The request must come from this wallet, for mainnet, and not have expired. What is
        // signed expires at the dApp's valid_until but never more than five minutes from now.
        val validUntil = validUntilToSign(signRequest, contract)
        val seqnoDeferred = async { tonRemoteSource.getSeqno(chain, senderAccountId) }

        // Every message is signed exactly as the dApp requested and the user approved it.
        val transfers = signRequest.messages.map { message ->
            async {
                val recipientAccountData = tonRemoteSource.loadAccountData(chain, message.addressValue)
                TonConnectTransfers.transfer(message, isBounce(message.addressValue, recipientAccountData))
            }
        }.awaitAll()

        val seqNo = seqnoDeferred.await()
        val unsignedBody = TonConnectTransfers.unsignedBody(contract.walletId, seqNo, validUntil, transfers)
        val keypair = keyPairRepository.getKeypairFor(chain, tonPublicKey)
        val privateKey = PrivateKeyEd25519(keypair.privateKey)
        val hash = privateKey.sign(unsignedBody.hash().toByteArray())
        val signature = BitString(hash)
        val signedBody = CellBuilder.createCell {
            storeBits(signature)
            storeBits(unsignedBody.bits)
            storeRefs(unsignedBody.refs)
        }

        val info = ExtInMsgInfo(
            src = AddrNone,
            dest = contract.address,
            importFee = Coins()
        )

        val init = if (seqNo == 0) {
            contract.stateInit
        } else {
            null
        }

        val maybeStateInit = Maybe.of(init?.let { Either.of<StateInit, CellRef<StateInit>>(null, CellRef(it)) })

        val body = Either.of<Cell, CellRef<Cell>>(null, CellRef(signedBody))
        val message = Message(
            info = info,
            init = maybeStateInit,
            body = body
        )
        val cell = buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
        }
        return@withContext cell.base64() to cell.hash().toHex()
    }

    override suspend fun sendBlockchainMessage(chain: Chain, boc: String): Unit = withContext(Dispatchers.Default) {
        val request = SendBlockchainMessageRequest(boc)
        tonRemoteSource.sendBlockchainMessage(chain, request)
    }

    override suspend fun sendDappMessage(event: BridgeEvent, boc: String): Unit = withContext(Dispatchers.IO) {
        val message = JSONObject().apply {
            put("result", boc)
            put("id", event.requestId)
        }.toString()
        val keypair = tonConnectRepository.getConnectionKeypair(
            event.connection.identity()
        )
            ?: error("There is no keypair for this dapp connection")
        val encryptedMessage = encryptMessage(
            event.connection.clientId.hex(),
            keypair.privateKey,
            message.toByteArray()
        )
        sendDappMessage(
            encryptedMessage,
            keypair.publicKey.toHexString(false),
            event.connection.clientId
        )
    }

    private suspend fun sendDappMessage(
        encryptedMessage: ByteArray,
        publicKey: String,
        clientId: String
    ) = withContext(Dispatchers.Default) {
        val chain = getChain()
        val bridgeUrl = chain.tonBridgeUrl ?: error("Chain ${chain.name} doesn't support Ton Connect")
        val url = "$bridgeUrl/message?client_id=$publicKey&to=$clientId&ttl=300"
        val mimeType = "text/plain".toMediaType()
        val requestBody = encryptedMessage.encodeBase64().toRequestBody(mimeType)

        tonApiHttpClient.post(url, requestBody)
    }

    override suspend fun getConnection(url: String, source: ConnectionSource): TonConnectionLocal? {
        TonConnectUrlValidator.host(url) ?: return null
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val sameOriginConnections = tonConnectRepository
            .getConnections(metaAccount.id, source)
            .filter { connection ->
                TonConnectUrlValidator.isSameOrigin(connection.url, url)
            }
        if (sameOriginConnections.size != 1) return null
        return tonConnectRepository.getConnection(
            sameOriginConnections.single().identity()
        )
    }

    override suspend fun getConnection(identity: TonConnectionIdentity): TonConnectionLocal? {
        return tonConnectRepository.getConnection(identity)
    }

    override suspend fun respondDappError(event: BridgeEvent, error: BridgeError): Unit =
        withContext(Dispatchers.Default) {
            val chain = getChain()
            val bridgeUrl = chain.tonBridgeUrl ?: error("Chain ${chain.name} doesn't support Ton Connect")
            // A response names the dApp's request id, never the bridge's event id.
            val unsignedMessage = JsonBuilder.responseError(event.requestId, error).toString()

            val keypair = tonConnectRepository.getConnectionKeypair(
                event.connection.identity()
            )
                ?: error("There is no keypair for this dapp connection")
            val encryptedMessage = encryptMessage(
                event.connection.clientId.hex(),
                keypair.privateKey,
                unsignedMessage.toByteArray()
            )

            val mimeType = "text/plain".toMediaType()
            val requestBody = encryptedMessage.encodeBase64().toRequestBody(mimeType)

            val url = "$bridgeUrl/message?client_id=${keypair.publicKey.toHexString(false)}&to=${event.connection.clientId}&ttl=300"
            tonApiHttpClient.post(url, requestBody)
        }

    private companion object {
        const val CONNECT_ERROR_EVENT = "connect_error"
        const val TON_ADDRESS_ITEM = "ton_addr"
        const val MILLIS_PER_SECOND = 1_000L
    }
}
