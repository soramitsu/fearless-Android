package jp.co.soramitsu.common.base

import java.io.IOException

class BaseException(
    val kind: Kind,
    message: String,
    exception: Throwable? = null
) : RuntimeException(message, exception) {

    enum class Kind {
        BUSINESS,
        NETWORK,
        HTTP,
        UNEXPECTED
    }

    companion object {

        fun businessError(message: String): BaseException {
            return BaseException(Kind.BUSINESS, message)
        }

        fun httpError(errorCode: Int, message: String): BaseException {
            return BaseException(Kind.HTTP, message)
        }

        fun networkError(message: String, exception: IOException): BaseException {
            return BaseException(Kind.NETWORK, message, exception)
        }

        fun unexpectedError(exception: Throwable): BaseException {
            return BaseException(Kind.UNEXPECTED, exception.message ?: "", exception)
        }
    }
}