package jp.co.soramitsu.staking.impl.presentation.common

import java.math.BigDecimal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.MutableStateFlow

data class StakingPoolJoinFlow(
    val chain: Chain? = null,
    val asset: Asset? = null,
    val chainAsset: Chain.Asset? = null,
    val amount: BigDecimal? = null,
    val selectedPool: PoolInfo? = null,
    val address: String? = null
)

class StakingPoolSetupFlowSharedState {

    val process = MutableStateFlow<StakingPoolJoinFlow?>(null)

    fun set(newState: StakingPoolJoinFlow) {
        process.value = newState
    }

    fun get(): StakingPoolJoinFlow? = process.value

    fun mutate(mutation: (StakingPoolJoinFlow?) -> StakingPoolJoinFlow) {
        set(mutation(get()))
    }

    fun complete() {
        process.value = null
    }
}

fun StakingPoolSetupFlowSharedState.chain() = get()?.chain
