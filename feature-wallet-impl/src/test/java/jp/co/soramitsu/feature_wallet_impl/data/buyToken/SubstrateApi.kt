package jp.co.soramitsu.feature_wallet_impl.data.buyToken

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.query.DecoratableQuery
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.rpc.DecoratableRPC
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.tx.DecoratableTx

interface SubstrateApi {

    val query: DecoratableQuery

    val tx: DecoratableTx

    val rpc: DecoratableRPC
}


fun SubstrateApi(
    runtime: RuntimeSnapshot,
    socketService: SocketService,
) = object : SubstrateApi {
    override val query: DecoratableQuery = DecoratableQuery(this, runtime)
    override val tx: DecoratableTx = DecoratableTx(this, runtime)
    override val rpc: DecoratableRPC = DecoratableRPC(socketService)
}
