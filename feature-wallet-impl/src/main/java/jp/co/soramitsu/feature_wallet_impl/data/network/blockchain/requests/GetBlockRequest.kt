package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class GetBlockRequest(blockHash: String) : RuntimeRequest(
    method = "chain_getBlock",
    params = listOf(
        blockHash
    )
)