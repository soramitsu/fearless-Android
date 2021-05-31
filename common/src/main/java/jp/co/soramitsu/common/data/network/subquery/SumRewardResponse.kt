package jp.co.soramitsu.common.data.network.subquery

import java.math.BigInteger

class SumRewardResponse(val sumReward: SumReward?) {
    class SumReward(val accountTotal: BigInteger?)
}
