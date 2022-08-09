package jp.co.soramitsu.featurestakingimpl.data.network.subquery.request

class StakingSumRewardRequest(accountAddress: String) {
    val query = """
    query {
        historyElements(
        filter: {
            reward: { notEqualTo: "null"},
            address: { equalTo: "$accountAddress"}  }
        ) {
            nodes {
                  reward
            }
        }
    }
    """.trimIndent()
}
