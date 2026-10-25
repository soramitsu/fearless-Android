package jp.co.soramitsu.common.utils

import jp.co.soramitsu.common.model.UniversalWalletRegistry

object IrohaAddressCodec {
    const val TAIRA_DISCRIMINANT = 369
    const val NEXUS_DISCRIMINANT = 753
    const val DEV_DISCRIMINANT = 0

    private const val I105_DISCRIMINANT_MAX = 0x3fff
    private const val I105_SENTINEL_SORA = "sora"
    private const val I105_SENTINEL_TEST = "test"
    private const val I105_SENTINEL_DEV = "dev"
    private const val I105_SENTINEL_FALLBACK_PREFIX = "n"
    private const val I105_CHECKSUM_LEN = 6
    private const val I105_BASE = 105
    private const val BECH32M_CONST = 0x2bc830a3
    private const val I105_HRP = "snx"
    private const val CONTROLLER_SINGLE_KEY_TAG = 0x00
    private const val CURVE_ED25519 = 0x01
    private const val ED25519_PUBLIC_KEY_LENGTH = 32
    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val IROHA_POEM_KANA_HALFWIDTH = listOf(
        '\uff72',
        '\uff9b',
        '\uff8a',
        '\uff86',
        '\uff8e',
        '\uff8d',
        '\uff84',
        '\uff81',
        '\uff98',
        '\uff87',
        '\uff99',
        '\uff66',
        '\uff9c',
        '\uff76',
        '\uff96',
        '\uff80',
        '\uff9a',
        '\uff7f',
        '\uff82',
        '\uff88',
        '\uff85',
        '\uff97',
        '\uff91',
        '\uff73',
        '\u30f0',
        '\uff89',
        '\uff75',
        '\uff78',
        '\uff94',
        '\uff8f',
        '\uff79',
        '\uff8c',
        '\uff7a',
        '\uff74',
        '\uff83',
        '\uff71',
        '\uff7b',
        '\uff77',
        '\uff95',
        '\uff92',
        '\uff90',
        '\uff7c',
        '\u30f1',
        '\uff8b',
        '\uff93',
        '\uff7e',
        '\uff7d'
    )
    private val I105_ALPHABET = BASE58_ALPHABET.toList() + IROHA_POEM_KANA_HALFWIDTH
    private val I105_DIGIT_TABLE = I105_ALPHABET.withIndex().associate { it.value to it.index }
    private val BECH32_GENERATORS = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    enum class NetworkKind {
        TAIRA,
        NEXUS,
        DEV,
        CUSTOM
    }

    enum class ErrorCode {
        ERR_INVALID_LENGTH,
        ERR_CHECKSUM_MISMATCH,
        ERR_INVALID_HEX_ADDRESS,
        ERR_MISSING_I105_SENTINEL,
        ERR_I105_TOO_SHORT,
        ERR_INVALID_I105_BASE,
        ERR_INVALID_I105_CHAR,
        ERR_INVALID_I105_DIGIT,
        ERR_UNSUPPORTED_ADDRESS_FORMAT,
        ERR_UNEXPECTED_NETWORK_PREFIX,
        ERR_INVALID_I105_PREFIX,
        ERR_INVALID_HEADER_VERSION,
        ERR_INVALID_NORM_VERSION,
        ERR_UNKNOWN_ADDRESS_CLASS,
        ERR_UNEXPECTED_EXTENSION_FLAG,
        ERR_UNKNOWN_CONTROLLER_TAG,
        ERR_UNKNOWN_CURVE,
        ERR_UNEXPECTED_TRAILING_BYTES
    }

    data class Details(
        val chainDiscriminant: Int,
        val network: NetworkKind,
        val canonicalHex: String,
        val publicKeyHex: String,
        val i105: String
    )

    class IrohaAddressException(val code: ErrorCode, val detail: Any? = null) : IllegalArgumentException(code.name)

    fun canonicalHex(publicKeyHex: String): String = publicKeyToCanonicalBytes(publicKeyHex).toHex()

    fun encode(publicKeyHex: String, chainDiscriminant: Int): String =
        encodeCanonicalHex(canonicalHex(publicKeyHex), chainDiscriminant)

