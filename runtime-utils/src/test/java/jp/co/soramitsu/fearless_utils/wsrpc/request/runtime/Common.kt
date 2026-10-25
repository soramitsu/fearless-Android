package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime

import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange

internal fun createFakeChange(
    result: Any,
    subscriptionId: String = "test",
): SubscriptionChange {
    return SubscriptionChange(
        jsonrpc = "test",
        method = "test",
        params = SubscriptionChange.Params(
            result = result,
            subscription = subscriptionId
        )
    )
}