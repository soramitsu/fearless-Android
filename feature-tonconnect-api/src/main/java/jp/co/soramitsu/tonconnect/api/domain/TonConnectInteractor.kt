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
import jp.co.soramitsu.tonconnect.api.model.TonConnectionIdentity
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

@Suppress("ComplexInterface")
interface TonConnectInteractor {
    /**
     * Sends [signedRequest] to the dApp and, for a successful connect, stores the
     * connection under the wallet whose key [signedRequest] names, which is the
     * wallet the user picked. Returns the stored connection, or null when none was stored.
     */
    suspend fun respondDappConnectRequest(
        clientId: String?,
        request: ConnectRequest,
        signedRequest: JSONObject,
        app: AppEntity
    ): TonDappConnection?

    suspend fun getChain(): Chain

    suspend fun getDappsConfig(): List<DappConfig>

    fun getConnectedDappsFlow(source: ConnectionSource): Flow<DappConfig>
    suspend fun getConnectedDapps(source: ConnectionSource): DappConfig

    suspend fun requestProof(
        selectedWalletId: Long,
        app: AppEntity,
        proofPayload: String
    ): TONProof.Result

    /**
     * Bridge requests for the selected wallet's QR connections. Each request is
     * delivered at most once per connection, also across reconnects and restarts.
     */
    fun eventsFlow(): Flow<BridgeEvent>

    suspend fun disconnect(identity: TonConnectionIdentity)

    /**
     * Parses the `sendTransaction` params of a request and checks them against the
     * wallet [metaId] the connection is bound to. Throws [BridgeError.Exception]
     * with [BridgeError.BAD_REQUEST] for a request that must not be shown or signed.
     */
    suspend fun prepareSignRequest(params: List<String>, metaId: Long): TonConnectSignRequest

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

    /** The stored connection with exactly this identity, if it is still usable. */
    suspend fun getConnection(identity: TonConnectionIdentity): TonConnectionLocal?

    suspend fun respondDappError(event: BridgeEvent, error: BridgeError)
}
