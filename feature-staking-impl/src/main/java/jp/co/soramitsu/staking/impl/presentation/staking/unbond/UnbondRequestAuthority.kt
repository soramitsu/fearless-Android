package jp.co.soramitsu.staking.impl.presentation.staking.unbond

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationPayload
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

/** One screen's observed source and input authority, shared with its in-process confirmation. */
class UnbondRequestAuthority(private val owner: Job, private val selection: StakingSelectionRevision) {
    private val lock = Any()
    private var active = true
    private var revision = 0L
    private var asset: Asset? = null
    private var state: StakingState? = null
    private var assetEpoch: Long? = null
    private var stateEpoch: Long? = null
    private var assetSelection: StakingSelectionRevision.Snapshot? = null
    private var stateSelection: StakingSelectionRevision.Snapshot? = null
    private var attempt: Attempt? = null
    private var submittedRevision: Long? = null
    private val mutableChanges = MutableStateFlow(0L)
    val changes = mutableChanges.asStateFlow()
    private val text = MutableStateFlow("1")

    /** All public input writes serialize with the final signing/submission primitive. */
    val amountInput: MutableStateFlow<String> = object : MutableStateFlow<String> by text {
        override var value: String
            get() = text.value
            set(value) { synchronized(lock) { if (text.value != value) { changed(); text.value = value } } }
        override fun compareAndSet(expect: String, update: String): Boolean = synchronized(lock) {
            if (text.value != expect) false else { value = update; true }
        }
        override suspend fun emit(value: String) { this.value = value }
        override fun tryEmit(value: String): Boolean { this.value = value; return true }
    }

    init { owner.invokeOnCompletion { revoke() } }

    private fun changed() {
        revision++
        attempt = null
        mutableChanges.value = revision
    }

    fun assetChanged(value: Asset?) = synchronized(lock) {
        val epoch = runCatching { WalletSelectionEpoch.snapshot() }.getOrNull()
        val selected = selection.capture()
        if (asset !== value || assetEpoch != epoch || assetSelection !== selected) {
            asset = value; assetEpoch = epoch; assetSelection = selected; changed()
        }
    }

    fun stateChanged(value: StakingState?) = synchronized(lock) {
        val epoch = runCatching { WalletSelectionEpoch.snapshot() }.getOrNull()
        val selected = selection.capture()
        if (state !== value || stateEpoch != epoch || stateSelection !== selected) {
            state = value; stateEpoch = epoch; stateSelection = selected; changed()
        }
    }

    fun revoke() = synchronized(lock) {
        if (active) { active = false; changed() }
    }

    fun capture(collator: String?): Context? = synchronized(lock) {
        val currentAsset = asset ?: return null
        val currentState = state ?: return null
        val amount = parseAmount(text.value, currentAsset.token.configuration.precision) ?: return null
        val type = when (currentState) {
            is StakingState.Stash -> StakingType.RELAYCHAIN
            is StakingState.Parachain -> StakingType.PARACHAIN
            else -> return null
        }
        if (currentAsset.chainId != currentState.chain.id ||
            !currentAsset.accountId.contentEquals(currentState.accountId)) return null
        if (type == StakingType.RELAYCHAIN && currentAsset.bondedInPlanks?.signum()?.let { it >= 0 } != true) return null
        if (type == StakingType.PARACHAIN && collator == null) return null
        val epoch = runCatching { WalletSelectionEpoch.snapshot() }.getOrNull() ?: return null
        val snapshot = selection.capture()
        if (assetEpoch != epoch || stateEpoch != epoch || assetSelection !== snapshot || stateSelection !== snapshot) return null
        Context(this, revision, currentAsset, currentState, text.value, amount, collator, epoch, snapshot, type)
            .takeIf { current(it) }
    }

    companion object {
        /** Bound decimal work before exact conversion; fractional planks cannot acquire a review. */
        internal fun parseAmount(input: String, precision: Int): BigDecimal? {
            if (precision !in 0..255 || input.length > 4096) return null
            val amount = input.toBigDecimalOrNull() ?: return null
            if (amount.signum() < 0 || amount.precision() > 4096 || amount.scale() !in -4096..4096) return null
            val plankDigits = amount.precision().toLong() - amount.scale() + precision
            if (plankDigits > 4096) return null
            return try {
                amount.scaleByPowerOfTen(precision).toBigIntegerExact()
                amount
            } catch (_: ArithmeticException) { null }
        }
    }

    private fun current(context: Context): Boolean = active && owner.isActive && revision == context.revision &&
        asset === context.asset && state === context.stash && text.value == context.input &&
        selection.matchesAction(context.selection, context.type, context.asset.chainId, context.asset.token.configuration.id) &&
        WalletSelectionEpoch.isCurrent(context.walletEpoch)

