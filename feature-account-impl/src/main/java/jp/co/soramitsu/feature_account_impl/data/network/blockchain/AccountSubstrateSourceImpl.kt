package jp.co.soramitsu.feature_account_impl.data.network.blockchain

import io.reactivex.Single
import jp.co.soramitsu.common.data.network.rpc.SocketSingleRequestExecutor
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.string
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.system.NodeNetworkTypeRequest

class AccountSubstrateSourceImpl(
    private val socketRequestExecutor: SocketSingleRequestExecutor
) : AccountSubstrateSource {

    override fun getNodeNetworkType(nodeHost: String): Single<String> {
        val request = NodeNetworkTypeRequest()
        return socketRequestExecutor.executeRequest(request, nodeHost, string().nonNull())
    }
}