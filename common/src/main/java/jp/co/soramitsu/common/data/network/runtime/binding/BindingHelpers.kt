package jp.co.soramitsu.common.data.network.runtime.binding

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

annotation class UseCaseBinding

annotation class HelperBinding

fun incompatible(): Nothing = throw IllegalStateException("Binding is incompatible")

@OptIn(ExperimentalContracts::class)
inline fun <reified T> requireType(dynamicInstance: Any?): T {

    contract {
        returns() implies (dynamicInstance is T)
    }

    return dynamicInstance as? T ?: incompatible()
}

inline fun <reified T> Any?.cast(): T {
    return this as? T ?: incompatible()
}

inline fun <reified R> Struct.Instance.getOfType(key: String) = get<R>(key) ?: incompatible()

inline fun <T> bindOrNull(binder: () -> T): T? = runCatching(binder).getOrNull()