package jp.co.soramitsu.xcm

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.FixedArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Option
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.DynamicByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.NumberType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.skipAliases
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import java.math.BigInteger

/**
 * Converts the deliberately JSON-like XCM call description into the concrete instance classes
 * required by fearless-utils' SCALE encoder. Keeping this conversion metadata-driven makes the
 * reviewed call shape readable while still failing closed if a live runtime changes that shape.
 */
internal fun XcmExtrinsicCall.toRuntimeArguments(function: MetadataFunction): Map<String, Any?> {
    val logicalArguments = arguments.requireStringKeys()
    val consumedKeys = mutableSetOf<String>()
    val runtimeArguments = function.arguments.associateTo(LinkedHashMap()) { argument ->
        val lookup = logicalArguments.lookupRuntimeName(argument.name)
        require(lookup != null) { "XCM runtime argument ${argument.name} is missing" }
        consumedKeys += lookup.key
        val type = requireNotNull(argument.type) { "XCM runtime argument ${argument.name} is unresolved" }
        argument.name to type.toXcmRuntimeValue(lookup.value)
    }
    require(consumedKeys.size == logicalArguments.size) { "XCM call contains an unknown runtime argument" }
    return runtimeArguments
}

internal fun Type<*>.toXcmRuntimeValue(logical: Any?): Any? {
    val type = skipAliases() ?: error("XCM runtime type is unresolved")
    if (type.isValidInstance(logical)) return logical

    val converted = when (type) {
        is Option -> if (logical == null) null else type.typeReference.requireValue().toXcmRuntimeValue(logical)
        is NumberType -> logical.toRuntimeInteger()
        is FixedByteArray -> logical.toRuntimeBytes().also {
            require(it.size == type.length) { "XCM runtime byte width changed" }
        }
        is DynamicByteArray -> logical.toRuntimeBytes()
        is DictEnum -> type.toRuntimeEnum(logical)
        is Struct -> type.toRuntimeStruct(logical)
        is Tuple -> type.toRuntimeTuple(logical)
        is FixedArray -> type.toRuntimeFixedArray(logical)
        is Vec -> type.toRuntimeVector(logical)
        else -> error("XCM runtime value does not match ${type.name}")
    }

    require(type.isValidInstance(converted)) { "XCM runtime value does not match ${type.name}" }
    return converted
}

private fun DictEnum.toRuntimeEnum(logical: Any?): DictEnum.Entry<Any?> {
    val (variant, value) = when (logical) {
        is String -> logical to null
        is Map<*, *> -> {
            val values = logical.requireStringKeys()
            require(values.size == 1) { "XCM runtime enum must contain exactly one variant" }
            values.entries.single().toPair()
        }
        else -> error("XCM runtime enum ${name} has an invalid value")
    }
    val childType = this[variant] ?: Null
    return DictEnum.Entry(variant, childType.toXcmRuntimeValue(value))
}

private fun Struct.toRuntimeStruct(logical: Any?): Struct.Instance {
    if (logical !is Map<*, *>) {
        require(mapping.size == 1) { "XCM runtime struct $name requires named fields" }
        val (runtimeName, reference) = mapping.entries.single()
        return Struct.Instance(
            mapOf(runtimeName to reference.requireValue().toXcmRuntimeValue(logical))
        )
    }
    val logicalFields = logical.requireStringKeys()
    val consumedKeys = mutableSetOf<String>()
    val runtimeFields = mapping.mapValues { (runtimeName, reference) ->
        val fieldType = reference.requireValue()
        val lookup = logicalFields.lookupRuntimeName(runtimeName)
        if (lookup == null) {
            require(fieldType.skipAliases() is Option) { "XCM runtime field $runtimeName is missing" }
            null
        } else {
            consumedKeys += lookup.key
            fieldType.toXcmRuntimeValue(lookup.value)
        }
    }
    require(consumedKeys.size == logicalFields.size) { "XCM value contains an unknown runtime field" }
    return Struct.Instance(runtimeFields)
}

private fun Tuple.toRuntimeTuple(logical: Any?): List<Any?> {
    if (typeReferences.size == 1) {
        val childType = typeReferences.single().requireValue()
        runCatching { childType.toXcmRuntimeValue(logical) }
            .getOrNull()
            ?.let { return listOf(it) }
    }
    val values = logical.asRuntimeSequence(typeReferences.size)
    return typeReferences.mapIndexed { index, reference ->
        reference.requireValue().toXcmRuntimeValue(values[index])
    }
}

private fun FixedArray.toRuntimeFixedArray(logical: Any?): List<Any?> {
    val values = when (logical) {
        is ByteArray -> logical.map { it.toUByte().toLong().toBigInteger() }
        is String -> logical.toRuntimeBytes().map { it.toUByte().toLong().toBigInteger() }
        else -> logical.asRuntimeSequence(length)
    }
    require(values.size == length) { "XCM runtime array width changed" }
    val childType = typeReference.requireValue()
    return values.map(childType::toXcmRuntimeValue)
}

private fun Vec.toRuntimeVector(logical: Any?): List<Any?> {
    val values = logical as? List<*> ?: error("XCM runtime vector requires a list")
    val childType = typeReference.requireValue()
    return values.map(childType::toXcmRuntimeValue)
}

private fun Any?.asRuntimeSequence(expectedSize: Int): List<*> {
    val values = when {
        this is List<*> -> this
        this == null && expectedSize == 0 -> emptyList<Any?>()
        expectedSize == 1 -> listOf(this)
        else -> null
    }
    require(values?.size == expectedSize) { "XCM runtime tuple width changed" }
    return values
}

private fun Any?.toRuntimeInteger(): BigInteger = when (this) {
    is BigInteger -> this
    is Byte -> toLong().toBigInteger()
    is Short -> toLong().toBigInteger()
    is Int -> toLong().toBigInteger()
    is Long -> toBigInteger()
    else -> error("XCM runtime number is invalid")
}

private fun Any?.toRuntimeBytes(): ByteArray = when (this) {
    is ByteArray -> this
    is String -> if (startsWith("0x")) fromHex() else runCatching { toAccountId() }
        .getOrElse { error("XCM runtime account or hex bytes are invalid") }
    else -> error("XCM runtime bytes are invalid")
}

private data class RuntimeValueLookup(val key: String, val value: Any?)

private fun Map<String, Any?>.lookupRuntimeName(runtimeName: String): RuntimeValueLookup? {
    val alternate = runtimeName.alternateRuntimeSpelling()
    val matches = listOf(runtimeName, alternate).distinct().filter(::containsKey)
    require(matches.size <= 1) { "XCM runtime field $runtimeName is ambiguous" }
    return matches.singleOrNull()?.let { RuntimeValueLookup(it, get(it)) }
}

private fun String.alternateRuntimeSpelling(): String = if ('_' in this) {
    split('_').let { words ->
        words.first() + words.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }
} else {
    buildString(length + 4) {
        this@alternateRuntimeSpelling.forEachIndexed { index, character ->
            if (character.isUpperCase()) {
                if (index != 0) append('_')
                append(character.lowercaseChar())
            } else {
                append(character)
            }
        }
    }
}

private fun Map<*, *>.requireStringKeys(): Map<String, Any?> {
    require(keys.all { it is String }) { "XCM runtime object keys must be strings" }
    @Suppress("UNCHECKED_CAST")
    return this as Map<String, Any?>
}
