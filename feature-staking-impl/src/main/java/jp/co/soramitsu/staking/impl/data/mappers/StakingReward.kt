package jp.co.soramitsu.staking.impl.data.mappers

import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.TransactionHistoryRemote
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.coredb.model.TotalRewardLocal
import jp.co.soramitsu.staking.impl.domain.model.TotalReward

fun mapSubqueryHistoryToTotalReward(response: SubQueryResponse<TransactionHistoryRemote>): TotalReward {
    return response.data.historyElements.nodes.sumByBigInteger { it.reward.amount }
}

fun mapTotalRewardLocalToTotalReward(reward: TotalRewardLocal): TotalReward {
    return reward.totalReward
}
