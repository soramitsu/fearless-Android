package jp.co.soramitsu.staking.impl.data.network.subquery.request

import java.math.BigInteger

class StakingSoraEraValidatorsRequest(
    eraFrom: BigInteger,
    eraTo: BigInteger,
    accountAddress: String
) {
    val query = """
query MyQuery {
  stakingEraNominators(where: {AND: {era: {index_gte: $eraFrom, index_lte: $eraTo}}, staker: {id_eq: "$accountAddress"}}) {
    nominations {
      validator {
        validator {
          id
        }
      }
    }
  }
}
    """.trimIndent()
}
