package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.notValidResult

class SubscribeStorageRequest(storageKeys: List<String>) : RuntimeRequest(
    "state_subscribeStorage",
    listOf(storageKeys)
) {

    constructor(vararg storageKeys: String) : this(storageKeys.toList())
}

// changes are in format [[storage key, value], [..], ..]
class SubscribeStorageResult(val block: String, val changes: List<List<String?>>) {
    fun getSingleChange() = changes.first()[1]
}

@Suppress("UNCHECKED_CAST")
fun SubscriptionChange.storageChange(): SubscribeStorageResult {
    val result = params.result as? Map<*, *> ?: notValidResult(params.result)

    val block = result["block"] as? String ?: notValidResult(result)
    val changes = result["changes"] as? List<List<String>> ?: notValidResult(result)

    return SubscribeStorageResult(block, changes)
}

private fun notValidResult(result: Any?): Nothing = notValidResult(result, "storage")
