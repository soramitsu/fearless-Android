package jp.co.soramitsu.tonconnect.impl.domain

import android.util.ArrayMap
import android.util.Base64
import android.util.Log
import androidx.core.net.toUri
import co.jp.soramitsu.tonconnect.domain.TonConnectInteractor
import co.jp.soramitsu.tonconnect.domain.TonConnectRepository
import co.jp.soramitsu.tonconnect.domain.TonConnectRouter
import co.jp.soramitsu.tonconnect.model.AppEntity
import co.jp.soramitsu.tonconnect.model.BridgeEvent
import co.jp.soramitsu.tonconnect.model.DappConfig
import co.jp.soramitsu.tonconnect.model.DappModel
import co.jp.soramitsu.tonconnect.model.TONProof
import co.jp.soramitsu.tonconnect.model.hex
import co.jp.soramitsu.tonconnect.model.optStringCompat
import co.jp.soramitsu.tonconnect.model.optStringCompatJS
import co.jp.soramitsu.tonconnect.model.post
import co.jp.soramitsu.tonconnect.model.sse
import co.jp.soramitsu.tonconnect.model.toDomain
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.util.encodeBase64
import javax.inject.Named
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.network.ton.DappConfigRemote
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.shared_utils.encrypt.json.copyBytes
import jp.co.soramitsu.shared_utils.encrypt.xsalsa20poly1305.Keys
import jp.co.soramitsu.shared_utils.encrypt.xsalsa20poly1305.SecretBox
import jp.co.soramitsu.shared_utils.extensions.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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
    private val tonApi: TonApi,
    private val tonConnectRouter: TonConnectRouter,
    private val tonConnectRepository: TonConnectRepository,
    private val tonRemoteSource: TonRemoteSource,
    private val contextManager: ContextManager,
    @Named("TonSseClient") private val tonSseClient: OkHttpClient,
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

    override suspend fun tonConnectApp(
        clientId: String,
        manifestUrl: String,
        proofPayload: String?
    ) {
        val app = readManifest(manifestUrl)

        tonConnectRouter.openTonConnectionDetails(app, proofPayload)
    }

    override suspend fun tonConnectAppWithResult(
        clientId: String?,
        manifestUrl: String,
        proofPayload: String?
    ): JSONObject {
        val app = readManifest(manifestUrl)

        val result = tonConnectRouter.openTonConnectionAndWaitForResult(app, proofPayload)

        clientId?.let {
            withContext(Dispatchers.IO) {
                val privateKey = Keys.generatePrivateKey()
                val publicKey = Keys.generatePublicKey(privateKey)

                val encryptedMessage = encryptMessage(
                    remotePublicKey = clientId.hex(),
                    localPrivateKey = privateKey,
                    body = result.toString().toByteArray()
                )
                val publicKeyHex = hex(publicKey)

                 val url = "$BRIDGE_URL/message?client_id=$publicKeyHex&to=$clientId&ttl=300"
                Log.d("&&&", "connected to clientId: ${clientId}")
                val mimeType = "text/plain".toMediaType()
                val requestBody = encryptedMessage.encodeBase64().toRequestBody(mimeType)

                val response = tonApiHttpClient.post(url, requestBody)

                if ((response as? Response)?.isSuccessful == true) {
                    tonConnectRepository.saveConnection(
                        TonConnectionLocal(
                            clientId,
                            app.name,
                            app.iconUrl,
                            app.url
                        ),
                        Keypair(publicKey, privateKey)
                    )
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
                apps = list.map { DappModel(it) }
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
        lastEventId: Long,
    ): Flow<BridgeEvent> {
        return tonConnectRepository.observeConnections()
            .flatMapLatest { connections ->
                val clientIdParams = connections.mapNotNull {
                    val keypair = tonConnectRepository.getConnectionKeypair(it.clientId)
                    keypair?.publicKey?.toHexString(false)
                }.joinToString(", ")

                val url = "$BRIDGE_URL/events?client_id=$clientIdParams"
                tonSseClient.sse(url, lastEventId).filter { it.type == "message" }
                    .mapNotNull { event ->
                        val id = event.id?.toLongOrNull() ?: return@mapNotNull null
                        val from = event.json.optStringCompat("from") ?: return@mapNotNull null
                        val message =
                            event.json.optStringCompatJS("message") ?: return@mapNotNull null
                        val connection =
                            connections.find { it.clientId == from } ?: return@mapNotNull null

                        val messageJsonObject = runCatching {
                            val keyPair = tonConnectRepository.getConnectionKeypair(connection.clientId) ?: throw IllegalStateException("There is no keypair for clientId: ${connection.clientId}")

                            val decryptedMessage = decryptEventMessage(connection.clientId, keyPair.privateKey, message)
                            JSONObject(decryptedMessage)
                        }.getOrNull() ?: return@mapNotNull null

                        BridgeEvent(
                            eventId = id,
                            message = BridgeEvent.Message(messageJsonObject),
                            connection = connection,
                        )
                    }
            }
            .flowOn(Dispatchers.Default).catch { println("&&& eventsFlow catch error: $it") }
    }

    private fun decryptEventMessage(clientId: String, privateKey: ByteArray, message: String): String {
        val bytes = Base64.decode(message, Base64.NO_WRAP)
        val decrypted = decryptMessage(clientId, privateKey, bytes)
        val result = decrypted.toString(Charsets.UTF_8)
        return result
    }

    private fun decryptMessage(clientId: String, privateKey: ByteArray, body: ByteArray): ByteArray {
        val nonce = body.copyBytes(0, 24)
        val encryptedData = body.copyOfRange(24, body.size)
        val secretBox = SecretBox(clientId.hex(), privateKey)
        val secret = secretBox.open(nonce, encryptedData)
        return secret
    }

    override suspend fun getSeqno(chain: Chain, accountId: String): Int =
        tonRemoteSource.getSeqno(chain, accountId)

}