package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.BridgeEvent
import jp.co.soramitsu.tonconnect.api.model.BridgeMethod
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.JsonBuilder
import jp.co.soramitsu.tonconnect.api.model.TonConnectUrlValidator
import jp.co.soramitsu.tonconnect.api.model.TonConnectionIdentity
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CancellationException
import javax.inject.Inject

@HiltViewModel
class DappScreenViewModel @Inject constructor(
    private val interactor: TonConnectInteractor,
    private val accountInteractor: AccountInteractor,
    private val tonConnectRouter: TonConnectRouter,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private val dapp = savedStateHandle.get<DappModel>(DappScreenFragment.PAYLOAD_DAPP_KEY)
        ?: error("No required info provided")

    val state = MutableStateFlow(dapp)

    // The only origin this browser serves the bridge to.
    private val trustedOrigin: String? = dapp.url?.let { url ->
        runCatching { TonConnectUrlValidator.origin(url) }.getOrNull()
    }

    /**
     * The connection this page established or restored in this session. It may
     * belong to a wallet other than the selected one when the user picked that one.
     */
    internal var sessionConnection: TonConnectionIdentity? = null

    suspend fun connect(
        version: Int,
        request: ConnectRequest,
        pageUrl: String?
    ): JSONObject {
        val app = when (val authorization = authorizeConnect(version, request, pageUrl)) {
            is ConnectAuthorization.Rejected -> return JsonBuilder.connectEventError(authorization.error)
            is ConnectAuthorization.Approved -> authorization.app
        }

        val signedRequest =
            tonConnectRouter.openTonConnectionAndWaitForResult(app, request.proofPayload)
        val result = kotlin.runCatching {
            interactor.respondDappConnectRequest(
                null,
                request,
                signedRequest,
                app
            )
        }

        if (result.isFailure) {
            tonConnectRouter.back()
            throw IllegalStateException(result.exceptionOrNull())
        }
        result.getOrNull()?.let { connection -> sessionConnection = connection.identity() }

        return signedRequest
    }

    /**
     * A page may only connect for itself: it must be the trusted origin's page and
     * name a manifest of its own origin, so the stored connection, the proof domain
     * and later signing requests all belong to the page that asked.
     */
    internal suspend fun authorizeConnect(
        version: Int,
        request: ConnectRequest,
        pageUrl: String?
    ): ConnectAuthorization {
        val isOwnManifest = isTrustedPage(pageUrl) &&
            TonConnectUrlValidator.isSameOrigin(request.manifestUrl, pageUrl.orEmpty())
        if (version != SUPPORTED_PROTOCOL_VERSION || !isOwnManifest) {
            return ConnectAuthorization.Rejected(BridgeError.BAD_REQUEST)
        }
        val app = runCatching { interactor.readManifest(request.manifestUrl) }.getOrNull()
            ?: return ConnectAuthorization.Rejected(BridgeError.UNKNOWN_APP)
        return ConnectAuthorization.Approved(app)
    }

    suspend fun send(array: JSONArray, pageUrl: String?): JSONObject {
        val messages = BridgeEvent.Message.parse(array)
        if (messages.size != 1) {
            return JsonBuilder.responseError(0, BridgeError.BAD_REQUEST)
        }
        val message = messages.first()
        val id = message.id
        if (message.method != BridgeMethod.SEND_TRANSACTION) {
            return JsonBuilder.responseError(id, BridgeError.METHOD_NOT_SUPPORTED)
        }

        @Suppress("SwallowedException")
        return try {
            sendTransaction(message, pageUrl).fold(
                { boc -> JsonBuilder.responseSendTransaction(id, boc) },
                { JsonBuilder.responseError(id, BridgeError.UNKNOWN) }
            )
        } catch (e: CancellationException) {
            JsonBuilder.responseError(id, BridgeError.USER_DECLINED_TRANSACTION)
        } catch (e: BridgeError.Exception) {
            JsonBuilder.responseError(id, e.error)
        } catch (e: Throwable) {
            JsonBuilder.responseError(id, BridgeError.UNKNOWN)
        }
    }

    /**
     * Signs only for an established connection of this page, with the wallet that
     * connection is bound to, and only after the request passed validation for it.
     */
    internal suspend fun sendTransaction(message: BridgeEvent.Message, pageUrl: String?): Result<String> {
        val connection = activeConnection(pageUrl) ?: throw BridgeError.Exception(BridgeError.UNKNOWN_APP)
        val signRequest = interactor.prepareSignRequest(message.params, connection.metaId)
        val signResult = tonConnectRouter.openTonSignRequestWithResult(
            DappModel(TonDappConnection(connection)),
            message.method.title,
            signRequest
        )

        if (signResult.isFailure) return signResult.map { it.first }

        val (boc, _) = signResult.requireValue()
        val sendTransactionResult = runCatching { interactor.sendBlockchainMessage(interactor.getChain(), boc) }

        return if (sendTransactionResult.isSuccess) {
            signResult.map { it.first }
        } else {
            Result.failure(sendTransactionResult.requireException())
        }
    }

    suspend fun restoreConnection(pageUrl: String?): JSONObject {
        val connection = activeConnection(pageUrl) ?: return JsonBuilder.connectEventError(BridgeError.UNKNOWN_APP)
        sessionConnection = TonConnectionIdentity(connection)

        // The page is told the address of the wallet its connection is bound to.
        val wallet = accountInteractor.getMetaAccount(connection.metaId)
        val tonPublicKey = wallet.tonPublicKey ?: error("There is no ton account for this wallet")
        return JsonBuilder.connectEventSuccess(tonPublicKey, null, null)
    }

    fun disconnect(pageUrl: String?) {
        viewModelScope.launch {
            val connection = activeConnection(pageUrl) ?: return@launch
            sessionConnection = null
            interactor.disconnect(TonConnectionIdentity(connection))
        }
    }

    /**
     * The connection the page at [pageUrl] may use: the one it established or
     * restored in this session, otherwise the selected wallet's connection for its origin.
     */
    internal suspend fun activeConnection(pageUrl: String?): TonConnectionLocal? {
        if (pageUrl == null || !isTrustedPage(pageUrl)) return null
        val sessionMatch = sessionConnection
            ?.let { identity -> interactor.getConnection(identity) }
            ?.takeIf { connection -> TonConnectUrlValidator.isSameOrigin(connection.url, pageUrl) }
        return sessionMatch ?: interactor.getConnection(pageUrl, ConnectionSource.WEB)
    }

    private fun isTrustedPage(pageUrl: String?): Boolean = DappBridgeAccess.isTrustedPage(pageUrl, trustedOrigin)

    internal sealed interface ConnectAuthorization {
        data class Approved(val app: AppEntity) : ConnectAuthorization
        data class Rejected(val error: BridgeError) : ConnectAuthorization
    }

    private companion object {
        const val SUPPORTED_PROTOCOL_VERSION = 2
    }
}
