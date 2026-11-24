package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.model.WalletConnectProposal
import co.jp.soramitsu.walletconnect.model.WalletConnectRequest
import co.jp.soramitsu.walletconnect.model.WalletConnectSession
import co.jp.soramitsu.walletconnect.model.WalletConnectSessionNamespace
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClient
import jp.co.soramitsu.walletconnect.impl.protocol.runCatchingNonCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** The answer the wallet sends to one session request. */
internal sealed interface WalletConnectResponse {
    val id: Long

    data class Result(override val id: Long, val result: String) : WalletConnectResponse

    data class Error(override val id: Long, val code: Int, val message: String) : WalletConnectResponse
}

/** The WalletConnect calls the interactor makes, kept apart so its rules run without the relay. */
internal interface WalletConnectSessionClient {
    fun getActiveSessionByTopic(topic: String): WalletConnectSession?

    fun getPendingListOfSessionRequests(topic: String): List<WalletConnectRequest>

    fun getVerifyContext(topic: String, requestId: Long): WalletConnectVerifyContext?

    fun respondSessionRequest(
        topic: String,
        response: WalletConnectResponse,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    )

    suspend fun pair(uri: String)

    suspend fun approveSession(proposal: WalletConnectProposal, namespaces: Map<String, WalletConnectSessionNamespace>)

    suspend fun rejectSession(
        proposal: WalletConnectProposal,
        code: Long,
        message: String
    )

    suspend fun disconnect(topic: String)
}

/** [WalletConnectSessionClient] over the in-repo [WalletConnectClient]. */
internal class ClientSessionClient(
    private val client: WalletConnectClient,
    private val scope: CoroutineScope
) : WalletConnectSessionClient {
    override fun getActiveSessionByTopic(topic: String): WalletConnectSession? = client.session(topic)

    override fun getPendingListOfSessionRequests(topic: String): List<WalletConnectRequest> =
        client.pendingRequests(topic)

    override fun getVerifyContext(topic: String, requestId: Long): WalletConnectVerifyContext? =
        client.verifyContext(topic, requestId)

    override suspend fun pair(uri: String) = client.pair(uri)

    override suspend fun approveSession(
        proposal: WalletConnectProposal,
        namespaces: Map<String, WalletConnectSessionNamespace>
    ) {
        client.approveSession(proposal.id, namespaces)
    }

    override suspend fun rejectSession(
        proposal: WalletConnectProposal,
        code: Long,
        message: String
    ) = client.rejectSession(proposal.id, code, message)

    override suspend fun disconnect(topic: String) = client.disconnect(topic)

    override fun respondSessionRequest(
        topic: String,
        response: WalletConnectResponse,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        scope.launch {
            runCatchingNonCancellation {
                when (response) {
                    is WalletConnectResponse.Result -> client.respond(topic, response.id, walletConnectResultJson(response.result))
                    is WalletConnectResponse.Error -> client.respondError(topic, response.id, response.code.toLong(), response.message)
                }
            }.onSuccess { onSuccess() }.onFailure(onError)
        }
    }
}

/**
 * A signing result as dApps expect it, as the SDK sent it before: a JSON object or array text
 * (`polkadot_*` answers `{"id", "signature"}`) goes out as JSON, anything else (EVM signatures and
 * transaction hashes) as a JSON string.
 */
internal fun walletConnectResultJson(result: String): JsonElement {
    val trimmed = result.trimStart()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        try {
            return Json.parseToJsonElement(result)
        } catch (ignored: SerializationException) {
            // Not JSON after all: sent as text.
        }
    }
    return JsonPrimitive(result)
}
