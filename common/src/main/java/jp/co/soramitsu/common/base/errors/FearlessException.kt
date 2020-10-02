package jp.co.soramitsu.common.base.errors

import jp.co.soramitsu.common.resources.ResourceManager

class FearlessException(
    val kind: Kind,
    message: String,
    exception: Throwable? = null
) : RuntimeException(message, exception) {

    enum class Kind {
        NETWORK,
        UNEXPECTED
    }

    companion object {

        fun networkError(resourceManager: ResourceManager, throwable: Throwable): FearlessException {
            return FearlessException(Kind.NETWORK, "", throwable)
        }

        fun unexpectedError(exception: Throwable): FearlessException {
            return FearlessException(Kind.UNEXPECTED, exception.message ?: "", exception)
        }
    }
}