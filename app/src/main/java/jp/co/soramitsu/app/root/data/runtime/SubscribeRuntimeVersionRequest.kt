package jp.co.soramitsu.app.root.data.runtime

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

object SubscribeRuntimeVersionRequest : RuntimeRequest(
    method = "chain_subscribeRuntimeVersion",
    params = listOf()
)