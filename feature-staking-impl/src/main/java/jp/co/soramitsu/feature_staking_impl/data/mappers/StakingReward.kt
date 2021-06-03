package jp.co.soramitsu.feature_staking_impl.data.mappers

import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SumRewardResponse
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.core_db.model.StakingRewardLocal
import jp.co.soramitsu.core_db.model.TotalRewardLocal
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.response.StakingRewardRemote
import jp.co.soramitsu.feature_staking_impl.domain.model.StakingReward
import jp.co.soramitsu.feature_staking_impl.domain.model.TotalReward
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import java.math.BigInteger

fun mapStakingRewardLocalToStakingReward(local: StakingRewardLocal): StakingReward {
    return with(local) {
        val token = Token.Type.fromNetworkType(accountAddress.networkType())
        val type = if (eventId == "Reward") StakingReward.Type.REWARD else StakingReward.Type.SLASH

        StakingReward(
            accountAddress = accountAddress,
            type = type,
            blockNumber = blockNumber,
            extrinsicIndex = extrinsicIndex,
            extrinsicHash = extrinsicHash,
            moduleId = moduleId,
            eventIndex = eventIndex,
            amount = token.amountFromPlanks(amountInPlanks),
            blockTimestamp = blockTimestamp
        )
    }
}

fun sumRewards(rewards: List<StakingReward>): TotalReward {
    return rewards.sumByBigDecimal {
        it.amount * it.type.summingCoefficient.toBigDecimal()
    }
}

fun mapSubquerySumRewardResponseToAmount(response: SubQueryResponse<SumRewardResponse>): BigInteger? {
    return response.data.sumReward?.accountTotal
}

fun mapTotalRewardLocalToTotalReward(reward: TotalRewardLocal): TotalReward {
    return with(reward) {
        val token = Token.Type.fromNetworkType(accountAddress.networkType())

        token.amountFromPlanks(reward.totalReward ?: BigInteger.ZERO)
    }
}

fun mapStakingRewardRemoteToLocal(
    remote: StakingRewardRemote,
    accountAddress: String,
): StakingRewardLocal {
    return with(remote) {

        StakingRewardLocal(
            accountAddress = accountAddress,
            eventIndex = eventIndex,
            eventIdx = eventIdx,
            blockNumber = blockNumber,
            extrinsicIndex = extrinsicIndex,
            moduleId = moduleId,
            eventId = eventId,
            params = params,
            extrinsicHash = extrinsicHash,
            amountInPlanks = amount,
            blockTimestamp = blockTimestamp,
            slashKton = slashKton.orEmpty()
        )
    }
}
