package jp.co.soramitsu.staking.impl.presentation.common

import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.api.domain.model.RoleInPool
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

data class StakingPoolJoinFlowState(
    val amount: BigDecimal? = null,
    val selectedPool: PoolInfo? = null,
    val context: StakingPoolSharedState.Snapshot<StakingPoolState>
)

data class StakingPoolCreateFlowState(
    val poolName: String? = null,
    val amountInPlanks: BigInteger? = null,
    val poolId: Int? = null,
    val nominatorAddress: String? = null,
    val stateTogglerAddress: String? = null,
    val context: StakingPoolSharedState.Snapshot<StakingPoolState>
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
    val amountInPlanks: BigInteger? = null,
    val userRole: RoleInPool? = null,
    val context: StakingPoolSharedState.Snapshot<StakingPoolState>
)

data class StakingPoolState(
    val chain: Chain? = null,
    val asset: Asset? = null,
    val chainAsset: CoreAsset? = null,
    val address: String? = null,
    val amount: BigDecimal? = null,
    val walletSelectionEpoch: Long? = runCatching { jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.snapshot() }.getOrNull()
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
    val newPoolName: String? = null,
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

class StakingPoolSharedState<T>(private val lock: Any = Any()) {
    /** Identifies one publication, including an equal value published again later. */
    class Snapshot<T> internal constructor(
        val value: T?,
        internal val revision: Any,
        internal val lineage: Any,
        internal val editGeneration: Any
    ) {
        /** Legitimate back-editing retains the flow; replacing or completing it starts another lineage. */
        fun isSameFlowAs(other: Snapshot<T>): Boolean = lineage === other.lineage
    }

    /** Only the attempt that acquired this claim may release or consume its publication. */
    class Claim<T> internal constructor(internal val snapshot: Snapshot<T>, internal val owner: Any)

    private data class Publication<T>(
        val value: T?,
        val revision: Any = Any(),
        val claimOwner: Any? = null,
        val lineage: Any = Any(),
        val editGeneration: Any = Any(),
        val consumed: Boolean = false
    )

    private val publication = MutableStateFlow(Publication<T>(null))
    val process = publication.map { it.value }.distinctUntilChanged()
    val submissionInProgress = publication.map { it.claimOwner != null }.distinctUntilChanged()

    /** Captures the value and its revision together for a review that may outlive later edits. */
    fun capture(): Snapshot<T> = synchronized(lock) {
        val current = publication.value
        Snapshot(current.value, current.revision, current.lineage, current.editGeneration)
    }

    /** Checks publication ownership without reserving or cancelling a submission. */
    fun isCurrent(snapshot: Snapshot<T>): Boolean = synchronized(lock) { snapshot.revision === publication.value.revision }

    /** Checks both the reviewed publication and the exact in-flight attempt. */
    fun owns(claim: Claim<T>): Boolean = synchronized(lock) {
        val current = publication.value
        current.revision === claim.snapshot.revision && current.claimOwner === claim.owner
    }

    fun set(newState: T) = synchronized(lock) {
        publication.value = Publication(newState)
    }

    /** Publishes a new flow only while its original context still owns this state, including initial null. */
    fun replaceIfCurrent(snapshot: Snapshot<T>, newState: T): Snapshot<T>? = synchronized(lock) {
        val current = publication.value
        if (current.revision !== snapshot.revision || current.claimOwner != null) return@synchronized null
        val replacement = Publication(newState)
        val receipt = Snapshot(replacement.value, replacement.revision, replacement.lineage, replacement.editGeneration)
        publication.value = replacement
        receipt.takeIf { publication.value.revision === replacement.revision }
    }

    fun get(): T? = publication.value.value

    fun mutate(mutation: (T?) -> T) = synchronized(lock) {
        set(mutation(get()))
    }

    fun complete() = synchronized(lock) {
        publication.value = Publication(null)
    }

    /** Acquires one attempt across every confirmation using the same reviewed publication. */
    fun tryClaim(snapshot: Snapshot<T>): Claim<T>? = synchronized(lock) {
        val current = publication.value
        if (current.revision !== snapshot.revision || current.value == null || current.claimOwner != null || current.consumed) return@synchronized null
        val owner = Any()
        publication.value = current.copy(claimOwner = owner, editGeneration = Any())
        Claim(snapshot, owner)
    }

    /** Releases only this unsuccessful attempt; a newer attempt or draft remains untouched. */
    fun release(claim: Claim<T>) = synchronized(lock) {
        val current = publication.value
        if (current.revision === claim.snapshot.revision && current.claimOwner === claim.owner) {
            publication.value = current.copy(claimOwner = null)
        }
    }

    /** Keeps readable review fields after known output, without permitting another claim on them. */
    fun consumeKeepingValue(claim: Claim<T>): Snapshot<T>? = synchronized(lock) {
        val current = publication.value
        if (current.revision !== claim.snapshot.revision || current.claimOwner !== claim.owner) return@synchronized null
        val completed = Publication(current.value, consumed = true)
        val receipt = Snapshot(completed.value, completed.revision, completed.lineage, completed.editGeneration)
        publication.value = completed
        receipt
    }

    /** Records known success before a cancellable dispatch-back and returns its exact completion revision. */
    fun consume(claim: Claim<T>): Snapshot<T>? = synchronized(lock) {
        val current = publication.value
        if (current.revision !== claim.snapshot.revision || current.claimOwner !== claim.owner) return@synchronized null
        val completed = Publication<T>(null)
        val receipt = Snapshot(completed.value, completed.revision, completed.lineage, completed.editGeneration)
        // A resumed observer may publish another draft reentrantly. Never capture that newer revision.
        publication.value = completed
        receipt
    }

    /** Rejects pending and previously interrupted edits even after a failed submission releases its claim. */
    fun isEditable(snapshot: Snapshot<T>): Boolean = synchronized(lock) {
        val current = publication.value
        current.value != null && current.revision === snapshot.revision &&
            current.editGeneration === snapshot.editGeneration && current.claimOwner == null && !current.consumed
    }

    /** Publishes a validated edit only while its draft is current and has no unresolved submission. */
    fun setIfCurrent(snapshot: Snapshot<T>, newState: T): Boolean = editIfCurrent(snapshot, newState) != null

    /** Returns only this edit's receipt, never a newer publication from a synchronous observer. */
    fun editIfCurrent(snapshot: Snapshot<T>, newState: T): Snapshot<T>? = synchronized(lock) {
        val current = publication.value
        if (!isEditable(snapshot)) return@synchronized null
        val replacement = Publication(newState, lineage = current.lineage)
        val receipt = Snapshot(replacement.value, replacement.revision, replacement.lineage, replacement.editGeneration)
        publication.value = replacement
        receipt.takeIf { isEditable(it) }
    }
}
