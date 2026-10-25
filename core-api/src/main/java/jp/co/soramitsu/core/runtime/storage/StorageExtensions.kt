package jp.co.soramitsu.core.runtime.storage

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry

fun StorageEntry.returnType(): Type<*> {
    return requireNotNull(type.value) { "Storage entry $moduleName.$name has no return type" }
}

fun incompatible(): Nothing = throw IllegalStateException("Storage binding is incompatible")

fun incompatible(message: String): Nothing = throw IllegalStateException(message)
