package jp.co.soramitsu.common.data.storage.pin

import javax.inject.Inject
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.envelope.PinVerification
import jp.co.soramitsu.common.data.storage.envelope.WalletPinAuthority
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretKeyring

/**
 * Checks the wallet PIN without the wallet database, for emergency key access
 * from a start-up failure or recovery state (addendum U0.4). It opens the same
 * PIN wrap the PIN screen opens (or reads the legacy PIN before the keyring
 * exists) and shares that screen's lockout. It never unlocks the session.
 */
class WalletEmergencyAuthenticator internal constructor(
    private val preferences: Preferences,
    private val pinAuthority: WalletPinAuthority,
    private val clock: PinAttemptClock
) {

    @Inject
    constructor(
        preferences: Preferences,
        pinAuthority: WalletPinAuthority
    ) : this(preferences, pinAuthority, PinAttemptClock.system)

    fun authenticate(code: String): WalletEmergencyAuthentication {
        val (result, key) = authenticateWithKey(code)
        key?.close()
        return result
    }

    /**
     * Like [authenticate]; when it succeeds and a keyring exists, also the wallet
     * key SK that opens sealed values. The caller must close it.
     */
    fun authenticateWithKey(code: String): Pair<WalletEmergencyAuthentication, WalletSecretKeyring.OpenedKey?> {
        val limiter = PinAttemptLimiter(preferences, clock)
        val remaining = limiter.remainingDelayMillis()
        if (remaining > 0L) return WalletEmergencyAuthentication.Locked(remaining) to null

        val (verification, key) = pinAuthority.verifyForEmergency(code)
        return when (verification) {
            PinVerification.CORRECT -> {
                limiter.reset()
                WalletEmergencyAuthentication.Authenticated to key
            }
            PinVerification.WRONG -> {
                val delay = limiter.registerFailure()
                (if (delay > 0L) WalletEmergencyAuthentication.Locked(delay) else WalletEmergencyAuthentication.WrongPin) to null
            }
            // No PIN was ever set, or this device can no longer open it:
            // nothing stored can be shown without it.
            PinVerification.UNREADABLE -> WalletEmergencyAuthentication.Unavailable to null
        }
    }
}

sealed interface WalletEmergencyAuthentication {
    data object Authenticated : WalletEmergencyAuthentication
    data object WrongPin : WalletEmergencyAuthentication

    /** Too many wrong PINs: no PIN is checked for [remainingMillis]. */
    data class Locked(val remainingMillis: Long) : WalletEmergencyAuthentication

    /** No PIN can be read on this device, so nothing can be revealed. */
    data object Unavailable : WalletEmergencyAuthentication
}
