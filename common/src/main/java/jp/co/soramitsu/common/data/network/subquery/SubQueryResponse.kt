package jp.co.soramitsu.common.data.network.subquery

class SubQueryResponse (val data: DataSubQuery) {
    class DataSubQuery(val sumReward: SumReward){
        class SumReward(val accountTotal: String)
    }
}
