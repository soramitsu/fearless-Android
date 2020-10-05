package jp.co.soramitsu.feature_account_impl.data.network.blockchain

import io.reactivex.Single

interface AccountSubstrateSource {

    fun getNodeNetworkType(nodeHost: String): Single<String>
}