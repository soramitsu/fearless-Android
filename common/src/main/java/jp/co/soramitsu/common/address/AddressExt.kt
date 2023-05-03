package jp.co.soramitsu.common.address

fun String.shorten(symbolsCount: Int = 8) = when {
    length < 20 -> this
    else -> "${take(symbolsCount)}...${takeLast(symbolsCount)}"
}
