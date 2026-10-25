package jp.co.soramitsu.fearless_utils.runtime.definitions.types

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.extensions.ensureExceptionType
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Alias
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.errors.EncodeDecodeException
import java.io.ByteArrayOutputStream

/**
 * @throws CyclicAliasingException
 */
fun Type<*>.skipAliases(): Type<*>? {
    if (this !is Alias) return this

    return aliasedReference.skipAliasesOrNull()?.value
}

fun Type<*>?.isFullyResolved() = this?.isFullyResolved ?: false

/**
 * @throws EncodeDecodeException
 */
fun <I> Type<I>.fromByteArray(runtime: RuntimeSnapshot, byteArray: ByteArray): I {
    val reader = ScaleCodecReader(byteArray)

    return ensureUnifiedException { decode(reader, runtime) }
}

/**
 * @throws EncodeDecodeException
 */
fun <I> Type<I>.fromHex(runtime: RuntimeSnapshot, hex: String): I {
    return ensureUnifiedException { fromByteArray(runtime, hex.fromHex()) }
}

fun <I> Type<I>.fromByteArrayOrNull(runtime: RuntimeSnapshot, byteArray: ByteArray): I? {
    return runCatching { fromByteArray(runtime, byteArray) }.getOrNull()
}

fun <I> Type<I>.fromHexOrNull(runtime: RuntimeSnapshot, hex: String): I? {
    return runCatching { fromHex(runtime, hex) }.getOrNull()
}

/**
 * @throws EncodeDecodeException
 */
fun <I> Type<I>.toByteArray(runtime: RuntimeSnapshot, value: I): ByteArray {
    return ensureUnifiedException {
        useScaleWriter { encode(this, runtime, value) }
    }
}

/**
 * Type-unsafe version of [toByteArray]
 *
 * @throws EncodeDecodeException
 */
fun Type<*>.bytes(runtime: RuntimeSnapshot, value: Any?): ByteArray {
    return ensureUnifiedException {
        useScaleWriter { encodeUnsafe(this, runtime, value) }
    }
}

fun <I> Type<I>.toByteArrayOrNull(runtime: RuntimeSnapshot, value: I): ByteArray? {
    return runCatching { toByteArray(runtime, value) }.getOrNull()
}

fun Type<*>.bytesOrNull(runtime: RuntimeSnapshot, value: Any?): ByteArray? {
    return runCatching { bytes(runtime, value) }.getOrNull()
}

/**
 * @throws EncodeDecodeException
 */
fun <I> Type<I>.toHex(runtime: RuntimeSnapshot, value: I) =
    toByteArray(runtime, value).toHexString(withPrefix = true)

fun <I> Type<I>.toHexOrNull(runtime: RuntimeSnapshot, value: I) =
    toByteArrayOrNull(runtime, value)?.toHexString(withPrefix = true)

fun useScaleWriter(use: ScaleCodecWriter.() -> Unit): ByteArray {
    val stream = ByteArrayOutputStream()
    val writer = ScaleCodecWriter(stream)

    writer.use()

    return stream.toByteArray()
}

private inline fun <R> ensureUnifiedException(block: () -> R): R {
    return ensureExceptionType(::EncodeDecodeException, block)
}
