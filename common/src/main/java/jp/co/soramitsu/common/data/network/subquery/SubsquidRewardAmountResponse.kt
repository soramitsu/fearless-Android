package jp.co.soramitsu.common.data.network.subquery

import java.math.BigInteger

class SubsquidRewardAmountResponse(val rewards: List<Reward>) {
    class Reward(
        val amount: BigInteger?
    )
}
