package jp.co.soramitsu.core.runtime.models.requests

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class ExistentialDepositRequest(asset: Any) : RuntimeRequest(
    method = "tokens_queryExistentialDeposit",
    params = listOf(asset)
)
