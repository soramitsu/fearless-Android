package jp.co.soramitsu.staking.impl.presentation.staking.main.compose

import java.math.BigInteger
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.staking.api.domain.model.NominationPool
import jp.co.soramitsu.staking.api.domain.model.NominationPoolState
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks

fun NominationPool.toViewState(asset: Asset, resourceManager: ResourceManager): StakeInfoViewState.PoolStakeInfoViewState {
    val staked = asset.token.amountFromPlanks(stakedInPlanks)
    val stakedFormatted = staked.formatTokenAmount(asset.token.configuration)
    val stakedFiat = staked.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

    val rewardedInPlanks = BigInteger.ZERO
    val rewarded = asset.token.amountFromPlanks(rewardedInPlanks)
    val rewardedFormatted = rewarded.formatTokenAmount(asset.token.configuration)
    val rewardedFiat = rewarded.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

    val redeemableInPlanks = BigInteger.ZERO
    val redeemable = asset.token.amountFromPlanks(redeemableInPlanks)
    val redeemableFormatted = redeemable.formatTokenAmount(asset.token.configuration)
    val redeemableFiat = redeemable.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

    val unstaking = asset.token.amountFromPlanks(unstaking)
    val unstakingFormatted = unstaking.formatTokenAmount(asset.token.configuration)
    val unstakingFiat = unstaking.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

    val status = when (state) {
        NominationPoolState.Open -> StakeStatus.PoolActive(10000L, false)
        NominationPoolState.Blocked -> StakeStatus.Inactive("")
        NominationPoolState.Destroying -> StakeStatus.Inactive("")
    }

    val default = StakeInfoViewState.PoolStakeInfoViewState.default(resourceManager)

    return default.copy(
        title = name ?: poolId.toString(),
        staked = default.staked.copy(value = stakedFormatted, additionalValue = stakedFiat),
        rewarded = default.rewarded.copy(value = rewardedFormatted, additionalValue = rewardedFiat),
        redeemable = default.redeemable.copy(value = redeemableFormatted, additionalValue = redeemableFiat),
        unstaking = default.unstaking.copy(value = unstakingFormatted, additionalValue = unstakingFiat),
        status = status
    )
}
