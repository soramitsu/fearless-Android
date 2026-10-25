package jp.co.soramitsu.fearless_utils.wsrpc.subscription

import jp.co.soramitsu.fearless_utils.wsrpc.SocketService.ResponseListener
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange

class RespondableSubscription(
    override val id: String,
    override val initiatorId: Int,
    val unsubscribeMethod: String,
    val callback: ResponseListener<SubscriptionChange>
) : SocketStateMachine.Subscription {

    override fun toString(): String {
        return "Subscription(id=$id, initiatorId=$initiatorId)"
    }
}
