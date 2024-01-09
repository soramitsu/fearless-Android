package jp.co.soramitsu.wallet.impl.data.network.integration

import jp.co.soramitsu.shared_utils.wsrpc.logging.Logger

class StdoutLogger : Logger {
    override fun log(message: String?) {
        println(message)
    }

    override fun log(throwable: Throwable?) {
        throwable?.printStackTrace()
    }
}

class NoOpLogger: Logger {
    override fun log(message: String?) {
        // pass
    }

    override fun log(throwable: Throwable?) {
        // pass
    }
}
