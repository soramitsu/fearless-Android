package co.jp.soramitsu.tonconnect.domain

import co.jp.soramitsu.tonconnect.model.AppEntity
import co.jp.soramitsu.tonconnect.model.DappModel
import co.jp.soramitsu.tonconnect.model.TonConnectSignRequest
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

interface TonConnectRouter {
    fun back()

    fun backWithResult(vararg results: Pair<String, Any?>)

    fun openTonConnectionDetails(app: AppEntity, proofPayload: String?)
    suspend fun openTonSignRequestWithResult(dapp: DappModel, method: String, signRequest: TonConnectSignRequest): Result<String>

    fun openTonConnectionDetailsForResult(app: AppEntity, proofPayload: String?): Flow<String>
    suspend fun openTonConnectionAndWaitForResult(app: AppEntity, proofPayload: String?): JSONObject
    fun openRawData(payload: String)

}