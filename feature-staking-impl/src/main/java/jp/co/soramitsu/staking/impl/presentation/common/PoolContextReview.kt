package jp.co.soramitsu.staking.impl.presentation.common

import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch

/** Synchronous presentation authority for a screen's original pool context and lifetime. */
class PoolContextReview internal constructor(
    private val lock: Any,
    private val mainState: StakingPoolSharedState<StakingPoolState>,
    private val context: StakingPoolSharedState.Snapshot<StakingPoolState>
) {
    private var active = true

    fun isCurrent(): Boolean = synchronized(lock) {
        active && mainState.isCurrent(context) &&
            context.value?.walletSelectionEpoch?.let(WalletSelectionEpoch::isCurrent) == true
    }

    /** Departure remains final even when the original wallet or equal state returns. */
    fun revoke(): Boolean = synchronized(lock) {
        val wasActive = active
        active = false
        wasActive
    }

    /** No suspended lookup or output belongs inside this synchronous publication boundary. */
    fun <T : Any> withCurrent(operation: () -> T): T? = synchronized(lock) {
        if (!isCurrent()) return@synchronized null
        WalletSelectionEpoch.runIfCurrentOrNull(requireNotNull(context.value?.walletSelectionEpoch), operation)
    }
}
