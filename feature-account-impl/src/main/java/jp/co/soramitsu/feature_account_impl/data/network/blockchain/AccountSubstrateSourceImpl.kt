package jp.co.soramitsu.feature_account_impl.data.network.blockchain

import io.reactivex.Single
import jp.co.soramitsu.common.data.network.rpc.RxWebSocket
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.system.NodeNetworkTypeRequest

class AccountSubstrateSourceImpl(
    private val rxWebSocket: RxWebSocket
) : AccountSubstrateSource {

    override fun getNodeNetworkType(nodeHost: String): Single<String> {
        val request = NodeNetworkTypeRequest()
        return rxWebSocket.requestWithStringResponse(request, nodeHost)
    }
}