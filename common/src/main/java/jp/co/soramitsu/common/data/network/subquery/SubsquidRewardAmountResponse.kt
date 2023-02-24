package jp.co.soramitsu.common.data.network.subquery

import java.math.BigInteger

class SubsquidEthRewardAmountResponse(val rewards: List<Reward>) {
    class Reward(
        val amount: BigInteger?
    )
}

class SubsquidRelayRewardAmountResponse(val historyElements: List<HistoryElement>) {
    class HistoryElement(
        val reward: Reward?
    )
    class Reward(
        val amount: BigInteger
    )
}
