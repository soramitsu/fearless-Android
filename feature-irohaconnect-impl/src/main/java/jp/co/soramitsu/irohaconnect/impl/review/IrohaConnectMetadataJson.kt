// A JSON.parse-compatible parser and iroha-js's metadata normalizer: one branch per JSON grammar rule.
@file:Suppress("MagicNumber", "CyclomaticComplexMethod", "NestedBlockDepth")

package jp.co.soramitsu.irohaconnect.impl.review

import java.text.Normalizer

/**
 * Transaction metadata values are JSON texts that must already be in the SDK's canonical form: parsed with
 * JavaScript `JSON.parse` semantics, normalized (safe integers only, bounded depth and size, canonical keys) and
 * printed back byte for byte. A port of iroha-js `normalizeMetadataValue` and `canonicalJsonStringify`.
 */
internal sealed class JsonValue {
    object Null : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()

    /** A safe integer; -0 is kept as 0, exactly as JavaScript prints it. */
    data class Integer(val value: Long) : JsonValue()
    data class Text(val value: String) : JsonValue()
    data class Array(val items: List<JsonValue>) : JsonValue()

    /** Keys in JavaScript property order: array-index keys ascending, then insertion order. */
    data class Object(val entries: LinkedHashMap<String, JsonValue>) : JsonValue()
}

internal class MetadataJsonException(message: String) : Exception(message)

internal object IrohaConnectMetadataJson {
    const val MAX_JSON_BYTES = 64 * 1024
    const val MAX_ENTRIES = 64
    const val MAX_DEPTH = 32
    const val MAX_NODES = 4096
    const val MAX_KEY_BYTES = 255
    private const val MAX_PARSE_DEPTH = 64
    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991.0

    class State(var nodes: Int)

    private fun fail(message: String): Nothing = throw MetadataJsonException(message)

