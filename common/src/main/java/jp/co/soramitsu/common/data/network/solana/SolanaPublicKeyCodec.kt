package jp.co.soramitsu.common.data.network.solana

import java.math.BigInteger

/** Canonical Base58 encoding for 32-byte Solana addresses, including program-derived addresses. */
object SolanaPublicKeyCodec {
    /** Decodes exactly 32 bytes; a plausible Base58 character count alone is insufficient. */
    fun decode(address: String): ByteArray {
        return decodeFixedSize(address, 32)
    }

    internal fun decodeFixedSize(address: String, byteCount: Int): ByteArray {
        require(byteCount in 1..64 && address.length in byteCount..88)
        var number = BigInteger.ZERO
        address.forEach { character ->
            val digit = ALPHABET.indexOf(character)
            require(digit >= 0)
            number = number.multiply(RADIX).add(BigInteger.valueOf(digit.toLong()))
        }
        val body = if (number == BigInteger.ZERO) ByteArray(0) else number.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        val decoded = ByteArray(address.takeWhile { it == '1' }.length) + body
        require(decoded.size == byteCount)
        return decoded
    }

    /** Encodes an exact public address without conflating wallet keys and PDA curve membership. */
    fun encode(bytes: ByteArray): String {
        require(bytes.size == 32)
        var number = BigInteger(1, bytes)
        val result = StringBuilder()
        while (number > BigInteger.ZERO) {
            val pair = number.divideAndRemainder(RADIX)
            result.append(ALPHABET[pair[1].toInt()])
            number = pair[0]
        }
        repeat(bytes.takeWhile { it == 0.toByte() }.size) { result.append('1') }
        return result.reverse().toString()
    }

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val RADIX = BigInteger.valueOf(58)
}
