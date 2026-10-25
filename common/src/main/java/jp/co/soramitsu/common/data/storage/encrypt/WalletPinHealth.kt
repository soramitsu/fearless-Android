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

/** Health is published only after the device key authenticates independently. */
object WalletPinHealth {
    private val mutableHealth = MutableStateFlow<PinHealth?>(null)
    val health: StateFlow<PinHealth?> = mutableHealth.asStateFlow()

    internal fun replace(health: PinHealth?) {
        mutableHealth.value = health
    }
}
