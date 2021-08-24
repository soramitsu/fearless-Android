package jp.co.soramitsu.common.data.network.runtime.calls

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

object GetFinalizedHeadRequest : RuntimeRequest(
    method = "chain_getFinalizedHead",
    params = emptyList()
)
