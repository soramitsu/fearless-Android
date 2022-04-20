package jp.co.soramitsu.common.list

typealias GroupedList<K, V> = Map<K, List<V>>

fun <K, V> emptyGroupedList() = emptyMap<K, V>()

fun <K, V> GroupedList<K?, V>.toListWithHeaders(): List<Any?> = map { (groupKey, values) ->
    groupKey?.let { listOf(groupKey) }.orEmpty() + values
}.flatten()

fun <K, V> GroupedList<K, V>.toValueList(): List<V> = map { (_, values) -> values }.flatten()
