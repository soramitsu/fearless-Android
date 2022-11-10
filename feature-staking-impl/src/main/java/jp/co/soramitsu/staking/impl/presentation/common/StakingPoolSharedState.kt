package jp.co.soramitsu.staking.impl.presentation.common

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.MutableStateFlow

data class StakingPoolJoinFlowState(
    val amount: BigDecimal? = null,
    val selectedPool: PoolInfo? = null
)

data class StakingPoolCreateFlowState(
    val poolName: String? = null,
    val amountInPlanks: BigInteger? = null,
    val poolId: Int? = null,
    val nominatorAddress: String? = null,
    val stateTogglerAddress: String? = null
) {
    val requirePoolName
        get() = requireNotNull(poolName)
    val requireAmountInPlanks
        get() = requireNotNull(amountInPlanks)
    val requirePoolId
        get() = requireNotNull(poolId)
    val requireNominatorAddress
        get() = requireNotNull(nominatorAddress)
    val requireStateTogglerAddress
        get() = requireNotNull(stateTogglerAddress)
}

data class StakingPoolManageFlowState(
    val redeemInPlanks: BigInteger,
    val claimableInPlanks: BigInteger,
    val stakedInPlanks: BigInteger,
    val amountInPlanks: BigInteger? = null
)

data class StakingPoolState(
    val chain: Chain? = null,
    val asset: Asset? = null,
    val chainAsset: Chain.Asset? = null,
    val address: String? = null,
    val amount: BigDecimal? = null
) {
    val accountId
        get() = requireChain.accountIdOf(requireAddress)
    val requireAddress
        get() = requireNotNull(address)
    val requireAsset
        get() = requireNotNull(asset)
    val requireChain
        get() = requireNotNull(chain)
    val requireAmount
        get() = requireNotNull(amount)
}

data class SelectValidatorFlowState(
    val selectedValidators: List<AccountId> = emptyList(),
    val poolName: String?,
    val poolId: BigInteger?,
    val selectMode: ValidatorSelectMode? = null
) {
    enum class ValidatorSelectMode {
        CUSTOM, RECOMMENDED
    }

    val requirePoolId: BigInteger
        get() = requireNotNull(poolId)
    val requirePoolName
        get() = requireNotNull(poolName)
    val requireSelectMode
        get() = requireNotNull(selectMode)
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
