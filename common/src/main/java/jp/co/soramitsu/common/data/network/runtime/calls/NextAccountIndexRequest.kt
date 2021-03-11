package jp.co.soramitsu.common.data.network.runtime.calls

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class NextAccountIndexRequest(accountAddress: String) : RuntimeRequest(
    method = "system_accountNextIndex",
    params = listOf(
        accountAddress
    )
)