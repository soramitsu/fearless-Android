package jp.co.soramitsu.common.data.network.subquery

import java.math.BigInteger

class TransactionHistoryRemote(val historyElements: SubQueryNodes<HistoryElement>) {
    class HistoryElement(val reward: Reward) {
        class Reward(val amount: BigInteger)
    }
}
