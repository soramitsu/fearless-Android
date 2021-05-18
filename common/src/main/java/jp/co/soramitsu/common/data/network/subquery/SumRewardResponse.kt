package jp.co.soramitsu.common.data.network.subquery

class SumRewardResponse(val sumReward: SumReward) {
    class SumReward(val accountTotal: String)
}
