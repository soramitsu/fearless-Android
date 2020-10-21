package jp.co.soramitsu.common.utils

@Suppress("UNCHECKED_CAST")
class ComponentHolder(val values: List<*>) {
    operator fun <T> component1() = values.first() as T
    operator fun <T> component2() = values[1] as T
    operator fun <T> component3() = values[2] as T
    operator fun <T> component4() = values[3] as T
    operator fun <T> component5() = values[4] as T
}