    fun encodeCanonicalHex(canonicalHex: String, chainDiscriminant: Int): String =
        encodeI105Literal(resolveDiscriminant(chainDiscriminant), normalizeHexBytes(canonicalHex))

    fun parse(address: String, expectedDiscriminant: Int? = null): Details {
        if (address.isEmpty() || address != address.trim()) throw IrohaAddressException(ErrorCode.ERR_UNSUPPORTED_ADDRESS_FORMAT)
        if (address.startsWith("0x") || address.startsWith("0X")) throw IrohaAddressException(ErrorCode.ERR_UNSUPPORTED_ADDRESS_FORMAT)

        val decoded = decodeI105Literal(address)
        val expected = expectedDiscriminant?.let(::resolveDiscriminant)

        if (expected != null && decoded.chainDiscriminant != expected) {
            throw IrohaAddressException(
                ErrorCode.ERR_UNEXPECTED_NETWORK_PREFIX,
                mapOf("expected" to expected, "found" to decoded.chainDiscriminant)
            )
        }

        val publicKeyHex = decodeCanonicalSingleEd25519(decoded.canonicalBytes)
        val i105 = encodeI105Literal(decoded.chainDiscriminant, decoded.canonicalBytes)

        if (i105 != address) throw IrohaAddressException(ErrorCode.ERR_UNSUPPORTED_ADDRESS_FORMAT)

        return Details(
            chainDiscriminant = decoded.chainDiscriminant,
            network = networkFromDiscriminant(decoded.chainDiscriminant),
            canonicalHex = decoded.canonicalBytes.toHex(),
            publicKeyHex = publicKeyHex,
            i105 = i105
        )
    }

    fun isValid(address: String, expectedDiscriminant: Int? = null): Boolean = runCatching {
        parse(address, expectedDiscriminant)
    }.isSuccess

    fun networkKind(address: String): NetworkKind? = runCatching { parse(address).network }.getOrNull()

    private data class DecodedLiteral(val chainDiscriminant: Int, val canonicalBytes: ByteArray)

    private fun resolveDiscriminant(chainDiscriminant: Int): Int {
        if (chainDiscriminant !in 0..I105_DISCRIMINANT_MAX) {
            throw IrohaAddressException(ErrorCode.ERR_INVALID_I105_PREFIX, chainDiscriminant)
        }

        return chainDiscriminant
    }

    private fun networkFromDiscriminant(discriminant: Int): NetworkKind {
        return when (discriminant) {
            UniversalWalletRegistry.taira.chainDiscriminant -> NetworkKind.TAIRA
            UniversalWalletRegistry.nexus.chainDiscriminant -> NetworkKind.NEXUS
            DEV_DISCRIMINANT -> NetworkKind.DEV
            else -> NetworkKind.CUSTOM
        }
    }

    private fun sentinelForDiscriminant(discriminant: Int): String {
        return when (discriminant) {
            UniversalWalletRegistry.nexus.chainDiscriminant -> I105_SENTINEL_SORA
            UniversalWalletRegistry.taira.chainDiscriminant -> I105_SENTINEL_TEST
            DEV_DISCRIMINANT -> I105_SENTINEL_DEV
            else -> "$I105_SENTINEL_FALLBACK_PREFIX$discriminant"
        }
    }

    private fun discriminantFromSentinel(input: String): Int? {
        if (input.startsWith(I105_SENTINEL_SORA)) return UniversalWalletRegistry.nexus.chainDiscriminant
        if (input.startsWith(I105_SENTINEL_TEST)) return UniversalWalletRegistry.taira.chainDiscriminant
        if (input.startsWith(I105_SENTINEL_DEV)) return DEV_DISCRIMINANT
        if (!input.startsWith(I105_SENTINEL_FALLBACK_PREFIX)) return null

        val digits = input.drop(1).take(5).takeWhile { it.isDigit() }
        if (digits.isEmpty()) return null

        val discriminant = digits.toIntOrNull() ?: return null

        return discriminant.takeIf { it <= I105_DISCRIMINANT_MAX }
    }

    private fun encodeI105Literal(chainDiscriminant: Int, canonicalBytes: ByteArray): String {
        val digits = encodeBaseN(canonicalBytes, I105_BASE)
        val checksum = i105ChecksumDigits(canonicalBytes)

        return buildString {
            append(sentinelForDiscriminant(chainDiscriminant))
            (digits + checksum).forEach { append(i105DigitSymbol(it)) }
        }
    }

