package jp.co.soramitsu.common.base.errors

import jp.co.soramitsu.common.resources.ResourceManager

class FearlessException(
    val kind: Kind,
    message: String?,
    exception: Throwable? = null
) : RuntimeException(message, exception) {

    enum class Kind {
        NETWORK,
        UNEXPECTED
    }

    companion object {

        fun networkError(resourceManager: ResourceManager, throwable: Throwable): FearlessException {
            // TODO: add common error text to resources
            return FearlessException(Kind.NETWORK, "", throwable)
        }

        fun unexpectedError(exception: Throwable): FearlessException {
            // TODO: add common error text to resources
            return FearlessException(Kind.UNEXPECTED, exception.message ?: "", exception)
        }
    }
}
