package jp.co.soramitsu.common.data.network.rpc

import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange

// changes are in format [[storage key, value], [..], ..]
class SubscribeStorageResult(val block: String, val changes: List<List<String?>>) {
    fun getSingleChange() = changes.first()[1]
}

@Suppress("UNCHECKED_CAST")
fun SubscriptionChange.subscribeStorageResult(): SubscribeStorageResult {
    val result = params.result as? Map<*, *> ?: notValidResult(params.result)

    val block = result["block"] as? String ?: notValidResult(result)
    val changes = result["changes"] as? List<List<String>> ?: notValidResult(result)

    return SubscribeStorageResult(block, changes)
}

private fun notValidResult(result: Any?): Nothing {
    throw IllegalArgumentException("$result is not a valid result")
}