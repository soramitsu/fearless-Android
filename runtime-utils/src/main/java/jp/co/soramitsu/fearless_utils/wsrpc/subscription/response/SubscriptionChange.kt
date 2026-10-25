package jp.co.soramitsu.fearless_utils.wsrpc.subscription.response

class SubscriptionChange(
    val jsonrpc: String,
    val method: String,
    val params: Params
) {

    class Params(val result: Any, val subscription: String)

    val subscriptionId: String
        get() = params.subscription

    override fun toString() = "SubscriptionChange(${params.subscription})"
}

internal fun notValidResult(result: Any?, ofWhat: String): Nothing {
    throw IllegalArgumentException("$result is not a valid $ofWhat result")
}
