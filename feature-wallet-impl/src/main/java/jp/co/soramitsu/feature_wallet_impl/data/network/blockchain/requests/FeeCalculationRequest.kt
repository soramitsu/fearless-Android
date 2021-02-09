package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class FeeCalculationRequest(extrinsicInHex: String) : RuntimeRequest(
    method = "payment_queryInfo",
    params = listOf(extrinsicInHex)
)