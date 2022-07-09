package jp.co.soramitsu.feature_staking_impl.data.network.subquery.request

class StakingDelegatorHistoryRequest(delegatorAddress: String) {
    val query = """
    query {
        delegatorHistoryElements(
        last: 20,
        filter: {
            delegatorId: { equalToInsensitive: "$delegatorAddress"}  }
        ) {
            nodes {
              id
              blockNumber
              delegatorId
              collatorId
              timestamp
              type
              roundId
              amount
            }
        }
    }
    """.trimIndent()
}
