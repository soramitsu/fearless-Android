package jp.co.soramitsu.tonconnect.api.domain

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import org.json.JSONObject

interface TonConnectRouter {
    fun back()

    fun backWithResult(vararg results: Pair<String, Any?>)

    fun openTonConnectionInfo(dappItem: DappModel)
    suspend fun openTonSignRequestWithResult(
        dapp: DappModel,
        method: String,
        signRequest: TonConnectSignRequest
    ): Result<Pair<String, String>>

    suspend fun openTonConnectionAndWaitForResult(app: AppEntity, proofPayload: String?): JSONObject
    fun openRawData(payload: String)

    fun openOperationSuccess(
        operationHash: String?,
        chainId: ChainId?,
        customMessage: String?,
        customTitle: String?
    )
}