    class Context internal constructor(
        internal val authority: UnbondRequestAuthority,
        internal val revision: Long,
        val asset: Asset,
        val stash: StakingState,
        internal val input: String,
        val amount: BigDecimal,
        val collator: String?,
        internal val walletEpoch: Long,
        internal val selection: StakingSelectionRevision.Snapshot,
        internal val type: StakingType
    ) {
        val changes get() = authority.changes
        val selectionChanges get() = authority.selection.changes
        val walletId get() = asset.metaId
        internal var walletAdmitted = false
        /** Caller obtains this wallet from the selected-account repository while the context remains current. */
        internal fun admitWallet(wallet: MetaAccount): Boolean = synchronized(authority.lock) {
            if (!authority.current(this) || wallet.id != walletId ||
                wallet.accountId(stash.chain)?.contentEquals(asset.accountId) != true) return false
            walletAdmitted = true
            true
        }
        fun isCurrent(): Boolean = synchronized(authority.lock) { walletAdmitted && authority.current(this) }
        fun requireCurrent() { if (!isCurrent()) throw CancellationException("Unbond review changed") }
        fun claim(job: Job, review: Confirmation? = null): Attempt? = synchronized(authority.lock) {
            if (!walletAdmitted || !authority.current(this) || !job.isActive ||
                authority.submittedRevision == revision || review?.isCurrent() == false) return null
            Attempt(authority, this, job, review).also { authority.attempt = it }
        }
        fun confirmation(payload: UnbondValidationPayload): Confirmation = synchronized(authority.lock) {
            requireCurrent()
            check(payload.asset === asset && payload.stash === stash && payload.collatorAddress == collator)
            require(payload.amount.compareTo(amount) == 0 && payload.fee.signum() >= 0) { "Unbond amount requires a new fee review" }
            Confirmation(this, payload)
        }
    }

    /** Deliberately not parcelable: restored or legacy confirmation must obtain a new review. */
    class Confirmation internal constructor(val context: Context, val payload: UnbondValidationPayload) {
        private var attached: Job? = null
        private var revoked = false
        fun attach(job: Job): Boolean = synchronized(context.authority.lock) {
            if (attached != null || revoked || !context.isCurrent() || !job.isActive) return false
            attached = job
            job.invokeOnCompletion { revoke() }
            true
        }
        fun isCurrent(): Boolean = synchronized(context.authority.lock) {
            !revoked && attached?.isActive == true && context.isCurrent()
        }
        fun revoke() = synchronized(context.authority.lock) { revoked = true }
    }

    class Attempt internal constructor(
        private val authority: UnbondRequestAuthority,
        val context: Context,
        private val job: Job,
        private val review: Confirmation?
    ) {
        private var admitted = false
        fun isCurrent(): Boolean = synchronized(authority.lock) {
            authority.attempt === this && context.walletAdmitted && authority.current(context) && job.isActive && review?.isCurrent() != false
        }
        fun requireCurrent() { if (!isCurrent()) throw CancellationException("Unbond attempt changed") }
        fun release() = synchronized(authority.lock) { if (authority.attempt === this) authority.attempt = null }
        /** A warning may change the input, never the amount authorized by its old fee. */
        fun requestAmountReview(amount: BigDecimal): Boolean = synchronized(authority.lock) {
            synchronized(authority.selection) {
                WalletSelectionEpoch.runIfCurrentOrNull(context.walletEpoch) review@{
                    if (!isCurrent() || amount.precision() > 4096 || amount.scale() !in -4096..4096 ||
                        amount.compareTo(context.amount) == 0) return@review false
                    val input = amount.toString()
                    if (UnbondRequestAuthority.parseAmount(input, context.asset.token.configuration.precision) == null) return@review false
                    authority.amountInput.value = input
                    true
                } ?: false
            }
        }

        fun authorize(intent: String, execution: Job): MutationExecutionGuard {
            requireCurrent()
            return object : MutationExecutionGuard {
                override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T =
                    synchronized(authority.lock) {
                        // Observers of wallet/selection signals dispatch on regular Main, never inline under these monitors.
                        synchronized(authority.selection) {
                            WalletSelectionEpoch.runIfCurrent(context.walletEpoch) {
                                job.ensureActive()
                                execution.ensureActive()
                                check(intentSha256 == intent) { "Unbond transaction intent changed" }
                                requireCurrent()
                                check(!admitted) { "Unbond submission already admitted" }
                                operation()
                            }
                        }
                    }
                override fun <T> runSubmissionIfAuthorized(intentSha256: String, operation: () -> T): T {
                    runIfAuthorized(intentSha256) {
                        admitted = true
                        authority.submittedRevision = context.revision
                    }
                    // The physical writer owns frozen bytes; blocking output must not retain UI/selection locks.
                    return operation()
                }
            }
        }
    }
}
