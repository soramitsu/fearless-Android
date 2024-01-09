package jp.co.soramitsu.common.utils.formatting

fun String.shortenHash() = "${take(5)}...${takeLast(5)}"
