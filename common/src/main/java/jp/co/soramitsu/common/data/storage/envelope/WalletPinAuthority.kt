package jp.co.soramitsu.common.data.storage.envelope

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.storage.PinPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.PinResetSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.WalletPinHealth

/** The result of one PIN check. The wrong-PIN limiter stays with the caller. */
enum class PinVerification {
    CORRECT,
    WRONG,

    /** Nothing can match: no PIN can be read or opened here, so this is no wrong guess. */
    UNREADABLE
}

/**
 * The only place the wallet PIN is checked or stored (4.3.2).
 *
 * Once a wallet keyring exists, opening its PIN wrap is the PIN check and
 * puts SK in [WalletSecretSession]; the legacy `pin_code` preference is then
 * kept only until the unlock migration deletes it. Before that, a correct
 * legacy PIN creates the keyring, so the first unlock on this build moves the
 * PIN inside the encryption. With sealing switched off, a device that has no
 * keyring keeps working exactly as 4.3.1 did.
 */
@Singleton
class WalletPinAuthority @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val keyring: WalletSecretKeyring,
    private val sealer: WalletSecretSealer
) {

    /** ABSENT (first run), HEALTHY, or UNREADABLE (recovery needed), and publishes it. */
    @Synchronized
    fun health(): PinHealth {
        encryptedPreferences.requireDurableStorageHealthy()
        val health = when (keyring.pinWrapState()) {
            WalletSecretKeyring.PinWrapState.USABLE -> PinHealth.HEALTHY
            WalletSecretKeyring.PinWrapState.UNUSABLE -> PinHealth.UNREADABLE
            WalletSecretKeyring.PinWrapState.ABSENT -> null
        }
        WalletPinHealth.replaceKeyring(health)
        return health ?: encryptedPreferences.getPinHealth()
    }

    /**
     * Checks [pin]. On CORRECT, SK is open in [WalletSecretSession]: from the
     * PIN wrap, or from a keyring this first unlock just created.
     */
    @Synchronized
    fun verify(pin: String): PinVerification {
        when (keyring.pinWrapState()) {
            WalletSecretKeyring.PinWrapState.USABLE -> return when (val opening = openWrap(pin)) {
                is WalletSecretKeyring.PinOpening.Opened -> {
                    try {
                        openSession(opening.key, WalletSecretSession.Origin.PIN_WRAP)
                    } finally {
                        opening.key.close()
                    }
                    PinVerification.CORRECT
                }

                WalletSecretKeyring.PinOpening.WrongPin -> PinVerification.WRONG
                WalletSecretKeyring.PinOpening.Unusable -> {
                    WalletPinHealth.replaceKeyring(PinHealth.UNREADABLE)
                    PinVerification.UNREADABLE
                }
            }

            // A keyring whose wrap can never open: only a backup proof can reach SK now.
            WalletSecretKeyring.PinWrapState.UNUSABLE -> return PinVerification.UNREADABLE
            WalletSecretKeyring.PinWrapState.ABSENT -> Unit
        }
        val stored = try {
            encryptedPreferences.getDecryptedString(PinPreferenceSnapshot.PIN_KEY)
        } catch (_: ClassCastException) {
            return PinVerification.UNREADABLE
        }
        // The stored PIN is judged before the typed one: an unreadable PIN is never a wrong guess.
        if (PinHealth.assess(true, stored) != PinHealth.HEALTHY) return PinVerification.UNREADABLE
        if (!isWalletPin(pin) ||
            !MessageDigest.isEqual(checkNotNull(stored).toByteArray(Charsets.US_ASCII), pin.toByteArray(Charsets.US_ASCII))
        ) {
            return PinVerification.WRONG
        }
        // The first unlock on this build puts SK under the PIN that was just proved.
        createKeyringQuietly(pin)
        return PinVerification.CORRECT
    }

    /**
     * Checks [pin] for emergency key access without the wallet database and
     * without unlocking the session: on CORRECT the caller gets SK (null when no
     * keyring exists yet) to open sealed values, and must close it. It never
     * creates a keyring.
     */
    @Synchronized
    fun verifyForEmergency(pin: String): Pair<PinVerification, WalletSecretKeyring.OpenedKey?> {
        when (keyring.pinWrapState()) {
            WalletSecretKeyring.PinWrapState.USABLE -> return when (val opening = openWrap(pin)) {
                is WalletSecretKeyring.PinOpening.Opened -> PinVerification.CORRECT to opening.key
                WalletSecretKeyring.PinOpening.WrongPin -> PinVerification.WRONG to null
                WalletSecretKeyring.PinOpening.Unusable -> PinVerification.UNREADABLE to null
            }

            WalletSecretKeyring.PinWrapState.UNUSABLE -> return PinVerification.UNREADABLE to null
            WalletSecretKeyring.PinWrapState.ABSENT -> Unit
        }
        val stored = try {
            encryptedPreferences.getDecryptedString(PinPreferenceSnapshot.PIN_KEY)
        } catch (_: ClassCastException) {
            return PinVerification.UNREADABLE to null
        }
        if (PinHealth.assess(true, stored) != PinHealth.HEALTHY) return PinVerification.UNREADABLE to null
        val matches = isWalletPin(pin) &&
            MessageDigest.isEqual(checkNotNull(stored).toByteArray(Charsets.US_ASCII), pin.toByteArray(Charsets.US_ASCII))
        return (if (matches) PinVerification.CORRECT else PinVerification.WRONG) to null
    }

    /**
     * Stores the first PIN (onboarding) or a changed PIN. A change rewraps the
     * open SK; the sealed values are not touched.
     */
    @Synchronized
    fun save(pin: String) {
        require(isWalletPin(pin))
        if (!keyring.exists()) {
            val firstPin = encryptedPreferences.getPinHealth() == PinHealth.ABSENT
            // A first PIN on this build lives only in the keyring; no pin_code is written.
            if (firstPin && createKeyringQuietly(pin)) return
            encryptedPreferences.savePinCodeDurably(pin)
            if (!firstPin) createKeyringQuietly(pin)
            return
        }
        val opened = sessionKey() ?: error("A PIN change needs the wallet key the old PIN opened")
        try {
            keyring.replacePin(opened, pin, expectedRecord = null)
            WalletPinHealth.replaceKeyring(PinHealth.HEALTHY)
            // Until the migration removes it, the legacy PIN follows the keyring.
            if (encryptedPreferences.hasKey(PinPreferenceSnapshot.PIN_KEY)) {
                encryptedPreferences.savePinCodeDurably(pin)
            }
        } finally {
            opened.close()
        }
    }

    /** State a recovery is authorized against; any PIN change elsewhere invalidates it. */
    fun captureResetSnapshot(): PinResetSnapshot =
        encryptedPreferences.capturePinResetSnapshot().withKeyringPinRecord(keyring.pinRecord())

    /**
     * Sets [pin] after a recovery proof. With a keyring, [recoveredKey] (SK
     * from a backup wrap or the biometric vault's PIN) is required and only
     * the PIN wrap changes. Without one, the legacy PIN is replaced and a
     * keyring is created for the new PIN.
     */
    @Synchronized
    fun saveRecovered(pin: String, expected: PinResetSnapshot, recoveredKey: WalletSecretKeyring.OpenedKey?, forgotten: Boolean) {
        require(isWalletPin(pin))
        check(expected.matches(captureResetSnapshot())) { "The PIN changed while recovery was open" }
        if (keyring.exists()) {
            val key = checkNotNull(recoveredKey) { "Recovery needs the wallet key once a keyring exists" }
            keyring.replacePin(key, pin, expectedRecord = keyring.pinRecord())
            WalletPinHealth.replaceKeyring(PinHealth.HEALTHY)
            if (encryptedPreferences.hasKey(PinPreferenceSnapshot.PIN_KEY)) {
                encryptedPreferences.savePinCodeDurably(pin)
            }
            openSession(key, WalletSecretSession.Origin.BACKUP_PROOF)
            return
        }
        if (forgotten) {
            encryptedPreferences.saveForgottenPinCodeDurably(pin, expected)
        } else {
            encryptedPreferences.saveRecoveredPinCodeDurably(pin, expected)
        }
        createKeyringQuietly(pin)
    }

    /**
     * Removes `pin_code` and its history once the PIN wrap is the verifier:
     * the keyring's wrap opened from storage in this process, which every
     * successful keyring unlock or creation proves.
     */
    @Synchronized
    fun retireLegacyPin() {
        check(keyring.pinWrapState() == WalletSecretKeyring.PinWrapState.USABLE) { "The PIN wrap is not usable" }
        check(WalletSecretSession.isOpen()) { "The PIN wrap has not opened in this session" }
        encryptedPreferences.removeLegacyPinDurably()
    }

    /** SK of this session, for operations that must rewrap or add to the keyring. Close it after use. */
    fun sessionKey(): WalletSecretKeyring.OpenedKey? =
        WalletSecretSession.withKey { keyId, key -> WalletSecretKeyring.OpenedKey(keyId, key) }

    private fun createKeyringQuietly(pin: String): Boolean {
        if (!sealer.isSealingEnabled() || keyring.exists()) return false
        val created = try {
            keyring.create(pin)
        } catch (failure: Exception) {
            if (failure is InterruptedException) throw failure
            // The legacy PIN keeps working; the next unlock tries again.
            return false
        }
        try {
            openSession(created, WalletSecretSession.Origin.NEW_KEYRING)
            WalletPinHealth.replaceKeyring(PinHealth.HEALTHY)
        } finally {
            created.close()
        }
        return true
    }

    /** A malformed typed PIN is a wrong guess without running the KDF. */
    private fun openWrap(pin: String): WalletSecretKeyring.PinOpening =
        if (isWalletPin(pin)) keyring.openWithPin(pin) else WalletSecretKeyring.PinOpening.WrongPin

    /** Copies [key] into the session; the caller still closes its own copy. */
    private fun openSession(key: WalletSecretKeyring.OpenedKey, origin: WalletSecretSession.Origin) {
        WalletSecretSession.open(key.keyId, key.key, origin)
    }

    private fun isWalletPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    private companion object {
        const val PIN_LENGTH = 6
    }
}
