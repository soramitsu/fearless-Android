package jp.co.soramitsu.fearless_utils.extensions

import org.bouncycastle.util.encoders.Hex

private const val HEX_PREFIX = "0x"

fun ByteArray.toHexString(withPrefix: Boolean = false): String {
    val encoded = Hex.toHexString(this)

    return if (withPrefix) return HEX_PREFIX + encoded else encoded
}

fun String.fromHex(): ByteArray = Hex.decode(removePrefix(HEX_PREFIX))

fun String.requirePrefix(prefix: String) = if (startsWith(prefix)) this else prefix + this

fun String.requireHexPrefix() = requirePrefix(HEX_PREFIX)

fun Byte.toHex(withPrefix: Boolean = false): String {
    return byteArrayOf(this).toHexString(withPrefix)
}
