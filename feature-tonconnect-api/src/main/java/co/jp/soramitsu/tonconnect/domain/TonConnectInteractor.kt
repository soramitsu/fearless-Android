package co.jp.soramitsu.tonconnect.domain

import co.jp.soramitsu.tonconnect.model.AppEntity
import co.jp.soramitsu.tonconnect.model.BridgeEvent
import co.jp.soramitsu.tonconnect.model.DappConfig
import co.jp.soramitsu.tonconnect.model.SignRequestEntity
import co.jp.soramitsu.tonconnect.model.TONProof
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

interface TonConnectInteractor {
    suspend fun tonConnectApp(clientId: String, manifestUrl: String, proofPayload: String?)
    suspend fun tonConnectAppWithResult(clientId: String?, manifestUrl: String, proofPayload: String?): JSONObject
//    suspend fun openTonSignRequest(appUrl: String, method: String, signRequest: SignRequestEntity): JSONObject
//    suspend fun openTonSignPreview()

    suspend fun getChain(): Chain

    //    fun getDiscoverDapps(): List<DappConfigRemote>
    suspend fun getDappsConfig(): List<DappConfig>

    fun getConnectedDapps(): Flow<DappConfig>
    suspend fun requestProof(selectedWalletId: Long, app: AppEntity, proofPayload: String): TONProof.Result

    fun eventsFlow(
//        connections: List<AppConnectEntity>,
        lastEventId: Long = 0,
    ): Flow<BridgeEvent>

    suspend fun disconnect(dappId: String)
    suspend fun getSeqno(chain: Chain, accountId: String): Int
}