package jp.co.soramitsu.staking.impl.data.network.subquery.request

import jp.co.soramitsu.shared_utils.extensions.toHexString

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