    private fun decodeI105Literal(input: String): DecodedLiteral {
        val discriminant = discriminantFromSentinel(input)
            ?: throw IrohaAddressException(ErrorCode.ERR_MISSING_I105_SENTINEL)
        val sentinel = sentinelForDiscriminant(discriminant)

        if (!input.startsWith(sentinel)) throw IrohaAddressException(ErrorCode.ERR_UNSUPPORTED_ADDRESS_FORMAT)

        return DecodedLiteral(discriminant, decodeI105Payload(input.drop(sentinel.length)))
    }

    private fun decodeI105Payload(payload: String): ByteArray {
        val digits = payload.map { I105_DIGIT_TABLE[it] ?: throw IrohaAddressException(ErrorCode.ERR_INVALID_I105_CHAR, it) }

        if (digits.size <= I105_CHECKSUM_LEN) throw IrohaAddressException(ErrorCode.ERR_I105_TOO_SHORT)

        val splitAt = digits.size - I105_CHECKSUM_LEN
        val canonicalBytes = decodeBaseN(digits.take(splitAt), I105_BASE)
        val expected = i105ChecksumDigits(canonicalBytes)

        if (digits.drop(splitAt) != expected) throw IrohaAddressException(ErrorCode.ERR_CHECKSUM_MISMATCH)

        return canonicalBytes
    }

    private fun i105DigitSymbol(digit: Int): Char {
        return I105_ALPHABET.getOrNull(digit)
            ?: throw IrohaAddressException(ErrorCode.ERR_INVALID_I105_DIGIT, digit)
    }

    private fun encodeBaseN(bytes: ByteArray, base: Int): List<Int> {
        if (base < 2) throw IrohaAddressException(ErrorCode.ERR_INVALID_I105_BASE)
        if (bytes.isEmpty()) return listOf(0)

        val value = bytes.map { it.toInt() and 0xff }.toMutableList()
        val leadingZeros = value.takeWhile { it == 0 }.size
        val digits = mutableListOf<Int>()
        var start = leadingZeros

        while (start < value.size) {
            var remainder = 0

            for (index in start until value.size) {
                val accumulator = (remainder shl 8) or value[index]
                value[index] = accumulator / base
                remainder = accumulator % base
            }

            digits.add(remainder)

            while (start < value.size && value[start] == 0) start += 1
        }

        repeat(leadingZeros) { digits.add(0) }
        if (digits.isEmpty()) digits.add(0)

        return digits.asReversed()
    }

    private fun decodeBaseN(digits: List<Int>, base: Int): ByteArray {
        if (base < 2) throw IrohaAddressException(ErrorCode.ERR_INVALID_I105_BASE)
        if (digits.isEmpty()) throw IrohaAddressException(ErrorCode.ERR_INVALID_LENGTH)

        val value = digits.toMutableList()
        val leadingZeros = value.takeWhile { it == 0 }.size
        val bytes = mutableListOf<Int>()
        var start = leadingZeros

        while (start < value.size) {
            var remainder = 0

            for (index in start until value.size) {
                val digit = value[index]
                if (digit >= base) throw IrohaAddressException(ErrorCode.ERR_INVALID_I105_DIGIT, digit)

                val accumulator = remainder * base + digit
                value[index] = accumulator / 256
                remainder = accumulator % 256
            }

            bytes.add(remainder)

            while (start < value.size && value[start] == 0) start += 1
        }

        repeat(leadingZeros) { bytes.add(0) }

        return bytes.asReversed().map { it.toByte() }.toByteArray()
    }

    private fun i105ChecksumDigits(canonicalBytes: ByteArray): List<Int> {
        val data = convertToBase32(canonicalBytes)
        val values = expandHrp(I105_HRP) + data + List(I105_CHECKSUM_LEN) { 0 }
        val polymod = bech32Polymod(values) xor BECH32M_CONST

        return List(I105_CHECKSUM_LEN) { index ->
            (polymod ushr (5 * (I105_CHECKSUM_LEN - 1 - index))) and 0x1f
        }
    }

