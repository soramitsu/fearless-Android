package jp.co.soramitsu.staking.impl.data.network.subquery.request

class GiantsquidRewardAmountRequest(accountId: String) {
    val query = """
    query MyQuery {
  stakingRewards(where: {account: {publicKey_eq: "$accountId"}}) {
    amount
    validatorId
    account {
      id
      publicKey
    }
  }
}
    """.trimIndent()
}
