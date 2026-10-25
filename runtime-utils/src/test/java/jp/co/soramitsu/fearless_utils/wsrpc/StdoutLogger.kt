package jp.co.soramitsu.fearless_utils.wsrpc

import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger

object StdoutLogger : Logger {
    override fun log(message: String?) {
        println(message)
    }

    override fun log(throwable: Throwable?) {
        println(throwable)
    }
}