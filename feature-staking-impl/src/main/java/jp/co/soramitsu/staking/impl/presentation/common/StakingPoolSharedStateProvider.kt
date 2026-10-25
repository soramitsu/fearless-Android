package jp.co.soramitsu.staking.impl.presentation.common

import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import kotlinx.coroutines.flow.MutableStateFlow

class StakingPoolSharedStateProvider {
    private val preparationLock = Any()
    private var preparationOwner: Any? = null
    val mainState by lazy { StakingPoolSharedState<StakingPoolState>(preparationLock) }

    class PoolPreparation internal constructor(
        internal val owner: Any,
        internal val main: StakingPoolSharedState.Snapshot<StakingPoolState>,
        val walletEpoch: Long,
        val selection: jp.co.soramitsu.staking.api.data.StakingSelectionRevision.Snapshot
    )

    /** Captured synchronously at the user's action, before queued or suspended preparation. */
    fun beginPoolPreparation(selection: jp.co.soramitsu.staking.api.data.StakingSelectionRevision.Snapshot): PoolPreparation? =
        synchronized(preparationLock) {
            val owner = Any()
            preparationOwner = owner
            val epoch = runCatching { jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.snapshot() }.getOrNull()
                ?: return@synchronized null
            PoolPreparation(owner, mainState.capture(), epoch, selection)
        }

    fun isPoolPreparationCurrent(request: PoolPreparation): Boolean = synchronized(preparationLock) {
        preparationOwner === request.owner && mainState.isCurrent(request.main) &&
            jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.isCurrent(request.walletEpoch)
    }

    /** Conditional publication and request ownership use the same lock as every main-state mutation. */
    fun publishPoolPreparation(
        request: PoolPreparation,
        state: StakingPoolState,
        selectionRevision: jp.co.soramitsu.staking.api.data.StakingSelectionRevision
    ): Boolean = synchronized(preparationLock) {
        val chain = state.chain ?: return@synchronized false
        val asset = state.asset ?: return@synchronized false
        if (!isPoolPreparationCurrent(request) || state.walletSelectionEpoch != request.walletEpoch ||
            !selectionRevision.matches(request.selection, chain.id, asset.token.configuration.id)) return@synchronized false
        val receipt = mainState.replaceIfCurrent(request.main, state) ?: return@synchronized false
        preparationOwner === request.owner && mainState.isCurrent(receipt) &&
            jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.isCurrent(request.walletEpoch) &&
            selectionRevision.matches(request.selection, chain.id, asset.token.configuration.id)
    }
    // Main, join, and execution revocation share one lock, including synchronous StateFlow observers.
    val joinFlowState by lazy { StakingPoolSharedState<StakingPoolJoinFlowState>(preparationLock) }

    /** Creates local review authority without remote capability or configuration dependencies. */
    fun joinExecutionReview(context: StakingPoolSharedState.Snapshot<StakingPoolState>): JoinPoolExecutionReview =
        JoinPoolExecutionReview(preparationLock, mainState, joinFlowState, context)
    val createFlowState by lazy { StakingPoolSharedState<StakingPoolCreateFlowState>(preparationLock) }

    /** Captures main context and create draft under their common publication lock. */
    fun createConfirmationReview(): CreatePoolConfirmationReview = synchronized(preparationLock) {
        CreatePoolConfirmationReview(preparationLock, mainState, createFlowState)
    }
    /** Shares the main publication lock with the local lifetime of setup and welcome screens. */
    fun poolContextReview(context: StakingPoolSharedState.Snapshot<StakingPoolState>): PoolContextReview =
        PoolContextReview(preparationLock, mainState, context)

    /** Retains an editable current draft or conditionally replaces its original empty/stale publication. */
    fun prepareCreateDraft(
        context: StakingPoolSharedState.Snapshot<StakingPoolState>,
        original: StakingPoolSharedState.Snapshot<StakingPoolCreateFlowState>
    ): StakingPoolSharedState.Snapshot<StakingPoolCreateFlowState>? = synchronized(preparationLock) {
        if (!isPoolContextCurrent(context) || !createFlowState.isCurrent(original)) return@synchronized null
        jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.runIfCurrentOrNull(
            requireNotNull(context.value?.walletSelectionEpoch)
        ) {
            val receipt = if (original.value?.context?.let(::isPoolContextCurrent) == true) {
                original.takeIf(createFlowState::isEditable)
            } else {
                createFlowState.replaceIfCurrent(original, StakingPoolCreateFlowState(context = context))
            }
            // Publication can synchronously resume observers that replace the draft or context.
            CreateDraftReceipt(receipt?.takeIf { createFlowState.isCurrent(it) && isPoolContextCurrent(context) })
        }?.snapshot
    }

