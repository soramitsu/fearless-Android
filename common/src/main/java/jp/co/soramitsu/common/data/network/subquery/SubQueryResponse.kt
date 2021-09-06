package jp.co.soramitsu.common.data.network.subquery

class SubQueryResponse<T>(
    val data: T
)

class SubQueryNodes<T>(val nodes: List<T>)
