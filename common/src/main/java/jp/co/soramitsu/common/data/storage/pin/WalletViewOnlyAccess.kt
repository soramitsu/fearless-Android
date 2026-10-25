package jp.co.soramitsu.common.data.storage.pin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WalletPinRecoveryRequiredException : jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException("Recover the wallet PIN before sending, signing, or backing up keys")

/** A device credential may reveal balances, but never wallet signing material. */
object WalletViewOnlyAccess {
    private val mutableEnabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    fun enter() { mutableEnabled.value = true }
    fun pinResetCompleted() { mutableEnabled.value = false }
    fun isEnabled(): Boolean = mutableEnabled.value
    fun requireSigningAccess() {
        if (isEnabled()) throw WalletPinRecoveryRequiredException()
    }
}
