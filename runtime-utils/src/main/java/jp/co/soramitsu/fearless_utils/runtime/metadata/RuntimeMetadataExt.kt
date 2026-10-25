package jp.co.soramitsu.fearless_utils.runtime.metadata

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.xxHash128
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.errors.EncodeDecodeException
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Event
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType
import java.io.ByteArrayOutputStream

/**
 * @throws NoSuchElementException if module was not found
 */
fun RuntimeMetadata.module(index: Int): Module =
    modules.values.first { it.index == index.toBigInteger() }

fun RuntimeMetadata.moduleOrNull(index: Int): Module? = nullOnException { module(index) }

/**
 * @throws NoSuchElementException if module was not found
 */
fun RuntimeMetadata.module(name: String) = moduleOrNull(name) ?: throw NoSuchElementException()

fun RuntimeMetadata.moduleOrNull(name: String): Module? = modules[name]

/**
 * @throws NoSuchElementException if storage entry was not found
 */
fun Module.storage(name: String): StorageEntry =
    storageOrNull(name) ?: throw NoSuchElementException()

fun Module.storageOrNull(name: String): StorageEntry? = storage?.get(name)

/**
 * @throws NoSuchElementException if call was not found
 */
fun Module.call(index: Int): MetadataFunction = requireElementWithIndex(calls, index) { it.index.second }

fun Module.callOrNull(index: Int): MetadataFunction? = nullOnException { call(index) }

/**
 * @throws NoSuchElementException if call was not found
 */
fun Module.call(name: String): MetadataFunction = callOrNull(name) ?: throw NoSuchElementException()

fun Module.callOrNull(name: String): MetadataFunction? = calls?.get(name)

/**
 * @throws NoSuchElementException if event was not found
 */
fun Module.event(index: Int): Event = requireElementWithIndex(events, index) { it.index.second }

fun Module.eventOrNull(index: Int): Event? = nullOnException { event(index) }

/**
 * @throws NoSuchElementException if event was not found
 */
fun Module.event(name: String): Event = eventOrNull(name) ?: throw NoSuchElementException()

fun Module.eventOrNull(name: String): Event? = events?.get(name)

/**
 * Constructs a key for storage with no arguments.
 * This either fill be a full key for [StorageEntryType.Plain] entries,
 * or a prefix key for [StorageEntryType.Map] and [StorageEntryType.DoubleMap] entries
 *
 */
fun StorageEntry.storageKey(): String {
    return (moduleHash() + serviceHash()).toHexString(withPrefix = true)
}

fun StorageEntry.storageKeyOrNull() = nullOnException { storageKey() }

/**
 * Dimension of [StorageEntryType] is an number of arguments of which the key is formed
 */
fun StorageEntryType.dimension() = when (this) {
    is StorageEntryType.Plain -> 0
    is StorageEntryType.NMap -> keys.size
}

/**
 * Constructs a key for storage with supplied arguments.
 *
 * If [StorageEntryType.dimension] is equal to the number of arguments, then result will be the full storage key
 * If [StorageEntryType.dimension] is greater then the number of arguments, then result will be the prefix key
 *
 * @throws IllegalArgumentException if [StorageEntryType.dimension] is less than the number of arguments
 * @throws IllegalStateException if some of types used for encoding cannot be resolved
 * @throws EncodeDecodeException if error happened during encoding
 */
fun StorageEntry.storageKey(runtime: RuntimeSnapshot, vararg keys: Any?): String {
    // keys size can be less then dimension to retrieve by prefix
    if (keys.size > type.dimension()) wrongEntryType()

    val keysWithHashers = when (type) {
        is StorageEntryType.Plain -> emptyList()
        is StorageEntryType.NMap -> type.keys.zip(type.hashers)
    }

    val keyOutputStream = ByteArrayOutputStream()

    keyOutputStream.write(moduleHash())
    keyOutputStream.write(serviceHash())

    keys.forEachIndexed { index, key ->
        val (keyType, keyHasher) = keysWithHashers[index]

        val keyEncoded = keyType?.bytes(runtime, key) ?: typeNotResolved(name)

        keyOutputStream.write(keyHasher.hashingFunction(keyEncoded))
    }

    return keyOutputStream.toByteArray().toHexString(withPrefix = true)
}

fun StorageEntry.storageKeyOrNull(runtime: RuntimeSnapshot, vararg keys: Any?): String? {
    return nullOnException { storageKey(runtime, keys) }
}

fun Module.fullNameOf(suffix: String): String {
    return "$name.$suffix"
}

fun Module.fullNameOf(withName: WithName): String {
    return "$name.${withName.name}"
}

private fun typeNotResolved(entryName: String): Nothing =
    throw IllegalStateException("Cannot resolve key or value type for storage entry `$entryName`")

private fun wrongEntryType(): Nothing =
    throw IllegalArgumentException("Storage entry has different type than requested for storage key")

private fun StorageEntry.moduleHash() = moduleName.toByteArray().xxHash128()

private fun StorageEntry.serviceHash() = name.toByteArray().xxHash128()

private inline fun <T> nullOnException(block: () -> T): T? {
    return runCatching(block).getOrNull()
}

// V14 call and event indices are enum variant indices and keep gaps where calls were removed
// (Polkadot Balances has no call 1 or 7), so look elements up by their index rather than by position.
private inline fun <V> requireElementWithIndex(map: Map<String, V>?, index: Int, elementIndex: (V) -> Int): V {
    if (map == null) throw NoSuchElementException()

    return map.values.firstOrNull { elementIndex(it) == index } ?: throw NoSuchElementException()
}
