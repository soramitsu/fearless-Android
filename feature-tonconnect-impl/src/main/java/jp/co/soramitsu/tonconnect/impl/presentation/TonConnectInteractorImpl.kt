package jp.co.soramitsu.tonconnect.impl.presentation

import android.util.ArrayMap
import androidx.core.net.toUri
import co.jp.soramitsu.tonconnect.domain.TonConnectInteractor
import co.jp.soramitsu.tonconnect.domain.TonConnectRepository
import co.jp.soramitsu.tonconnect.domain.TonConnectRouter
import co.jp.soramitsu.tonconnect.model.AppConnectEntity
import co.jp.soramitsu.tonconnect.model.AppEntity
import co.jp.soramitsu.tonconnect.model.BridgeEvent
import co.jp.soramitsu.tonconnect.model.DappConfig
import co.jp.soramitsu.tonconnect.model.TONProof
import co.jp.soramitsu.tonconnect.model.base64
import co.jp.soramitsu.tonconnect.model.hex
import co.jp.soramitsu.tonconnect.model.optStringCompat
import co.jp.soramitsu.tonconnect.model.post
import co.jp.soramitsu.tonconnect.model.sse
import co.jp.soramitsu.tonconnect.model.toDomain
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.util.encodeBase64
import javax.inject.Named
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.ton.DappConfigRemote
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.shared_utils.encrypt.json.coders.type.JsonTypeEncoder
import jp.co.soramitsu.shared_utils.encrypt.json.coders.type.keyGenerator.ScryptKeyGenerator
import jp.co.soramitsu.shared_utils.encrypt.xsalsa20poly1305.Keys
import jp.co.soramitsu.shared_utils.encrypt.xsalsa20poly1305.SecretBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd

