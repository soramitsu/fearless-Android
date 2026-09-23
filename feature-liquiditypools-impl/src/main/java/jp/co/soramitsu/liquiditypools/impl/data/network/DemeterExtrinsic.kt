package jp.co.soramitsu.liquiditypools.impl.data.network

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder

private const val DEMETER_FARMING_MODULE = "DemeterFarmingPlatform"

fun ExtrinsicBuilder.demeterDeposit(
    baseAssetId: String,
    poolAssetId: String,
    rewardAssetId: String,
    amount: BigInteger
) = call(
    DEMETER_FARMING_MODULE,
    "deposit",
    mapOf(
        "base_asset" to baseAssetId.mapCodeToken(),
        "pool_asset" to poolAssetId.mapCodeToken(),
        "reward_asset" to rewardAssetId.mapCodeToken(),
        "is_farm" to true,
        "pooled_tokens" to amount
    )
)

fun ExtrinsicBuilder.demeterWithdraw(
    baseAssetId: String,
    poolAssetId: String,
    rewardAssetId: String,
    amount: BigInteger
) = call(
    DEMETER_FARMING_MODULE,
    "withdraw",
    mapOf(
        "base_asset" to baseAssetId.mapCodeToken(),
        "pool_asset" to poolAssetId.mapCodeToken(),
        "reward_asset" to rewardAssetId.mapCodeToken(),
        "pooled_tokens" to amount,
        "is_farm" to true
    )
)

fun ExtrinsicBuilder.demeterClaimRewards(
    baseAssetId: String,
    poolAssetId: String,
    rewardAssetId: String
) = call(
    DEMETER_FARMING_MODULE,
    "get_rewards",
    mapOf(
        "base_asset" to baseAssetId.mapCodeToken(),
        "pool_asset" to poolAssetId.mapCodeToken(),
        "reward_asset" to rewardAssetId.mapCodeToken(),
        "is_farm" to true
    )
)
