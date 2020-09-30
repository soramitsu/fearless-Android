package jp.co.soramitsu.feature_account_impl.data.network.blockchain

import io.reactivex.Single
import jp.co.soramitsu.common.data.network.rpc.RxWebSocket

class AccountSubstrateSourceImpl(
    private val rxWebSocket: RxWebSocket
) : AccountSubstrateSource {

    override fun getNodeNetworkType(nodeHost: String): Single<String> {
        TODO("Not yet implemented")
    }
}