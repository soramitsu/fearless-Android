package jp.co.soramitsu.staking.impl.data.network.subquery.request

class SubsquidSoraStakingRewardsRequest(address: String) {
    val query = "query MyQuery {\n" +
            "  stakingRewards(orderBy: timestamp_DESC, where: {payee_eq: \"$address\"}) {\n" +
            "    id\n" +
            "    amount\n" +
            "    timestamp\n" +
            "  }\n" +
            "}\n"
}