package jp.co.soramitsu.staking.impl.data.network.subquery.request

import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix

class SubsquidDelegatorHistoryRequest(delegatorAddress: String, collatorAddress: String) {
    val query = """
       query MyQuery {
        historyElements(where: {staker: {id_eq: "${delegatorAddress.requireHexPrefix()}"}, amount_isNull: false, collator: {id_eq: "${collatorAddress.requireHexPrefix()}"}}) {
          id
          amount
          blockNumber
          timestamp
          type
        }
      }
    """.trimIndent()
}
