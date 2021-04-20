package jp.co.soramitsu.feature_staking_api.domain.model

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import java.math.BigInteger

class StakingLedger(
    val stashId: AccountId,
    val total: BigInteger,
    val active: BigInteger,
    val unlocking: List<UnlockChunk>,
    val claimedRewards: List<BigInteger>
)

class UnlockChunk(val amount: BigInteger, val era: BigInteger)
