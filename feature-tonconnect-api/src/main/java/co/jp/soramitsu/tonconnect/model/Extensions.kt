package co.jp.soramitsu.tonconnect.model

import android.util.Base64

private val DIGITS = "0123456789abcdef".toCharArray()


fun base64(input: String): ByteArray? {
    return try {
        Base64.decode(input, Base64.DEFAULT)
    } catch (e: Throwable) {
        null
    }
}

fun base64(input: ByteArray): String? {
    return try {
        Base64.encodeToString(input, Base64.DEFAULT)
    } catch (e: Throwable) {
        null
    }
}

fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
    bytes.forEach { byte ->
        val b = byte.toInt() and 0xFF
        append(DIGITS[b shr 4])
        append(DIGITS[b and 0x0F])
    }
}

fun String.hex(): ByteArray {
    val len = length
    if (len % 2 != 0) {
        throw IllegalArgumentException("Invalid hex string")
    }
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

enum class TonNetwork(
    val value: Int
) {
    MAINNET(-239),
    TESTNET(-3),
}
