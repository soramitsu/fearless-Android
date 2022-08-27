package jp.co.soramitsu.staking.impl.presentation.staking.main.compose

import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.staking.main.PoolMemberViewState

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

fun StakeInfoViewState.PoolStakeInfoViewState.update(pool: PoolMemberViewState.PoolModel): StakeInfoViewState.PoolStakeInfoViewState {
    return copy(
        title = pool.name ?: "Your pool staking",
        staked = staked.copy(value = pool.staked, additionalValue = pool.stakedFiat),
        rewarded = rewarded.copy(value = pool.rewarded, additionalValue = pool.rewardedFiat),
        redeemable = redeemable.copy(value = pool.redeemable, additionalValue = pool.redeemableFiat),
        unstaking = unstaking.copy(value = pool.unstaking, additionalValue = pool.unstakingFiat),
    )
}
