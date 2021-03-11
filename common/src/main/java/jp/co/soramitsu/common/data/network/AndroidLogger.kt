package jp.co.soramitsu.common.data.network

import android.util.Log
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger

const val TAG = "AndroidLogger"

class AndroidLogger : Logger {
    override fun log(message: String?) {
        Log.d(TAG, message.toString())
    }

    override fun log(throwable: Throwable?) {
        throwable?.printStackTrace()
    }
}