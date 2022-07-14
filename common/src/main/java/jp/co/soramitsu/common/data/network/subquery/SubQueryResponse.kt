package jp.co.soramitsu.common.data.network.subquery

const val subQueryTransactionPageSize = 100

class SubQueryResponse<T>(
    val data: T
)

class SubQueryNodes<T>(val nodes: List<T>)
