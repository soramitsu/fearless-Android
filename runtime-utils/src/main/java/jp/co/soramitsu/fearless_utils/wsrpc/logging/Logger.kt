package jp.co.soramitsu.fearless_utils.wsrpc.logging

interface Logger {
    fun log(message: String?)

    fun log(throwable: Throwable?)
}
