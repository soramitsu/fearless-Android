package jp.co.soramitsu.account.impl.presentation.recovery

/** Quotes complete stored text, making invisible characters and literal escape sequences distinct. */
internal fun savedEndpointDisplayText(value: String): String = buildString {
    append('"')
    var index = 0
    while (index < value.length) {
        val codePoint = value.codePointAt(index)
        val width = Character.charCount(codePoint)
        val character = value[index]
        when {
            character == '\\' -> append("\\\\")
            character == '"' -> append("\\\"")
            Character.isISOControl(codePoint) || Character.getType(codePoint) in INVISIBLE_TYPES ||
                Character.getType(codePoint) == Character.SURROGATE.toInt() -> {
                repeat(width) { offset ->
                    append("\\u")
                    append(value[index + offset].code.toString(16).padStart(4, '0'))
                }
            }
            codePoint != ' '.code && Character.getType(codePoint) == Character.SPACE_SEPARATOR.toInt() -> {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            }
            else -> append(value, index, index + width)
        }
        index += width
    }
    append('"')
}

private val INVISIBLE_TYPES = setOf(
    Character.FORMAT.toInt(), Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt()
)
