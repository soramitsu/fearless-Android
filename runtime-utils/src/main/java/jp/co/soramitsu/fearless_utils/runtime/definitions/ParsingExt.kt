package jp.co.soramitsu.fearless_utils.runtime.definitions

internal fun String.splitTuple(): List<String> {
    val innerPart = replace(Regex("\\s"), "").removeSurrounding("(", ")")

    val result = mutableListOf<String>()
    var bracketsCount = 0
    var currentBeginning = 0

    innerPart.forEachIndexed { index, c ->
        when (c) {
            '(', '<', '[' -> bracketsCount++
            ')', '>', ']' -> bracketsCount--
            ',' -> {
                if (bracketsCount == 0) {
                    result += innerPart.substring(currentBeginning, index)
                    currentBeginning = index + 1
                }
            }
        }
    }

    if (currentBeginning < innerPart.length) {
        result += innerPart.substring(currentBeginning, innerPart.length)
    }

    return result
}
