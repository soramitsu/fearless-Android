package jp.co.soramitsu.feature_staking_api.domain.model

import jp.co.soramitsu.common.utils.sumByBigInteger
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

fun StakingLedger.sumStaking(
    condition: (chunk: UnlockChunk) -> Boolean
): BigInteger {
    return unlocking
        .filter { condition(it) }
        .sumByBigInteger(UnlockChunk::amount)
}

fun UnlockChunk.isUnbondingIn(activeEraIndex: BigInteger) = era > activeEraIndex
fun UnlockChunk.isRedeemableIn(activeEraIndex: BigInteger) = era <= activeEraIndex
