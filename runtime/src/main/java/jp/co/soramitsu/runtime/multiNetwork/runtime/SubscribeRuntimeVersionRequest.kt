package jp.co.soramitsu.runtime.multiNetwork.runtime

import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.RuntimeRequest

object SubscribeRuntimeVersionRequest : RuntimeRequest(
    method = "state_subscribeRuntimeVersion",
    params = listOf()
)
