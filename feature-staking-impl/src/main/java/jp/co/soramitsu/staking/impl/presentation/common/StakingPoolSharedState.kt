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
    val poolId: BigInteger,
    val initialPoolName: String,
    val depositor: AccountId,
    val initialRoot: AccountId?,
    val initialNominator: AccountId?,
    val initialStateToggler: AccountId?,
    val newPoolName: String?,
    val newRoot: AccountId? = null,
    val newNominator: AccountId? = null,
    val newStateToggler: AccountId? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EditPoolFlowState

        if (poolId != other.poolId) return false
        if (initialPoolName != other.initialPoolName) return false
        if (!depositor.contentEquals(other.depositor)) return false
        if (initialRoot != null) {
            if (other.initialRoot == null) return false
            if (!initialRoot.contentEquals(other.initialRoot)) return false
        } else if (other.initialRoot != null) return false
        if (initialNominator != null) {
            if (other.initialNominator == null) return false
            if (!initialNominator.contentEquals(other.initialNominator)) return false
        } else if (other.initialNominator != null) return false
        if (initialStateToggler != null) {
            if (other.initialStateToggler == null) return false
            if (!initialStateToggler.contentEquals(other.initialStateToggler)) return false
        } else if (other.initialStateToggler != null) return false
        if (newPoolName != other.newPoolName) return false
        if (newRoot != null) {
            if (other.newRoot == null) return false
            if (!newRoot.contentEquals(other.newRoot)) return false
        } else if (other.newRoot != null) return false
        if (newNominator != null) {
            if (other.newNominator == null) return false
            if (!newNominator.contentEquals(other.newNominator)) return false
        } else if (other.newNominator != null) return false
        if (newStateToggler != null) {
            if (other.newStateToggler == null) return false
            if (!newStateToggler.contentEquals(other.newStateToggler)) return false
        } else if (other.newStateToggler != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId.hashCode()
        result = 31 * result + initialPoolName.hashCode()
        result = 31 * result + depositor.contentHashCode()
        result = 31 * result + (initialRoot?.contentHashCode() ?: 0)
        result = 31 * result + (initialNominator?.contentHashCode() ?: 0)
        result = 31 * result + (initialStateToggler?.contentHashCode() ?: 0)
        result = 31 * result + (newPoolName?.hashCode() ?: 0)
        result = 31 * result + (newRoot?.contentHashCode() ?: 0)
        result = 31 * result + (newNominator?.contentHashCode() ?: 0)
        result = 31 * result + (newStateToggler?.contentHashCode() ?: 0)
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
