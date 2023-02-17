package jp.co.soramitsu.staking.impl.data.network.subquery.request

class SubsquidRewardAmountRequest(accountAddress: String) {
    val query = """
    query MyQuery {
       rewards(where: {accountId_eq: "$accountAddress"}) {
        amount
      }
    }    
    """.trimIndent()
}
