package jp.co.soramitsu.staking.impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.staking.api.data.SyntheticStakingType
import jp.co.soramitsu.staking.api.data.syntheticStakingType
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingState

private const val TYPE_STAKED = "Staked"
private const val TYPE_ACCOUNT = "Account"

fun bindRewardDestination(rewardDestination: RewardDestination) = when (rewardDestination) {
    is RewardDestination.Restake -> DictEnum.Entry(TYPE_STAKED, null)
    is RewardDestination.Payout -> DictEnum.Entry(TYPE_ACCOUNT, rewardDestination.targetAccountId)
}

fun bindRewardDestination(
    scale: String,
    runtime: RuntimeSnapshot,
    stakingState: StakingState.Stash
): RewardDestination {
    val type = runtime.metadata.staking().storage("Payee").returnType()
    val isSoraStaking = stakingState.chain.utilityAsset?.syntheticStakingType() == SyntheticStakingType.SORA
    val dynamicInstance = type.fromHexOrNull(runtime, scale).cast<DictEnum.Entry<*>>()

    return when  {
        isSoraStaking && dynamicInstance.name == TYPE_STAKED -> RewardDestination.Payout(stakingState.stashId)
        dynamicInstance.name == TYPE_STAKED -> RewardDestination.Restake
        dynamicInstance.name == TYPE_ACCOUNT -> RewardDestination.Payout(dynamicInstance.value.cast())
        dynamicInstance.name == "Stash" -> RewardDestination.Payout(stakingState.stashId)
        dynamicInstance.name == "Controller" -> RewardDestination.Payout(stakingState.controllerId)
        else -> incompatible()
    }
}
