package jp.co.soramitsu.app.root.data.runtime

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange

object SubscribeRuntimeVersionRequest : RuntimeRequest(
    method = "chain_subscribeRuntimeVersion",
    params = listOf()
)

fun SubscriptionChange.asRuntimeSubscribption() {

}