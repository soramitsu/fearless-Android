package jp.co.soramitsu.common.data.network.runtime.binding

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
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

inline fun <reified R> Struct.Instance.getTyped(key: String) = get<R>(key) ?: incompatible()

fun Struct.Instance.getList(key: String) = get<List<*>>(key) ?: incompatible()

inline fun <T> bindOrNull(binder: () -> T): T? = runCatching(binder).getOrNull()

fun RuntimeMetadata.storageReturnType(moduleName: String, storageName: String): Type<*> {
    return module(moduleName).storage(storageName).type.value ?: incompatible()
}

fun <T> Type<T>.fromHexOrIncompatible(scale: String, runtime: RuntimeSnapshot) = fromHexOrNull(runtime, scale) ?: incompatible()
