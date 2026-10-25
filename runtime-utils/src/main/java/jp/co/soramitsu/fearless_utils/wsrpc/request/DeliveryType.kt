package jp.co.soramitsu.fearless_utils.wsrpc.request

enum class DeliveryType {

    /**
     * For idempotent requests will not produce error and try to to deliver after reconnect
     */
    AT_LEAST_ONCE,

    /**
     * For non-idempotent requests, will produce an error if fails to deliver/get response
     */
    AT_MOST_ONCE,

    /**
     * Similar to AT_LEAST_ONCE, but resend request on each reconnect regardless of success
     */
    ON_RECONNECT
}
