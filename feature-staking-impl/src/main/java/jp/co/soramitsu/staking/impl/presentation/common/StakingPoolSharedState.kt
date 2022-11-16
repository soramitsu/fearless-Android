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

data class SelectedValidatorsFlowState(
    val selectedValidators: List<AccountId> = emptyList(),
    val canChangeValidators: Boolean? = null,
    val poolName: String? = null,
    val poolId: BigInteger? = null
) {
    val requireCanChangeValidators: Boolean
        get() = requireNotNull(canChangeValidators)
    val requirePoolName: String
        get() = requireNotNull(poolName)
    val requirePoolId: BigInteger
        get() = requireNotNull(poolId)
}

data class EditPoolFlowState(
    val poolName: String,
    val poolId: BigInteger,
    val depositor: AccountId,
    val root: AccountId?,
    val nominator: AccountId?,
    val stateToggler: AccountId?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EditPoolFlowState

        if (poolName != other.poolName) return false
        if (poolId != other.poolId) return false
        if (!depositor.contentEquals(other.depositor)) return false
        if (!root.contentEquals(other.root)) return false
        if (!nominator.contentEquals(other.nominator)) return false
        if (!stateToggler.contentEquals(other.stateToggler)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolName.hashCode()
        result = 31 * result + poolId.hashCode()
        result = 31 * result + depositor.contentHashCode()
        result = 31 * result + root.contentHashCode()
        result = 31 * result + nominator.contentHashCode()
        result = 31 * result + stateToggler.contentHashCode()
        return result
    }
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
