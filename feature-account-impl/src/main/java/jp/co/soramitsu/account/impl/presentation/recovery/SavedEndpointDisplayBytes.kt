package jp.co.soramitsu.account.impl.presentation.recovery

/**
 * Quotes one bounded reader slice without replacement decoding. Malformed or
 * split UTF-8 bytes use distinct \\xNN escapes; valid text uses the same visible
 * control/format escaping as complete saved values. This is one slice, not a
 * claim that separate reads belong to one whole-value snapshot.
 */
internal fun savedEndpointDisplayBytes(value: ByteArray): String = buildString {
    append('"')
    val text = StringBuilder()
    fun flushText() {
        if (text.isNotEmpty()) {
            val quoted = savedEndpointDisplayText(text.toString())
            append(quoted, 1, quoted.length - 1)
            text.setLength(0)
        }
    }
    var offset = 0
    while (offset < value.size) {
        // Track bytes ourselves: platform decoders differ in their error-position
        // updates, so a decoder's position must never select the byte we escape.
        val first = value[offset].toInt() and 0xff
        val remaining = value.size - offset
        fun byteAt(delta: Int) = value[offset + delta].toInt() and 0xff
        fun continuation(delta: Int) = byteAt(delta) in 0x80..0xbf
        val width = when {
            first < 0x80 -> 1
            first in 0xc2..0xdf && remaining >= 2 && continuation(1) -> 2
            first in 0xe0..0xef && remaining >= 3 && continuation(1) && continuation(2) &&
                (first != 0xe0 || byteAt(1) >= 0xa0) &&
                (first != 0xed || byteAt(1) < 0xa0) -> 3
            first in 0xf0..0xf4 && remaining >= 4 && continuation(1) && continuation(2) && continuation(3) &&
                (first != 0xf0 || byteAt(1) >= 0x90) &&
                (first != 0xf4 || byteAt(1) <= 0x8f) -> 4
            else -> 0
        }
        if (width == 0) {
            flushText()
            append("\\x")
            append(first.toString(16).padStart(2, '0'))
            offset++
        } else {
            var codePoint = first and when (width) { 1 -> 0x7f; 2 -> 0x1f; 3 -> 0x0f; else -> 0x07 }
            for (index in 1 until width) codePoint = (codePoint shl 6) or (byteAt(index) and 0x3f)
            if (codePoint <= 0xffff) {
                text.append(codePoint.toChar())
            } else {
                val supplementary = codePoint - 0x10000
                text.append(((supplementary shr 10) + 0xd800).toChar())
                text.append(((supplementary and 0x3ff) + 0xdc00).toChar())
            }
            offset += width
        }
    }
    flushText()
    append('"')
}
