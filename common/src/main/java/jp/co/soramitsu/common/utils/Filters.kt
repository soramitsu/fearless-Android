package jp.co.soramitsu.common.utils

interface Filter<T> {
    fun shouldInclude(model: T): Boolean
}

fun <T> List<T>.applyFilters(filters: List<Filter<T>>): List<T> {
    return filter { item -> filters.all { filter -> filter.shouldInclude(item) } }
}

fun <T> List<T>.applyFiltersAny(filters: List<Filter<T>>): List<T> {
    return filter { item -> filters.any { filter -> filter.shouldInclude(item) } }
}
