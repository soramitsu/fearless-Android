package jp.co.soramitsu.test_shared

import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger

object StdoutLogger : Logger {
    override fun log(message: String?) {
        println(message)
    }

    override fun log(throwable: Throwable?) {
        throwable?.printStackTrace()
    }
}

object NoOpLogger : Logger {
    override fun log(message: String?) {
        // pass
    }

    override fun log(throwable: Throwable?) {
        // pass
    }
}
