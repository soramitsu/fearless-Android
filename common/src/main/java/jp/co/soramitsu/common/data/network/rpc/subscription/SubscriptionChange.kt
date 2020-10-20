package jp.co.soramitsu.common.data.network.rpc.subscription

class SubscriptionChange(
    val jsonrpc: String,
    val method: String,
    val params: Params
) {

    class Params(val result: Result, val subscription: String) {

        class Result(val block: String, val changes: Any?)
    }
}