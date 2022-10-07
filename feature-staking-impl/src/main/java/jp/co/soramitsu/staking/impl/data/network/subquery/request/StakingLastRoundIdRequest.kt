package jp.co.soramitsu.staking.impl.data.network.subquery.request

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
