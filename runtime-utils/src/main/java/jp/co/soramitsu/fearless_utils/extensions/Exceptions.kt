package jp.co.soramitsu.fearless_utils.extensions

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

inline fun <reified T : Exception, R> ensureExceptionType(
    creator: (Exception) -> T,
    block: () -> R
): R {
    return try {
        block()
    } catch (e: Exception) {
        if (e is T) {
            throw e
        } else {
            throw creator(e)
        }
    }
}

@OptIn(ExperimentalContracts::class)
internal fun requireOrException(condition: Boolean, lazyException: () -> Exception) {
    contract {
        returns() implies condition
    }

    if (!condition) throw lazyException()
}
