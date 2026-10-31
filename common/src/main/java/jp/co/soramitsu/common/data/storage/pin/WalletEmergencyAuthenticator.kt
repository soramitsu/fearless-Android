package jp.co.soramitsu.common.data.storage.pin

import java.security.MessageDigest
import javax.inject.Inject
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletMasterKeyAttestation
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth

/**
 * Checks the wallet PIN without the wallet database, for emergency key access
 * from a start-up failure or recovery state (addendum U0.4). It reads the PIN
 * from the same encrypted preference the PIN screen writes and shares that
 * screen's lockout.
 */
class WalletEmergencyAuthenticator internal constructor(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences,
    private val clock: PinAttemptClock
) {

    @Inject
    constructor(
        preferences: Preferences,
        encryptedPreferences: EncryptedPreferences
    ) : this(preferences, encryptedPreferences, PinAttemptClock.system)

    fun authenticate(code: String): WalletEmergencyAuthentication {
        val limiter = PinAttemptLimiter(preferences, clock)
        val remaining = limiter.remainingDelayMillis()
        if (remaining > 0L) return WalletEmergencyAuthentication.Locked(remaining)

        val stored = try {
            encryptedPreferences.getDecryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY)
        } catch (_: ClassCastException) {
            return WalletEmergencyAuthentication.Unavailable
        }
        if (PinHealth.assess(true, stored) != PinHealth.HEALTHY) {
            // No PIN was ever set, or this device can no longer decrypt it:
            // nothing stored can be shown without it.
            return WalletEmergencyAuthentication.Unavailable
        }
        val matches = MessageDigest.isEqual(
            checkNotNull(stored).toByteArray(Charsets.UTF_8),
            code.toByteArray(Charsets.UTF_8)
        )
        return if (matches) {
            limiter.reset()
            WalletEmergencyAuthentication.Authenticated
        } else {
            val delay = limiter.registerFailure()
            if (delay > 0L) WalletEmergencyAuthentication.Locked(delay) else WalletEmergencyAuthentication.WrongPin
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
