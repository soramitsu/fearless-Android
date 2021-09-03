package jp.co.soramitsu.feature_staking_impl.data.mappers

import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.TransactionHistoryRemote
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.core_db.model.TotalRewardLocal
import jp.co.soramitsu.feature_staking_impl.domain.model.TotalReward

fun mapSubqueryHistoryToTotalReward(response: SubQueryResponse<TransactionHistoryRemote>): TotalReward {
    return response.data.historyElements.nodes.sumByBigInteger { it.reward.amount }
}

fun mapTotalRewardLocalToTotalReward(reward: TotalRewardLocal): TotalReward {
    return reward.totalReward
}
