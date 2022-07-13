package jp.co.soramitsu.feature_staking_impl.data.network.subquery.request

import jp.co.soramitsu.fearless_utils.extensions.toHexString

class StakingCollatorsApyRequest(collatorIds: List<ByteArray>, roundId: Int?) {
    val query = """
    query {
        collatorRounds(
            filter: {
                collatorId: { inInsensitive: ${collatorIds.map { "\"" + it.toHexString(true) + "\"" }} }
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
