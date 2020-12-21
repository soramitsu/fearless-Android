package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class NextAccountIndexRequest(accountAddress: String) : RuntimeRequest(
    method = "system_accountNextIndex",
    params = listOf(
        accountAddress
    )
)