package jp.co.soramitsu.feature_staking_impl.data.network.subscan.request

class StakingSumRewardRequest(accountAddress: String) {
    val query = """
        {
        sumReward
           (id: "$accountAddress")
           {accountTotal}
        }
    """.trimIndent()
}
