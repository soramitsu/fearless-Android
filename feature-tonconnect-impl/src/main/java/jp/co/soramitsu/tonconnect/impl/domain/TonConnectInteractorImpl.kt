package jp.co.soramitsu.tonconnect.impl.domain

import android.util.Base64
import androidx.core.net.toUri
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
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonMainnetChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.TONOpCode
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.TonWalletContract.Companion.createIntMsg
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.V4R2WalletContract
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.cellFromBase64
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.hex
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.isBounce
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.loadAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.loadCoins
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.loadMaybeRef
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.loadOpCode
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.storeMaybeRef
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.storeOpCode
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.storeQueryId
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.storeSeqAndValidUntil
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.storeStringTail
import jp.co.soramitsu.shared_utils.encrypt.json.copyBytes
import jp.co.soramitsu.shared_utils.encrypt.xsalsa20poly1305.Keys
import jp.co.soramitsu.shared_utils.encrypt.xsalsa20poly1305.SecretBox
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRepository
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.BridgeEvent
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.DappConfig
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.JsonBuilder
import jp.co.soramitsu.tonconnect.api.model.Security.secureRandom
import jp.co.soramitsu.tonconnect.api.model.TONProof
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import jp.co.soramitsu.tonconnect.api.model.optStringCompat
import jp.co.soramitsu.tonconnect.api.model.optStringCompatJS
import jp.co.soramitsu.tonconnect.api.model.post
import jp.co.soramitsu.tonconnect.api.model.sse
import jp.co.soramitsu.tonconnect.api.model.toDomain
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
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
import org.ton.block.MessageRelaxed
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeRef
import org.ton.tlb.storeTlb
import java.net.URL
import javax.inject.Named

