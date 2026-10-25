package jp.co.soramitsu.core.runtime.models.requests

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class RuntimeCallRequest(runtimeCallMethod: String, args: String) : RuntimeRequest(
    method = "state_call",
    params = listOf(runtimeCallMethod, args)
)
