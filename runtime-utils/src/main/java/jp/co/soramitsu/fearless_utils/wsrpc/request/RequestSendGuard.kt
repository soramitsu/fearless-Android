package jp.co.soramitsu.fearless_utils.wsrpc.request

/**
 * Authorizes the exact synchronous handoff to the WebSocket transport. Implementations
 * serialize this closure with revocation, must not suspend or call SocketService themselves,
 * and must throw when the captured request is no longer permitted. No service monitor is held.
 */
fun interface RequestSendGuard {
    fun sendIfAuthorized(send: () -> Unit)
}
