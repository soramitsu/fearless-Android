package jp.co.soramitsu.common.data.storage.pin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Whether this process has been unlocked with the wallet PIN, or a biometric bound to it, since it
 * started or was last re-locked. A new process starts locked.
 *
 * dApp requests (WalletConnect, TON Connect) wait while the wallet is locked or in view-only mode,
 * so no signing screen can open over the PIN screen.
 */
object WalletSessionLock {
    private val mutableLocked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = mutableLocked.asStateFlow()

    /** True while the PIN screen guards the wallet, or while only view-only access was granted. */
    fun isLocked(): Boolean = mutableLocked.value || WalletViewOnlyAccess.isEnabled()

    fun lock() {
        mutableLocked.value = true
    }

    fun unlock() {
        mutableLocked.value = false
    }

    /** Suspends until the wallet is unlocked and no longer view-only. */
    suspend fun awaitUnlocked() {
        if (!isLocked()) return
        combine(mutableLocked, WalletViewOnlyAccess.enabled) { locked, viewOnly -> locked || viewOnly }
            .first { blocked -> !blocked }
    }
}
