package jp.co.soramitsu.feature_staking_impl.data.network.subquery.request

import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix

class StakingDelegatorHistoryRequest(delegatorAddress: String, collatorAddress: String) {
    val query = """
    query {
        delegatorHistoryElements(
        last: 20,
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
