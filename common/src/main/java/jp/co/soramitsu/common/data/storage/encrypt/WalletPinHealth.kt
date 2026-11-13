package jp.co.soramitsu.common.data.storage.encrypt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PinHealth {
    ABSENT,
    HEALTHY,
    UNREADABLE;

    companion object {
        fun assess(exists: Boolean, plaintext: String?): PinHealth = when {
            !exists -> ABSENT
            plaintext != null && plaintext.length == 6 && plaintext.all { it in '0'..'9' } -> HEALTHY
            else -> UNREADABLE
        }
    }
}

/**
 * Health is published only after the device key authenticates independently.
 *
 * Two sources feed it: the device key publishes the legacy `pin_code` health on
 * every load, and the wallet keyring publishes its PIN wrap's health once a
 * keyring exists. The keyring decides when it has spoken, because the migrated
 * PIN lives only in its wrap and `pin_code` is then gone.
 */
object WalletPinHealth {
    private val lock = Any()
    private var deviceKeyHealth: PinHealth? = null
    private var keyringHealth: PinHealth? = null
    private val mutableHealth = MutableStateFlow<PinHealth?>(null)
    val health: StateFlow<PinHealth?> = mutableHealth.asStateFlow()

    internal fun replace(health: PinHealth?) = synchronized(lock) {
        deviceKeyHealth = health
        publish()
    }

    /** Null when no keyring exists; otherwise HEALTHY or UNREADABLE for its PIN wrap. */
    internal fun replaceKeyring(health: PinHealth?) = synchronized(lock) {
        keyringHealth = health
        publish()
    }

    /** Tests share this process-wide state; they reset it between cases. */
    @androidx.annotation.VisibleForTesting
    fun resetForTest() = synchronized(lock) {
        deviceKeyHealth = null
        keyringHealth = null
        publish()
    }

    private fun publish() {
        mutableHealth.value = deviceKeyHealth?.let { keyringHealth ?: it }
    }
}