    // ------------------------------------------------------------------------------------------------------------
    // JSON.parse

    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.value(0)
        parser.skipWhitespace()
        if (!parser.atEnd()) fail("trailing characters")
        return value
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun atEnd() = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index] in " \t\n\r") index += 1
        }

        private fun expect(character: Char) {
            if (index >= text.length || text[index] != character) fail("expected '$character'")
            index += 1
        }

        fun value(depth: Int): JsonValue {
            if (depth > MAX_PARSE_DEPTH) fail("nesting is too deep")
            if (index >= text.length) fail("unexpected end")
            return when (text[index]) {
                'n' -> literal("null", JsonValue.Null)
                't' -> literal("true", JsonValue.Bool(true))
                'f' -> literal("false", JsonValue.Bool(false))
                '"' -> JsonValue.Text(string())
                '[' -> array(depth)
                '{' -> obj(depth)
                else -> number()
            }
        }

        private fun literal(word: String, value: JsonValue): JsonValue {
            if (!text.startsWith(word, index)) fail("invalid literal")
            index += word.length
            return value
        }

        private fun array(depth: Int): JsonValue {
            expect('[')
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (index < text.length && text[index] == ']') {
                index += 1
                return JsonValue.Array(items)
            }
            while (true) {
                skipWhitespace()
                items += value(depth + 1)
                skipWhitespace()
                if (index < text.length && text[index] == ',') {
                    index += 1
                    continue
                }
                expect(']')
                return JsonValue.Array(items)
            }
        }

        private fun obj(depth: Int): JsonValue {
            expect('{')
            val entries = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (index < text.length && text[index] == '}') {
                index += 1
                return JsonValue.Object(entries)
            }
            while (true) {
                skipWhitespace()
                if (index >= text.length || text[index] != '"') fail("expected a key")
                val key = string()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                entries[key] = value(depth + 1)
                skipWhitespace()
                if (index < text.length && text[index] == ',') {
                    index += 1
                    continue
                }
                expect('}')
                return JsonValue.Object(LinkedHashMap(jsPropertyOrder(entries.keys).associateWith { entries.getValue(it) }))
            }
        }

        private fun string(): String {
            expect('"')
            val output = StringBuilder()
            while (true) {
                if (index >= text.length) fail("unterminated string")
                val character = text[index++]
                when {
                    character == '"' -> return output.toString()
                    character.code < 0x20 -> fail("control character in string")
                    character == '\\' -> {
                        if (index >= text.length) fail("unterminated escape")
                        when (val escaped = text[index++]) {
                            '"' -> output.append('"')
                            '\\' -> output.append('\\')
                            '/' -> output.append('/')
                            'b' -> output.append('\b')
                            'f' -> output.append('\u000C')
                            'n' -> output.append('\n')
                            'r' -> output.append('\r')
                            't' -> output.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) fail("short unicode escape")
                                val hex = text.substring(index, index + 4)
                                if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail("bad unicode escape")
                                output.append(hex.toInt(16).toChar())
                                index += 4
                            }
                            else -> fail("bad escape \\$escaped")
                        }
                    }
                    else -> output.append(character)
                }
            }
        }

        private fun number(): JsonValue {
            val start = index
            if (index < text.length && text[index] == '-') index += 1
            if (index >= text.length) fail("invalid number")
            if (text[index] == '0') {
                index += 1
            } else if (text[index] in '1'..'9') {
                while (index < text.length && text[index].isAsciiDigit()) index += 1
            } else {
                fail("invalid number")
            }
            if (index < text.length && text[index] == '.') {
                index += 1
                if (index >= text.length || !text[index].isAsciiDigit()) fail("invalid number")
                while (index < text.length && text[index].isAsciiDigit()) index += 1
            }
            if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
                index += 1
                if (index < text.length && (text[index] == '+' || text[index] == '-')) index += 1
                if (index >= text.length || !text[index].isAsciiDigit()) fail("invalid number")
                while (index < text.length && text[index].isAsciiDigit()) index += 1
            }
            val value = text.substring(start, index).toDouble()
            // normalizeMetadataValue: only safe integers; decimals belong in strings.
            if (!value.isFinite() || value != Math.floor(value) || Math.abs(value) > MAX_SAFE_INTEGER) {
                fail("numbers must be safe integers")
            }
            return JsonValue.Integer(if (value == 0.0) 0L else value.toLong())
        }

        private fun Char.isAsciiDigit() = this in '0'..'9'
    }

    /** JavaScript `Object.keys` order: canonical array indexes ascending, then the rest in insertion order. */
    fun jsPropertyOrder(keys: Collection<String>): List<String> {
        val (indexes, names) = keys.partition(::isArrayIndex)
        return indexes.sortedBy { it.toLong() } + names
    }

    private fun isArrayIndex(key: String): Boolean {
        if (key.isEmpty() || key.length > 10 || !key.all { it in '0'..'9' }) return false
        if (key.length > 1 && key[0] == '0') return false
        return key.toLong() < 0xffff_ffffL
    }

    // ------------------------------------------------------------------------------------------------------------
    // Normalization rules

    fun isWellFormedUnicode(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val unit = value[index]
            if (unit.isHighSurrogate()) {
                if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) return false
                index += 2
                continue
            }
            if (unit.isLowSurrogate()) return false
            index += 1
        }
        return true
    }

    fun isNfc(value: String): Boolean = Normalizer.isNormalized(value, Normalizer.Form.NFC)

    fun isRustUnicodeWhitespace(value: String): Boolean = value.codePoints().anyMatch { codePoint ->
        codePoint in 0x09..0x0d || codePoint == 0x20 || codePoint == 0x85 || codePoint == 0xa0 || codePoint == 0x1680 ||
            codePoint in 0x2000..0x200a || codePoint == 0x2028 || codePoint == 0x2029 || codePoint == 0x202f ||
            codePoint == 0x205f || codePoint == 0x3000
    }

    fun utf8Length(value: String): Int = value.toByteArray(Charsets.UTF_8).size

    /** validateMetadataKey: a canonical metadata `Name`. */
    fun isValidKey(key: String): Boolean =
        key.isNotEmpty() && key.length <= MAX_KEY_BYTES && isWellFormedUnicode(key) && isNfc(key) &&
            !isRustUnicodeWhitespace(key) && key.none { it == '@' || it == '#' || it == '$' } &&
            utf8Length(key) <= MAX_KEY_BYTES

    fun normalize(
        value: JsonValue,
        depth: Int,
        state: State
    ) {
        state.nodes += 1
        if (state.nodes > MAX_NODES) fail("metadata exceeds $MAX_NODES values")
        if (depth > MAX_DEPTH) fail("metadata nesting exceeds $MAX_DEPTH")
        when (value) {
            JsonValue.Null, is JsonValue.Bool, is JsonValue.Integer -> Unit
            is JsonValue.Text -> {
                if (value.value.length > MAX_JSON_BYTES || utf8Length(value.value) > MAX_JSON_BYTES) fail("string is too long")
                if (!isWellFormedUnicode(value.value)) fail("string is not well-formed Unicode")
            }
            is JsonValue.Array -> {
                if (value.items.size > MAX_NODES - state.nodes) fail("metadata exceeds $MAX_NODES values")
                value.items.forEach { normalize(it, depth + 1, state) }
            }
            is JsonValue.Object -> {
                if (value.entries.size > MAX_NODES - state.nodes) fail("metadata exceeds $MAX_NODES values")
                value.entries.keys.forEach { if (!isValidKey(it)) fail("metadata key is not a canonical Name") }
                value.entries.keys.sortedWith(::compareUtf8).forEach { normalize(value.entries.getValue(it), depth + 1, state) }
            }
        }
    }

    fun compareUtf8(left: String, right: String): Int {
        val a = left.toByteArray(Charsets.UTF_8)
        val b = right.toByteArray(Charsets.UTF_8)
        for (index in 0 until minOf(a.size, b.size)) {
            val difference = (a[index].toInt() and 0xff) - (b[index].toInt() and 0xff)
            if (difference != 0) return difference
        }
        return a.size - b.size
    }

    // ------------------------------------------------------------------------------------------------------------
    // Printing

    /** canonicalJsonStringify: keys sorted by UTF-8 bytes, no whitespace, Rust-compatible escaping. */
    fun canonical(value: JsonValue): String = print(value, sortKeys = true)

    /** JSON.stringify of the parsed value (JavaScript property order), as the browser review shows it. */
    fun display(value: JsonValue): String = print(value, sortKeys = false)

    private fun print(value: JsonValue, sortKeys: Boolean): String = when (value) {
        JsonValue.Null -> "null"
        is JsonValue.Bool -> if (value.value) "true" else "false"
        is JsonValue.Integer -> value.value.toString()
        is JsonValue.Text -> quote(value.value)
        is JsonValue.Array -> value.items.joinToString(",", "[", "]") { print(it, sortKeys) }
        is JsonValue.Object -> {
            val keys = if (sortKeys) value.entries.keys.sortedWith(::compareUtf8) else value.entries.keys.toList()
            keys.joinToString(",", "{", "}") { "${quote(it)}:${print(value.entries.getValue(it), sortKeys)}" }
        }
    }

    fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u00").append(character.code.toString(16).padStart(2, '0')) else append(character)
            }
        }
        append('"')
    }
}
