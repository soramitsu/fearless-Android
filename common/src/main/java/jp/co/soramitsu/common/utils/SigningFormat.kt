package jp.co.soramitsu.common.utils

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.DateFormat
import java.util.Date

/**
 * An amount in a token's smallest units, shown with every decimal it has: what a user approves
 * for signing is never rounded or abbreviated.
 */
fun BigInteger.formatExactTokenAmount(decimals: Int, symbol: String): String =
    BigDecimal(this, decimals).stripTrailingZeros().formatCryptoFull() + " " + symbol.uppercase()

private const val MILLIS_PER_SECOND = 1_000L

/** A unix time in seconds, such as a signature deadline, as a local date and time. */
fun formatSigningTime(seconds: BigInteger): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(
        Date(BigDecimal(seconds.multiply(BigInteger.valueOf(MILLIS_PER_SECOND))).longValueExact())
    )

/**
 * Bytes that are printable UTF-8 text, such as a comment or a remark, as that text. Null for
 * anything else, including text with control or invisible formatting characters (such as bidi
 * overrides) that could make it read differently from what it is.
 */
fun readableUtf8Text(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return null
    val text = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (ignored: CharacterCodingException) {
        return null
    }
    return text.takeIf(::isDisplayableText)
}

/** Whether [text] has no control or invisible formatting characters apart from line breaks and tabs. */
fun isDisplayableText(text: String): Boolean = text.codePoints().allMatch { codePoint ->
    codePoint == '\n'.code || codePoint == '\t'.code ||
        !Character.isISOControl(codePoint) && Character.getType(codePoint) != Character.FORMAT.toInt()
}
