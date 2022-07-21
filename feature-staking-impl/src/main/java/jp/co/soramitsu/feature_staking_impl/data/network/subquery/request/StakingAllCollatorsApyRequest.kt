package jp.co.soramitsu.feature_staking_impl.data.network.subquery.request

class StakingAllCollatorsApyRequest(roundId: Int?) {
    val query = """
    query {
    collatorRounds (
      filter: {
                apr: { isNull: false, greaterThan: 0 }
                roundId: { equalTo: "$roundId" }
            }
    ) {
        nodes {
            collatorId
            apr
        }
    }
}
    """.trimIndent()
}
