package jp.co.soramitsu.common.data.network.runtime.calls

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class GetHeaderRequest(blockHash: String? = null) : RuntimeRequest(
    method = "chain_getHeader",
    params = listOfNotNull(
        blockHash
    )
)