    private class CreateDraftReceipt(val snapshot: StakingPoolSharedState.Snapshot<StakingPoolCreateFlowState>?)

    val manageState by lazy { StakingPoolSharedState<StakingPoolManageFlowState>(preparationLock) }

    /** Captures the original member draft and its main context under the common publication lock. */
    fun manageConfirmationReview(): PoolManageConfirmationReview = synchronized(preparationLock) {
        PoolManageConfirmationReview(preparationLock, mainState, manageState)
    }

    /** Amount entry keeps the original member draft even when the global selection changes. */
    fun managePreparationReview(): PoolManagePreparationReview = synchronized(preparationLock) {
        PoolManagePreparationReview(preparationLock, mainState, manageState)
    }

    /** An old pool observer cannot publish another wallet's management draft or replace an active claim. */
    fun publishManageState(
        context: StakingPoolSharedState.Snapshot<StakingPoolState>,
        state: StakingPoolManageFlowState
    ): Boolean = synchronized(preparationLock) {
        if (state.context !== context || !isPoolContextCurrent(context)) return@synchronized false
        jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.runIfCurrentOrNull(
            requireNotNull(context.value?.walletSelectionEpoch)
        ) {
            val receipt = manageState.replaceIfCurrent(manageState.capture(), state)
            receipt != null && manageState.isCurrent(receipt) && isPoolContextCurrent(context)
        } == true
    }
    val selectValidatorsState by lazy { StakingPoolSharedState<SelectValidatorFlowState>() }
    val selectedValidatorsState by lazy { StakingPoolSharedState<SelectedValidatorsFlowState>() }
    val editPoolState by lazy { StakingPoolSharedState<EditPoolFlowState>() }
    val poolsCache: MutableStateFlow<Map<Int, PoolInfo>> = MutableStateFlow(emptyMap())

    /** A join draft retains the exact prepared context; returning to an equal wallet/state cannot revive it. */
    fun isJoinContextCurrent(context: StakingPoolSharedState.Snapshot<StakingPoolState>): Boolean = isPoolContextCurrent(context)

    fun isPoolContextCurrent(context: StakingPoolSharedState.Snapshot<StakingPoolState>): Boolean =
        mainState.isCurrent(context) && context.value?.walletSelectionEpoch?.let {
            jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.isCurrent(it)
        } == true

    /** Setup may edit only its original draft and the main context from which that draft began. */
    fun publishCreateSetup(
        draft: StakingPoolSharedState.Snapshot<StakingPoolCreateFlowState>,
        state: StakingPoolCreateFlowState
    ): Boolean = synchronized(preparationLock) {
        val context = draft.value?.context ?: return@synchronized false
        if (state.context !== context || !isPoolContextCurrent(context)) return@synchronized false
        jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.runIfCurrentOrNull(
            requireNotNull(context.value?.walletSelectionEpoch)
        ) {
            createFlowState.setIfCurrent(draft, state) && isPoolContextCurrent(context)
        } == true
    }

    val requireMainState: StakingPoolState
        get() = requireNotNull(mainState.get())
    val requireCreateState: StakingPoolCreateFlowState
        get() = requireNotNull(createFlowState.get())
    val requireJoinState: StakingPoolJoinFlowState
        get() = requireNotNull(joinFlowState.get())
    val requireSelectValidatorsState: SelectValidatorFlowState
        get() = requireNotNull(selectValidatorsState.get())
    val requireSelectedValidatorsState: SelectedValidatorsFlowState
        get() = requireNotNull(selectedValidatorsState.get())
    val requireEditPoolState: EditPoolFlowState
        get() = requireNotNull(editPoolState.get())
    val requireManageState: StakingPoolManageFlowState
        get() = requireNotNull(manageState.get())
}
