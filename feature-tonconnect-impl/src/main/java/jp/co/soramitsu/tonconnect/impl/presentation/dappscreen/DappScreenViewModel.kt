package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.BridgeEvent
import jp.co.soramitsu.tonconnect.api.model.BridgeMethod
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.JsonBuilder
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
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

    suspend fun connect(version: Int, request: ConnectRequest): JSONObject {
        if (version != 2) {
            return JsonBuilder.connectEventError(BridgeError.BAD_REQUEST)
        }
        val app = interactor.readManifest(request.manifestUrl)

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

        return signedRequest
    }

    suspend fun send(array: JSONArray): JSONObject {
        val messages = BridgeEvent.Message.parse(array)
        if (messages.size == 1) {
            val message = messages.first()
            val id = message.id
            if (message.method != BridgeMethod.SEND_TRANSACTION) {
                return JsonBuilder.responseError(id, BridgeError.METHOD_NOT_SUPPORTED)
            }
            val signRequests = message.params.map { TonConnectSignRequest(it) }
            if (signRequests.size != 1) {
                return JsonBuilder.responseError(id, BridgeError.BAD_REQUEST)
            }
            val signRequest = signRequests.first()

            @Suppress("SwallowedException")
            return try {
                sendTransaction(dapp, message, signRequest).fold(
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
        } else {
            return JsonBuilder.responseError(0, BridgeError.BAD_REQUEST)
        }
    }

    private suspend fun sendTransaction(
        dapp: DappModel,
        message: BridgeEvent.Message,
        signRequest: TonConnectSignRequest
    ): Result<String> {
        val signResult = tonConnectRouter.openTonSignRequestWithResult(dapp, message.method.title, signRequest)

        if(signResult.isFailure) return signResult

        val boc = signResult.requireValue()
        val sendTransactionResult = runCatching { interactor.sendBlockchainMessage(interactor.getChain(), boc) }

        return if(sendTransactionResult.isSuccess) {
            signResult
        } else {
            Result.failure(sendTransactionResult.requireException())
        }
    }

    suspend fun restoreConnection(url: String?): JSONObject {
        if (url == null) {
            return JsonBuilder.connectEventError(BridgeError.UNKNOWN_APP)
        }

        if (interactor.getConnection(url) == null) {
            return JsonBuilder.connectEventError(BridgeError.UNKNOWN_APP)
        }

        val wallet = accountInteractor.selectedMetaAccount()
        val tonPublicKey = wallet.tonPublicKey ?: error("There is no ton account for this wallet")
        return JsonBuilder.connectEventSuccess(tonPublicKey, null, null)
    }

    fun disconnect() {
        viewModelScope.launch {
            interactor.disconnect(dapp.identifier)
        }
    }
}
