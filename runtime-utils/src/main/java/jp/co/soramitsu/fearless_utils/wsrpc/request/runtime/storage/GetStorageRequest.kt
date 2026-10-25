package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

open class GetStorageRequest(keys: List<String>) : RuntimeRequest(
    method = "state_getStorage",
    keys
)
