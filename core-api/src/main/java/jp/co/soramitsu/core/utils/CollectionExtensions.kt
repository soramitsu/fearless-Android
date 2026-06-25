package jp.co.soramitsu.core.utils

fun <T> List<T>.cycle(): Sequence<T> {
    var index = 0

    return generateSequence { this[index++ % size] }
}
