package jp.co.soramitsu.fearless_utils.wsrpc.exception

class ConnectionClosedException : Exception() {

    override fun toString(): String = javaClass.simpleName
}
