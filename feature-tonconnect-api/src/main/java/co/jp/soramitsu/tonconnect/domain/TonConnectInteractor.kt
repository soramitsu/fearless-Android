package co.jp.soramitsu.tonconnect.domain

import co.jp.soramitsu.tonconnect.model.AppEntity
import co.jp.soramitsu.tonconnect.model.BridgeError
import co.jp.soramitsu.tonconnect.model.BridgeEvent
import co.jp.soramitsu.tonconnect.model.ConnectRequest
import co.jp.soramitsu.tonconnect.model.DappConfig
import co.jp.soramitsu.tonconnect.model.TONProof
import co.jp.soramitsu.tonconnect.model.TonConnectSignRequest
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

interface TonConnectInteractor {
    suspend fun respondDappConnectRequest(
        clientId: String?,
        request: ConnectRequest,
        signedRequest: JSONObject,
        app: AppEntity
    )
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

    suspend fun disconnect(clientId: String)

    suspend fun signMessage(chain: Chain, method: String, signRequest: TonConnectSignRequest): String
    suspend fun sendBlockchainMessage(chain: Chain, boc: String)
    suspend fun sendDappMessage(event: BridgeEvent, boc: String)
    suspend fun readManifest(url: String): AppEntity

    suspend fun getConnection(url: String): TonConnectionLocal?
    suspend fun respondDappError(event: BridgeEvent, error: BridgeError)
}