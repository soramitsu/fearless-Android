package jp.co.soramitsu.walletconnect.impl.protocol

import java.math.BigInteger
import java.util.Base64

/** Hex, Base64 and Base58 as the WalletConnect wire format writes them. */
internal object WcEncoding {
    private const val HEX_DIGITS = "0123456789abcdef"
    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val NIBBLE_BITS = 4
    private const val NIBBLE_MASK = 0x0f
    private const val BYTE_MASK = 0xff
    private val BASE58 = BigInteger.valueOf(BASE58_ALPHABET.length.toLong())

    fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and BYTE_MASK
            out.append(HEX_DIGITS[value ushr NIBBLE_BITS]).append(HEX_DIGITS[value and NIBBLE_MASK])
        }
        return out.toString()
    }

    /** Lower or upper case hex without a prefix; anything else is refused. */
    fun unhex(text: String): ByteArray {
        require(text.length % 2 == 0) { "Odd hex length" }
        return ByteArray(text.length / 2) { index ->
            val high = Character.digit(text[index * 2], 16)
            val low = Character.digit(text[index * 2 + 1], 16)
            require(high >= 0 && low >= 0) { "Not hex" }
            ((high shl NIBBLE_BITS) or low).toByte()
        }
    }

    /** Whether [text] is exactly [byteLength] bytes of hex. */
    fun isHex(text: String?, byteLength: Int): Boolean =
        text != null && text.length == byteLength * 2 && text.all { Character.digit(it, 16) >= 0 }

    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun unbase64(text: String): ByteArray = Base64.getDecoder().decode(text)

    fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun unbase64Url(text: String): ByteArray = Base64.getUrlDecoder().decode(text)

    /** Bitcoin's Base58 alphabet, as multibase `z` (base58btc) uses it. */
    fun base58(bytes: ByteArray): String {
        var value = BigInteger(1, bytes)
        val out = StringBuilder()
        while (value.signum() > 0) {
            val (quotient, remainder) = value.divideAndRemainder(BASE58).let { it[0] to it[1] }
            out.append(BASE58_ALPHABET[remainder.toInt()])
            value = quotient
        }
        for (byte in bytes) {
            if (byte.toInt() != 0) break
            out.append(BASE58_ALPHABET[0])
        }
        return out.reverse().toString()
    }
}