class TonConnectInteractorImpl(
    private val chainsRepository: ChainsRepository,
    private val accountRepository: AccountRepository,
    private val keypairProvider: KeypairProvider,
//    private val keyPairRepository: KeypairProvider,
    private val tonApi: TonApi,
    private val tonConnectRouter: TonConnectRouter,
    private val tonConnectRepository: TonConnectRepository,
    private val tonRemoteSource: TonRemoteSource,
    private val contextManager: ContextManager,
    @Named("tonApiHttpClient") private val tonApiHttpClient: OkHttpClient
) : TonConnectInteractor {

    companion object {
        const val BRIDGE_URL = "https://bridge.tonapi.io/bridge"
    }

    override suspend fun getChain(): Chain {
        val tonChainId = "-3"
//        val tonChainId = "-239"
        return chainsRepository.getChain(tonChainId)
    }

    override suspend fun tonConnectApp(clientId: String, manifestUrl: String, proofPayload: String?) {
        val app = readManifest(manifestUrl)

//        println("!!! got dApp result: $app")
        tonConnectRouter.openTonConnectionDetails(app, proofPayload)
    }


    override suspend fun tonConnectAppWithResult(clientId: String?, manifestUrl: String, proofPayload: String?): JSONObject {
        val app = readManifest(manifestUrl)

        val result = tonConnectRouter.openTonConnectionAndWaitForResult(app, proofPayload)
        println("!!! TonConnectInteractor tonConnectAppWithResult clientId = $clientId")
        println("!!! TonConnectInteractor tonConnectAppWithResult remotePublicKey = ${clientId?.hex()?.encodeBase64()}")
        println("!!! TonConnectInteractor tonConnectAppWithResult result = $result")

        clientId?.let {
            withContext(Dispatchers.IO) {
                val tonPublicKey = accountRepository.getSelectedMetaAccount().tonPublicKey

                println("!!! TonConnectInteractor tonConnectAppWithResult tonPublicKey = $tonPublicKey")

                val privateKey = Keys.generatePrivateKey()
                val publicKey = Keys.generatePublicKey(privateKey)


                val encryptedMessage = encryptMessage(
                    remotePublicKey = clientId.hex(),
                    localPrivateKey = privateKey,
                    body = result.toString().toByteArray()
                )
                val publicKeyHex = hex(publicKey)
                println("!!! TonConnectInteractor tonConnectAppWithResult publicKeyHex = $publicKeyHex")
                val url = "${BRIDGE_URL}/message?client_id=$publicKeyHex&to=$clientId&ttl=300"

                println("!!! TonConnectInteractor tonConnectAppWithResult url = $url")
                val mimeType = "text/plain".toMediaType()
                val requestBody = encryptedMessage.encodeBase64().toRequestBody(mimeType)
                println("!!! TonConnectInteractor tonConnectAppWithResult requestBody = $requestBody")

//                val response = tonApi.tonconnectSend(url, requestBody)
                val response = tonApiHttpClient.post(url, requestBody)
                println("!!! TonConnectInteractor tonConnectAppWithResult response = $response")

                if ((response as? Response)?.isSuccessful == true) {
                    println("!!! TonConnectInteractor tonConnectAppWithResult response.isSuccessful, save connection locally")
                    tonConnectRepository.saveConnection(TonConnectionLocal(clientId, app.name, app.iconUrl, app.url))
                    tonConnectRepository.saveConnection(TonConnectionLocal(publicKeyHex, app.name, app.iconUrl, app.url))
                } else {
                    println("!!! TonConnectInteractor tonConnectAppWithResult response code = ${response.code}: ${response.message}")
                }
            }
        }

        return result
    }

    override suspend fun getDappsConfig(): List<DappConfig> {
        return tonApi.getDappsConfig().map {
            it.toDomain()
        }
    }

    fun encryptMessage(
        remotePublicKey: ByteArray,   ///clientId.hex(),
        localPrivateKey: ByteArray,   ///keypair.privateKey,
        body: ByteArray   ///result.toString().toByteArray()
    ): ByteArray {
        val secretBox = SecretBox(remotePublicKey, localPrivateKey)
        val nonce = secretBox.nonce(body)

        val secret = secretBox.seal(nonce, body)

        val encrypt = nonce + secret

        return encrypt

    }

    override suspend fun requestProof(selectedWalletId: Long, app: AppEntity, proofPayload: String): TONProof.Result {
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

    private suspend fun readManifest(url: String): AppEntity {
        val headers = ArrayMap<String, String>().apply {
            set("Connection", "close")
        }
        val response = tonApi.getManifest(url)
//        if (response.code != 200) {
//            throw ManifestException.NotFound(response.code)
//        }
//        val body = response.body?.string() ?: throw ManifestException.FailedParse(NullPointerException())
        return AppEntity(
            response.url,
            response.name,
            response.iconUrl,
            response.termsOfUseUrl,
            response.privacyPolicyUrl
        )

//        return try {
//            AppEntity(body)
//        } catch (e: Throwable) {
//            throw ManifestException.FailedParse(e)
//        }
    }

    override suspend fun disconnect(dappId: String) {
        tonConnectRepository.deleteConnection(dappId)
    }

    override fun getConnectedDapps(): Flow<DappConfig> {
        return tonConnectRepository.observeConnections().map { list ->
            DappConfig(
                type = null,
                apps = list.map { it.toDomain() }
            )
        }
    }

    fun getDiscoverDapps(): List<DappConfigRemote> {
        val localDappsJson =
            contextManager.getContext().assets.open("dapps.json").bufferedReader()
                .use { it.readText() }
        val json = Gson().fromJson<List<DappConfigRemote>>(
            localDappsJson,
            object : TypeToken<List<DappConfigRemote>>() {}.type
        )

        return json
    }

    override fun eventsFlow(
//        connections: List<AppConnectEntity>,
        lastEventId: Long,
    ): Flow<BridgeEvent> {

        return tonConnectRepository.observeConnections()
            .map {
                println("!!! tonConnectRepository.observeConnections: ${it.size}: ${it.joinToString(",") { it.clientId }}")
                it
            }
            .flatMapLatest { connections ->
                val clientIdParam = connections.joinToString(",") { it.clientId }
                val url = "$BRIDGE_URL/events?client_id=$clientIdParam"
                println("!!! eventsFlow url = $url")
                tonApiHttpClient.sse(url, lastEventId).onEach {
                    println("!!! eventsFlow raw event = $it")
                }.filter { it.type == "message" }
//        return tonApi.tonconnectEvents(publicKeys, lastEventId)
                    .mapNotNull { event ->
                        val id = event.id?.toLongOrNull() ?: return@mapNotNull null
                        val from = event.json.optStringCompat("from") ?: return@mapNotNull null
                        val message = event.json.optStringCompat("message") ?: return@mapNotNull null
                        val connection = connections.find { it.clientId == from } ?: return@mapNotNull null
                        println("!!! TON Connect Event for url=$url : $message")
//                    val decryptedMessage = BridgeEvent.Message(connection.decryptEventMessage(message))
                        val decryptedMessage = BridgeEvent.Message(decryptEventMessage(connection.clientId, message))
                        BridgeEvent(
                            eventId = id,
                            message = decryptedMessage,
                            connection = null //connection.copy(),
                        )
                    }
            }
    }

    suspend fun decryptMessage(clientId: String, body: ByteArray): ByteArray {
        val wallet = accountRepository.getSelectedMetaAccount()
        val chain = getChain()
        val keypair = keypairProvider.getKeypairFor(chain, wallet.tonPublicKey!!)

        return AppConnectEntity.decryptMessage(clientId.hex(), keypair.privateKey, body)
    }

    suspend fun decryptEventMessage(clientId: String, message: String): JSONObject {
        val bytes = message.base64
        val decrypted = decryptMessage(clientId, bytes)
        return JSONObject(decrypted.toString(Charsets.UTF_8))
    }

    override suspend fun getSeqno(chain: Chain, accountId: String): Int =
        tonRemoteSource.getSeqno(chain, accountId)

}