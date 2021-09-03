package jp.co.soramitsu.common.data.network.subquery

class SubQueryResponse<T>(
    val data: T
)

class SubQueryNodes<T>(private val nodes: List<T>) : List<T> by nodes
