package jp.co.soramitsu.staking.impl.presentation.staking.main.compose

import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.NominationPool
import jp.co.soramitsu.staking.api.domain.model.NominationPoolState
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks

sealed class StakeInfoViewState {
    abstract val title: String
    abstract val status: StakeStatus

    data class PoolStakeInfoViewState(
        override val title: String,
        val staked: TitleValueViewState,
        val rewarded: TitleValueViewState,
        val redeemable: TitleValueViewState,
        val unstaking: TitleValueViewState,
        override val status: StakeStatus
    ) : StakeInfoViewState() {
        companion object
    }

    data class RelayChainStakeInfoViewState(
        override val title: String,
        val staked: TitleValueViewState,
        val rewarded: TitleValueViewState,
        override val status: StakeStatus
    ) : StakeInfoViewState() {
        companion object
    }

    data class ParachainStakeInfoViewState(
        override val title: String,
        val staked: TitleValueViewState,
        val rewards: TitleValueViewState,
        override val status: StakeStatus
    ) : StakeInfoViewState() {
        companion object
    }
}

fun StakeInfoViewState.PoolStakeInfoViewState.Companion.default(resourceManager: ResourceManager): StakeInfoViewState.PoolStakeInfoViewState {
    return StakeInfoViewState.PoolStakeInfoViewState(
        title = "Your pool staking",
        staked = TitleValueViewState(resourceManager.getString(R.string.wallet_balance_bonded)),
        rewarded = TitleValueViewState(resourceManager.getString(R.string.staking_total_rewards_v1_9_0)),
        redeemable = TitleValueViewState(resourceManager.getString(R.string.wallet_balance_redeemable)),
        unstaking = TitleValueViewState(resourceManager.getString(R.string.wallet_balance_unbonding_v1_9_0)),
        status = StakeStatus.PoolActive(0L, true)
    )
}

fun NominationPool.toViewState(asset: Asset, resourceManager: ResourceManager): StakeInfoViewState.PoolStakeInfoViewState {
    val staked = asset.token.amountFromPlanks(myStakeInPlanks)
    val stakedFormatted = staked.formatTokenAmount(asset.token.configuration)
    val stakedFiat = staked.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

    val rewardedInPlanks = pendingRewards
    val rewarded = asset.token.amountFromPlanks(rewardedInPlanks)
    val rewardedFormatted = rewarded.formatTokenAmount(asset.token.configuration)
    val rewardedFiat = rewarded.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

    val redeemableInPlanks = redeemable
    val redeemable = asset.token.amountFromPlanks(redeemableInPlanks)
    val redeemableFormatted = redeemable.formatTokenAmount(asset.token.configuration)
    val redeemableFiat = redeemable.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

    val unstaking = asset.token.amountFromPlanks(unbonding)
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
