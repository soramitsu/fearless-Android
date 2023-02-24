package jp.co.soramitsu.staking.impl.data.network.subquery.request

class SubsquidEthRewardAmountRequest(accountAddress: String) {
    val query = """
    query MyQuery {
       rewards(where: {accountId_eq: "$accountAddress"}) {
        amount
      }
    }    
    """.trimIndent()
}

class SubsquidRelayRewardAmountRequest(accountAddress: String) {
    val query = """
    query MyQuery {
      historyElements(where: {reward_isNull: false, address_eq: "$accountAddress"}) {
        reward {
          amount
        }
      }
    }
    """.trimIndent()
}
