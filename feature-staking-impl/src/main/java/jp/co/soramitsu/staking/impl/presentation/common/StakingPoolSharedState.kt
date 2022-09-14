package jp.co.soramitsu.staking.impl.presentation.common

import java.math.BigDecimal
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.MutableStateFlow

data class StakingPoolJoinFlowState(
    val amount: BigDecimal? = null,
    val selectedPool: PoolInfo? = null
)

data class StakingPoolManageFlowState(
    val redeem: String
)

data class StakingPoolState(
    val chain: Chain? = null,
    val asset: Asset? = null,
    val chainAsset: Chain.Asset? = null,
    val address: String? = null
) {
    val accountId = address?.let { chain?.accountIdOf(it) }
}

class StakingPoolSharedState<T> {

    val process = MutableStateFlow<T?>(null)

    fun set(newState: T) {
        process.value = newState
    }

    fun get(): T? = process.value

    fun mutate(mutation: (T?) -> T) {
        set(mutation(get()))
    }

    fun complete() {
        process.value = null
    }
}
