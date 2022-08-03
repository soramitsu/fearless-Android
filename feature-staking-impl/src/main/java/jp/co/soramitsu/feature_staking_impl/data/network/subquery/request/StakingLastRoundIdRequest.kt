package jp.co.soramitsu.feature_staking_impl.data.network.subquery.request

class StakingLastRoundIdRequest {
    val query = """
    query {
          rounds(last: 1) {
              nodes {
                id
              }
          }
        }
    """.trimIndent()
}