@Suppress("LargeClass")
class TonConnectInteractorImpl(
    private val chainsRepository: ChainsRepository,
    private val accountRepository: AccountRepository,
    private val keypairProvider: KeypairProvider,
    private val tonApi: TonApi,
    private val tonConnectRepository: TonConnectRepository,
    private val tonRemoteSource: TonRemoteSource,
    private val walletRepository: WalletRepository,
    private val keyPairRepository: KeypairProvider,
    @Named("TonSseClient") private val tonSseClient: OkHttpClient,
    @Named("tonApiHttpClient") private val tonApiHttpClient: OkHttpClient
) : TonConnectInteractor {

    override suspend fun getChain(): Chain {
        return chainsRepository.getChain(tonMainnetChainId)
    }

    override suspend fun respondDappConnectRequest(
        clientId: String?,
        request: ConnectRequest,
        signedRequest: JSONObject,
        app: AppEntity
    ) {
        val metaAccount = accountRepository.getSelectedMetaAccount()

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
        if (signedRequest.getString("event") == "connect_error") {
            return
        }
        if (connectResult == null) {
            @Suppress("MagicNumber")
            val bytes = ByteArray(16)
            secureRandom().nextBytes(bytes)
            val dappBrowserClientId = bytes.toHexString(false)

            tonConnectRepository.saveConnection(
                TonConnectionLocal(
                    metaAccount.id,
                    dappBrowserClientId,
                    app.name,
                    app.iconUrl,
                    app.url,
                    ConnectionSource.WEB
                ),
                Keypair(publicKey, privateKey)
            )
        } else if (connectResult.isSuccessful) {
            tonConnectRepository.saveConnection(
                TonConnectionLocal(
                    metaAccount.id,
                    clientId,
                    app.name,
                    app.iconUrl,
                    app.url,
                    ConnectionSource.QR
                ),
                Keypair(publicKey, privateKey)
            )
        }
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
            domain = app.url.toUri().host!!
        )
    }

    override suspend fun readManifest(url: String): AppEntity {
        val response = tonApi.getManifest(url)

        return AppEntity(
            response.url,
            response.name,
            response.iconUrl,
            response.termsOfUseUrl,
            response.privacyPolicyUrl
        )
    }

    override suspend fun disconnect(clientId: String) = withContext(Dispatchers.IO) {
        tonConnectRepository.deleteConnection(clientId)
    }

    override fun getConnectedDappsFlow(source: ConnectionSource): Flow<DappConfig> {
        return tonConnectRepository.observeConnections(source).map { list ->
            DappConfig(
                type = null,
                apps = list.map { DappModel(it) }
            )
        }
    }

    override suspend fun getConnectedDapps(source: ConnectionSource): DappConfig = withContext(Dispatchers.Default) {
        val connections = tonConnectRepository.getConnections(source)
        return@withContext DappConfig(
            type = null,
            apps = connections.map { DappModel(it) }
        )
    }

    override fun eventsFlow(lastEventId: Long): Flow<BridgeEvent> {
        return tonConnectRepository.observeConnections(ConnectionSource.QR)
            .flatMapLatest { connections ->
                val chain = getChain()
                val clientIdParams = connections.mapNotNull {
                    val keypair = tonConnectRepository.getConnectionKeypair(it.clientId)
                    keypair?.publicKey?.toHexString(false)
                }.joinToString(",")
                val bridgeUrl = chain.tonBridgeUrl ?: error("Chain ${chain.name} doesn't support Ton Connect")
                val url = "$bridgeUrl/events?client_id=$clientIdParams"
                tonSseClient.sse(url, lastEventId).filter { it.type == "message" }
                    .mapNotNull { event ->
                        val id = event.id?.toLongOrNull() ?: return@mapNotNull null
                        val from = event.json.optStringCompat("from") ?: return@mapNotNull null
                        val message =
                            event.json.optStringCompatJS("message") ?: return@mapNotNull null
                        val connection =
                            connections.find { it.clientId == from } ?: return@mapNotNull null

                        val messageJsonObject = runCatching {
                            val keyPair = tonConnectRepository.getConnectionKeypair(connection.clientId)
                                ?: error("There is no keypair for clientId: ${connection.clientId}")

                            val decryptedMessage = decryptEventMessage(
                                connection.clientId,
                                keyPair.privateKey,
                                message
                            )
                            JSONObject(decryptedMessage)
                        }.getOrNull() ?: return@mapNotNull null

                        BridgeEvent(
                            eventId = id,
                            message = BridgeEvent.Message(messageJsonObject),
                            connection = connection,
                        )
                    }
            }
            .flowOn(Dispatchers.Default)
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

    @Suppress("MagicNumber", "CyclomaticComplexMethod")
    override suspend fun signMessage(
        chain: Chain,
        method: String,
        signRequest: TonConnectSignRequest,
        metaId: Long
    ) = withContext(Dispatchers.Default) {
        val connectedMetaAccount = async { accountRepository.getMetaAccount(metaId) }

        val tonPublicKey = connectedMetaAccount.await().tonPublicKey ?: error("No account provided")

        val contract = V4R2WalletContract(tonPublicKey)
        val senderAccountId = contract.getAccountId(chain.isTestNet)

        val validUntil: Long = signRequest.validUntil
        val seqnoDeferred = async { tonRemoteSource.getSeqno(chain, senderAccountId) }

        val transfers = mutableListOf<WalletTransfer>()

        val tonAssets = walletRepository.getAssets(connectedMetaAccount.await().id)
            .filter { it.chainId == chain.id }

        for (message in signRequest.messages) {
            val jetton = tonAssets.firstOrNull {
                message.addressValue == it.token.configuration.id
            }

            val jettonCustomPayload = jetton?.let {
                tonRemoteSource.getJettonTransferPayload(chain, senderAccountId, it.id)
            }

            val newStateInit = jettonCustomPayload?.stateInit
            val newCustomPayload = jettonCustomPayload?.customPayload

            val body = if (newCustomPayload != null) {
                run {
                    val payload = message.payloadValue?.cellFromBase64() ?: Cell.empty()
                    val slice = payload.beginParse()
                    val opCode = slice.loadOpCode()
                    if (opCode != TONOpCode.JETTON_TRANSFER) {
                        return@run newCustomPayload
                    }

                    val queryId = slice.loadUInt(64)
                    val jettonAmount = slice.loadCoins()
                    val receiverAddress = slice.loadAddress()
                    val excessesAddress = slice.loadAddress()
                    val customPayload = slice.loadMaybeRef()
                    if (customPayload != null) {
                        return@run payload
                    }

                    val forwardAmount = slice.loadCoins().amount.toLong()
                    val forwardBody = slice.loadMaybeRef()
                    val forwardPayload = body(forwardBody)

                    buildCell {
                        storeOpCode(TONOpCode.JETTON_TRANSFER)
                        storeQueryId(queryId)
                        storeTlb(Coins, jettonAmount)
                        storeTlb(MsgAddressInt, receiverAddress)
                        storeTlb(MsgAddressInt, excessesAddress)
                        storeBit(false) // storeMaybeRef(customPayload)
                        storeTlb(Coins, Coins.ofNano(forwardAmount))
                        storeMaybeRef(forwardPayload)
                    }
                }
            } else {
                message.payload
            }

            val recipientAccountDataDeferred =
                async { tonRemoteSource.loadAccountData(chain, message.addressValue) }

            val builder = WalletTransferBuilder()
            builder.destination = message.address
            builder.messageData =
                MessageData.Raw(body, newStateInit ?: message.stateInit?.let { CellRef(it) })
            builder.bounceable = message.addressValue.isBounceable()
            if (newCustomPayload != null) {
                val defCoins = Coins.of(0.5)
                if (defCoins.amount.value > message.coins.amount.value) {
                    builder.coins = defCoins
                } else {
                    builder.coins = message.coins
                }
            } else {
                builder.coins = message.coins
            }
            val transfer = builder.build()

            transfers.add(transfer)
        }

        require(transfers.size <= contract.maxMessages) {
            "Maximum number of messages in a single transfer is ${contract.maxMessages}"
        }

        val seqNo = seqnoDeferred.await()
        val unsignedBody = CellBuilder.createCell {
            storeUInt(contract.walletId, 32)
            storeSeqAndValidUntil(seqNo, validUntil)
            storeUInt(0, 8)
            for (transfer in transfers) {
                var sendMode = 3
                if (transfer.sendMode > -1) {
                    sendMode = transfer.sendMode
                }
                storeUInt(sendMode, 8)
                val internalMessage = createIntMsg(transfer)
                val internalMessageRef = CellRef(internalMessage)
                this.storeRef(MessageRelaxed.tlbCodec(AnyTlbConstructor), internalMessageRef)
            }
        }
        val keypair =
            keyPairRepository.getKeypairFor(chain, connectedMetaAccount.await().tonPublicKey!!)
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

    private fun body(body: Any?): Cell? {
        if (body == null) {
            return null
        }
        return when (body) {
            is String -> text(body)
            is Cell -> body
            else -> null
        }
    }

    fun text(text: String?): Cell? {
        if (text.isNullOrEmpty()) {
            return null
        }

        @Suppress("MagicNumber")
        return buildCell {
            storeUInt(0, 32)
            storeStringTail(text)
        }
    }

    override suspend fun sendBlockchainMessage(chain: Chain, boc: String): Unit = withContext(Dispatchers.Default) {
        val request = SendBlockchainMessageRequest(boc)
        tonRemoteSource.sendBlockchainMessage(chain, request)
    }

    override suspend fun sendDappMessage(event: BridgeEvent, boc: String): Unit = withContext(Dispatchers.IO) {
        val message = JSONObject().apply {
            put("result", boc)
            put("id", event.message.id)
        }.toString()
        val keypair = tonConnectRepository.getConnectionKeypair(event.connection.clientId)
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
        val formatted = URL(url).host
        val metaAccount = accountRepository.getSelectedMetaAccount()
        return tonConnectRepository.getConnection(metaAccount.id, formatted, source)
    }

    override suspend fun respondDappError(event: BridgeEvent, error: BridgeError): Unit =
        withContext(Dispatchers.Default) {
            val chain = getChain()
            val bridgeUrl = chain.tonBridgeUrl ?: error("Chain ${chain.name} doesn't support Ton Connect")
            val unsignedMessage = JsonBuilder.responseError(event.eventId, error).toString()

            val keypair = tonConnectRepository.getConnectionKeypair(event.connection.clientId)
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
}
