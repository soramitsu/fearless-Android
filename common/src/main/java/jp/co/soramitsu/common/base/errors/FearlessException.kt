package jp.co.soramitsu.common.base.errors

import jp.co.soramitsu.common.resources.ResourceManager
import java.io.IOException

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

        fun networkError(resourceManager: ResourceManager, exception: IOException): FearlessException {
            return FearlessException(Kind.NETWORK, "", exception)
        }

        fun unexpectedError(exception: Throwable): FearlessException {
            return FearlessException(Kind.UNEXPECTED, exception.message ?: "", exception)
        }
    }
}