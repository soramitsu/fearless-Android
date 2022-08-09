package jp.co.soramitsu.featurestakingimpl.data.network.subquery.request

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
