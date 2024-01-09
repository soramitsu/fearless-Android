package jp.co.soramitsu.staking.impl.data.network.subquery.request

import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix

class StakingDelegatorHistoryRequest(delegatorAddress: String, collatorAddress: String) {
    val query = """
    query {
        delegatorHistoryElements(
        orderBy: TIMESTAMP_DESC,
        last: 10,
        filter: {
            delegatorId: { equalToInsensitive: "${delegatorAddress.requireHexPrefix()}"},
            collatorId: { equalToInsensitive: "${collatorAddress.requireHexPrefix()}"} }
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
