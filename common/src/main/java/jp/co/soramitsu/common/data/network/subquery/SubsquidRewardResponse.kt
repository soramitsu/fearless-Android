package jp.co.soramitsu.common.data.network.subquery

import java.math.BigInteger

class SubsquidRewardResponse(val rewards: List<Reward>) {
    class Reward(
        val id: String,
        val amount: BigInteger?,
        val blockNumber: Int?,
        val round: Int?,
        val timestamp: String?,
        val extrinsicHash: String?
    )
}
