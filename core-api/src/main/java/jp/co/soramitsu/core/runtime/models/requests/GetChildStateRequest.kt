package jp.co.soramitsu.core.runtime.models.requests

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class GetChildStateRequest(
    storageKey: String,
    childKey: String
) : RuntimeRequest(
    method = "state_getChildStorage",
    params = listOf(storageKey, childKey)
)
