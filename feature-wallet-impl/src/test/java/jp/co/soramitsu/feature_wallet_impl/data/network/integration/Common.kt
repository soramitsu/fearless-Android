package jp.co.soramitsu.feature_wallet_impl.data.network.integration

import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger

class StdoutLogger : Logger {
    override fun log(message: String?) {
        println(message)
    }

    override fun log(throwable: Throwable?) {
        throwable?.printStackTrace()
    }
}