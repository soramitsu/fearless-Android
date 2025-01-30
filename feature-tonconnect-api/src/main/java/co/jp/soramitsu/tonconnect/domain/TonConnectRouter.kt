package co.jp.soramitsu.tonconnect.domain

import co.jp.soramitsu.tonconnect.model.AppEntity
import co.jp.soramitsu.tonconnect.model.DappModel
import co.jp.soramitsu.tonconnect.model.TonConnectSignRequest
import jp.co.soramitsu.core.models.ChainId
import org.json.JSONObject

interface TonConnectRouter {
    fun back()

    fun backWithResult(vararg results: Pair<String, Any?>)

    fun openTonConnectionInfo(dappItem: DappModel)
    suspend fun openTonSignRequestWithResult(
        dapp: DappModel,
        method: String,
        signRequest: TonConnectSignRequest
    ): Result<String>

    suspend fun openTonConnectionAndWaitForResult(app: AppEntity, proofPayload: String?): JSONObject
    fun openRawData(payload: String)

    fun openOperationSuccess(
        operationHash: String?,
        chainId: ChainId?,
        customMessage: String?,
        customTitle: String?
    )
}
