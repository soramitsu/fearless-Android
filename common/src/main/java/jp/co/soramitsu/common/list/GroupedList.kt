package jp.co.soramitsu.common.list

typealias GroupedList<K, V> = Map<K, List<V>>

fun <K, V> GroupedList<K, V>.flatten(): List<Any?> = map { (groupKey, values) ->
    listOf(groupKey) + values
}.flatten()
