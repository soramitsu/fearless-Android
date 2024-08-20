package jp.co.soramitsu.common.data.network

import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.shared_utils.wsrpc.logging.Logger

const val TAG = "AndroidLogger"

class AndroidLogger : Logger {
    override fun log(message: String?) {
        if (BuildConfig.DEBUG) {
//            Log.d(TAG, message.toString())
        }
    }

    override fun log(throwable: Throwable?) {
        if (BuildConfig.DEBUG) {
//            throwable?.printStackTrace()
        }
    }
}
