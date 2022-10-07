package jp.co.soramitsu.common.data.network.subquery

import java.math.BigDecimal
import java.math.BigInteger

class StakingHistoryRemote(val delegatorHistoryElements: SubQueryNodes<HistoryElement>) {
    class HistoryElement(
        val id: String?,
        val blockNumber: BigInteger?,
        val delegatorId: String?,
        val collatorId: String?,
        val timestamp: String?,
        val type: BigInteger?,
        val roundId: String?,
        val amount: BigDecimal?
    )
}
