package jp.co.soramitsu.common.utils.formatting

fun String.shortenAddress(symbolsCount: Int = 8): String {
    return when {
        length <= symbolsCount * 2 + 1 -> this
        else -> "${take(symbolsCount)}...${takeLast(symbolsCount)}"
    }
}
