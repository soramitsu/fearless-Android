package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime

object UnsubscribeMethodResolver {

    private const val SUBSCRIBE_PREFIX = "subscribe"
    private const val UNSUBSCRIBE_PREFIX = "unsubscribe"

    fun resolve(subscribeMethod: String): String {
        val (group, call) = subscribeMethod.split("_")

        if (call.startsWith(SUBSCRIBE_PREFIX).not()) {
            throw IllegalArgumentException("$subscribeMethod is not subscribe method")
        }

        val unsubscribeCall = call.replace(SUBSCRIBE_PREFIX, UNSUBSCRIBE_PREFIX)

        return "${group}_$unsubscribeCall"
    }
}
