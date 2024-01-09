package jp.co.soramitsu.staking.impl.data.network.subquery.request

class SubsquidLastRoundIdRequest {
    val query = """
    query MyQuery {
      rounds(orderBy: index_DESC, limit: 1) {
        id
      }
    }
    """.trimIndent()
}
