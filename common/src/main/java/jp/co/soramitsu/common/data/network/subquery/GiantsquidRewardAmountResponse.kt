package jp.co.soramitsu.common.data.network.subquery

import java.math.BigInteger

class GiantsquidRewardAmountResponse(val stakingRewards: List<StakingReward>) {
    class StakingReward(
        val amount: BigInteger
    )
}
