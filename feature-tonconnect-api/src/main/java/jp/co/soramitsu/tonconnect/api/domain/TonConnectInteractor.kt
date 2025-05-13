package jp.co.soramitsu.tonconnect.api.domain

import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.BridgeEvent
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.DappConfig
import jp.co.soramitsu.tonconnect.api.model.TONProof
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

@Suppress("ComplexInterface")
interface TonConnectInteractor {
    suspend fun respondDappConnectRequest(
        clientId: String?,
        request: ConnectRequest,
        signedRequest: JSONObject,
        app: AppEntity
    )

    suspend fun getChain(): Chain

    suspend fun getDappsConfig(): List<DappConfig>

    fun getConnectedDappsFlow(source: ConnectionSource): Flow<DappConfig>
    suspend fun getConnectedDapps(source: ConnectionSource): DappConfig

    suspend fun requestProof(
        selectedWalletId: Long,
        app: AppEntity,
        proofPayload: String
    ): TONProof.Result

    fun eventsFlow(
//        connections: List<AppConnectEntity>,
        lastEventId: Long = 0,
    ): Flow<BridgeEvent>

    suspend fun disconnect(clientId: String)

    suspend fun signMessage(
        chain: Chain,
        method: String,
        signRequest: TonConnectSignRequest,
        metaId: Long
    ): Pair<String, String>

    suspend fun sendBlockchainMessage(chain: Chain, boc: String)
    suspend fun sendDappMessage(event: BridgeEvent, boc: String)
    suspend fun readManifest(url: String): AppEntity

    suspend fun getConnection(url: String, source: ConnectionSource): TonConnectionLocal?
    suspend fun respondDappError(event: BridgeEvent, error: BridgeError)
}
