package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class TransferRequest(extrinsic: String) : RuntimeRequest(
    method = "author_submitExtrinsic",
    params = listOf(extrinsic)
)