    private fun convertToBase32(bytes: ByteArray): List<Int> {
        var accumulator = 0
        var bits = 0
        val result = mutableListOf<Int>()

        bytes.forEach { rawByte ->
            accumulator = ((accumulator shl 8) or (rawByte.toInt() and 0xff)) and 0xfff
            bits += 8

            while (bits >= 5) {
                bits -= 5
                result.add((accumulator ushr bits) and 0x1f)
            }
        }

        if (bits > 0) result.add((accumulator shl (5 - bits)) and 0x1f)

        return result
    }

    private fun bech32Polymod(values: List<Int>): Int {
        var checksum = 1

        values.forEach { value ->
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value

            BECH32_GENERATORS.forEachIndexed { index, generator ->
                if (((top ushr index) and 1) == 1) checksum = checksum xor generator
            }
        }

        return checksum
    }

    private fun expandHrp(hrp: String): List<Int> {
        return hrp.map { it.code ushr 5 } + listOf(0) + hrp.map { it.code and 31 }
    }

    private fun decodeCanonicalSingleEd25519(canonicalBytes: ByteArray): String {
        if (canonicalBytes.isEmpty()) throw IrohaAddressException(ErrorCode.ERR_INVALID_LENGTH)

        val header = canonicalBytes[0].toInt() and 0xff
        val version = header ushr 5
        val classBits = (header ushr 3) and 0b11
        val normVersion = (header ushr 1) and 0b11
        val extFlag = (header and 1) == 1

        if (extFlag) throw IrohaAddressException(ErrorCode.ERR_UNEXPECTED_EXTENSION_FLAG)
        if (version != 0) throw IrohaAddressException(ErrorCode.ERR_INVALID_HEADER_VERSION, version)
        if (normVersion != 1) throw IrohaAddressException(ErrorCode.ERR_INVALID_NORM_VERSION, normVersion)
        if (classBits != 0) throw IrohaAddressException(ErrorCode.ERR_UNKNOWN_ADDRESS_CLASS, classBits)
        if (canonicalBytes.size < 4) throw IrohaAddressException(ErrorCode.ERR_INVALID_LENGTH)

        val tag = canonicalBytes[1].toInt() and 0xff
        val curve = canonicalBytes[2].toInt() and 0xff
        val length = canonicalBytes[3].toInt() and 0xff

        if (tag != CONTROLLER_SINGLE_KEY_TAG) throw IrohaAddressException(ErrorCode.ERR_UNKNOWN_CONTROLLER_TAG, tag)
        if (curve != CURVE_ED25519) throw IrohaAddressException(ErrorCode.ERR_UNKNOWN_CURVE, curve)
        if (length != ED25519_PUBLIC_KEY_LENGTH) throw IrohaAddressException(ErrorCode.ERR_INVALID_LENGTH)
        if (canonicalBytes.size < 4 + length) throw IrohaAddressException(ErrorCode.ERR_INVALID_LENGTH)
        if (canonicalBytes.size != 4 + length) throw IrohaAddressException(ErrorCode.ERR_UNEXPECTED_TRAILING_BYTES)

        return canonicalBytes.copyOfRange(4, 4 + length).toHex().removePrefix("0x")
    }

    private fun publicKeyToCanonicalBytes(publicKeyHex: String): ByteArray {
        val publicKey = normalizeHexBytes(publicKeyHex, ED25519_PUBLIC_KEY_LENGTH)
        return byteArrayOf(0x02, CONTROLLER_SINGLE_KEY_TAG.toByte(), CURVE_ED25519.toByte(), publicKey.size.toByte()) + publicKey
    }

    private fun normalizeHexBytes(value: String, expectedBytes: Int? = null): ByteArray {
        val hex = value.removePrefix("0x").removePrefix("0X")

        if (hex.isEmpty() || hex.length % 2 != 0 || !Regex("^[0-9a-fA-F]+$").matches(hex)) {
            throw IrohaAddressException(ErrorCode.ERR_INVALID_HEX_ADDRESS)
        }
        if (expectedBytes != null && hex.length != expectedBytes * 2) {
            throw IrohaAddressException(ErrorCode.ERR_INVALID_LENGTH)
        }

        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHex(): String = joinToString(prefix = "0x", separator = "") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
