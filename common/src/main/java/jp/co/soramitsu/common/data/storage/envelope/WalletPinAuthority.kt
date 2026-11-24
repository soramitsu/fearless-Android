package jp.co.soramitsu.common.data.storage.envelope

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.storage.PinPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
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
 *
 * A keyring whose PIN wrap can never open again while no stored value is
 * sealed holds nothing: it is disposable. The PIN is then checked and
 * recovered as before the keyring existed, and a correct PIN starts a new SK,
 * so there is never a lockout while values are unsealed. A value that cannot
 * be read now counts as sealed once SK has begun sealing, so a new SK never
 * strands a value that reads again when its device key returns.
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
            // A disposable keyring answers with the legacy PIN; without one there is
            // nothing to ask for, which is recovery, never a first-PIN onboarding.
            WalletSecretKeyring.PinWrapState.UNUSABLE ->
                if (legacyPinHealth() == PinHealth.HEALTHY && isKeyringDisposable()) PinHealth.HEALTHY else PinHealth.UNREADABLE

            WalletSecretKeyring.PinWrapState.ABSENT -> null
        }
        WalletPinHealth.replaceKeyring(health)
        return health ?: encryptedPreferences.getPinHealth()
    }

    /**
     * Checks [pin]. On CORRECT, SK is open in [WalletSecretSession]: from the
     * PIN wrap, or from a keyring this unlock just created.
     */
    @Synchronized
    fun verify(pin: String): PinVerification {
        when (keyring.pinWrapState()) {
            WalletSecretKeyring.PinWrapState.USABLE -> when (val opening = openWrap(pin)) {
                is WalletSecretKeyring.PinOpening.Opened -> {
                    try {
                        openSession(opening.key, WalletSecretSession.Origin.PIN_WRAP)
                    } finally {
                        opening.key.close()
                    }
                    return PinVerification.CORRECT
                }

                WalletSecretKeyring.PinOpening.WrongPin -> return PinVerification.WRONG
                WalletSecretKeyring.PinOpening.Unusable -> return verifyWithUnusableWrap(pin)
            }

            WalletSecretKeyring.PinWrapState.UNUSABLE -> return verifyWithUnusableWrap(pin)
            WalletSecretKeyring.PinWrapState.ABSENT -> Unit
        }
        val verification = verifyLegacy(pin)
        // The first unlock on this build puts SK under the PIN that was just proved.
        if (verification == PinVerification.CORRECT) createKeyringQuietly(pin)
        return verification
    }

    /**
     * Checks [pin] for emergency key access without the wallet database and
     * without unlocking the session: on CORRECT the caller gets SK (null when no
     * keyring holds anything yet) to open sealed values, and must close it. It
     * never creates or replaces a keyring.
     */
    @Synchronized
    fun verifyForEmergency(pin: String): Pair<PinVerification, WalletSecretKeyring.OpenedKey?> {
        when (keyring.pinWrapState()) {
            WalletSecretKeyring.PinWrapState.USABLE -> when (val opening = openWrap(pin)) {
                is WalletSecretKeyring.PinOpening.Opened -> return PinVerification.CORRECT to opening.key
                WalletSecretKeyring.PinOpening.WrongPin -> return PinVerification.WRONG to null
                WalletSecretKeyring.PinOpening.Unusable ->
                    return (if (isKeyringDisposable()) verifyLegacy(pin) else PinVerification.UNREADABLE) to null
            }

            WalletSecretKeyring.PinWrapState.UNUSABLE ->
                return (if (isKeyringDisposable()) verifyLegacy(pin) else PinVerification.UNREADABLE) to null

            WalletSecretKeyring.PinWrapState.ABSENT -> Unit
        }
        return verifyLegacy(pin) to null
    }

    /**
     * Stores the first PIN (onboarding) or a changed PIN. A change rewraps the
     * open SK; the sealed values are not touched.
     */
    @Synchronized
    fun save(pin: String): Unit = synchronized(encryptedPreferences) {
        require(isWalletPin(pin))
        if (!keyring.exists()) {
            val firstPin = encryptedPreferences.getPinHealth() == PinHealth.ABSENT
            // A first PIN on this build lives only in the keyring; no pin_code is written.
            if (firstPin && createKeyringQuietly(pin)) return
            encryptedPreferences.savePinCodeDurably(pin)
            if (!firstPin) createKeyringQuietly(pin)
            return
        }
        val opened = sessionKey()
        if (opened == null) {
            // A keyring that holds nothing: the legacy PIN changes, and a new SK follows it.
            check(isKeyringDisposable()) { "A PIN change needs the wallet key the old PIN opened" }
            encryptedPreferences.savePinCodeDurably(pin)
            replaceDisposableKeyringQuietly(pin)
            return
        }
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
     * Whether a recovery must bring key material: a keyring exists that is not
     * disposable. Without one, a backup proof alone authorizes a new PIN, as
     * before the keyring existed.
     */
    @Synchronized
    fun recoveryNeedsKeyMaterial(): Boolean = keyring.exists() && !isKeyringDisposable()

    /**
     * Sets [pin] after a recovery proof. With a keyring that holds anything,
     * [recoveredKey] (SK from a backup wrap or the biometric vault's PIN) is
     * required and only the PIN wrap changes. Otherwise the legacy PIN is
     * replaced and a keyring is created (or started again) for the new PIN.
     */
    @Synchronized
    fun saveRecovered(
        pin: String,
        expected: PinResetSnapshot,
        recoveredKey: WalletSecretKeyring.OpenedKey?,
        forgotten: Boolean
    ): Unit = synchronized(encryptedPreferences) {
        require(isWalletPin(pin))
        check(expected.matches(captureResetSnapshot())) { "The PIN changed while recovery was open" }
        if (recoveredKey != null && keyring.exists()) {
            keyring.replacePin(recoveredKey, pin, expectedRecord = keyring.pinRecord())
            WalletPinHealth.replaceKeyring(PinHealth.HEALTHY)
            if (encryptedPreferences.hasKey(PinPreferenceSnapshot.PIN_KEY)) {
                encryptedPreferences.savePinCodeDurably(pin)
            }
            openSession(recoveredKey, WalletSecretSession.Origin.BACKUP_PROOF)
            return
        }
        check(!recoveryNeedsKeyMaterial()) { "Recovery needs the wallet key once a keyring holds anything" }
        if (forgotten) {
            encryptedPreferences.saveForgottenPinCodeDurably(pin, expected)
        } else {
            encryptedPreferences.saveRecoveredPinCodeDurably(pin, expected)
        }
        if (keyring.exists()) replaceDisposableKeyringQuietly(pin) else createKeyringQuietly(pin)
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

    /**
     * A keyring whose PIN wrap can never open while no stored value is sealed
     * (under any SK): it holds nothing, so nothing is lost by starting a new SK.
     *
     * A value the device key cannot read now may be sealed, and may read again
     * once that key returns, so it rules disposal out unless SK never began
     * sealing. Starting a new SK over it would strand it for good: only the
     * current SK is ever opened.
     *
     * Mutating callers hold encryptedPreferences' mutation monitor from this
     * scan through replacement. A writer or migration holding an SK copy must
     * finish its seal and commit before the scan can decide that SK is empty.
     */
    private fun isKeyringDisposable(): Boolean {
        if (!keyring.exists() || keyring.pinWrapState() != WalletSecretKeyring.PinWrapState.UNUSABLE) return false
        val scan = scanSealableValues()
        return !scan.anySealed && (!scan.anyUnreadable || !keyring.mayHoldSealedValues())
    }

    private class SealableScan(var anySealed: Boolean = false, var anyUnreadable: Boolean = false)

    private fun scanSealableValues(): SealableScan {
        val scan = SealableScan()
        encryptedPreferences.forEachKeyWithPrefixes(WalletSecretSealPolicy.SCANNED_PREFIXES, MAX_SCANNED_KEY_BYTES) { key ->
            if (scan.anySealed || !WalletSecretSealPolicy.isSealable(key)) return@forEachKeyWithPrefixes
            val snapshot = sealableSnapshotOrNull(key) ?: return@forEachKeyWithPrefixes
            when {
                WalletSecretEnvelope.isSealed(snapshot.plaintext) -> scan.anySealed = true
                // Empty (the device key cannot read it now) or oversized: it may hold a sealed value.
                !snapshot.hasDecryptedValue -> scan.anyUnreadable = true
            }
        }
        return scan
    }

    /** A stored value that is not a string is never a sealed value. */
    private fun sealableSnapshotOrNull(key: String): EncryptedPreferenceSnapshot? = try {
        encryptedPreferences.getDecryptedStringSnapshot(key)
    } catch (_: ClassCastException) {
        null
    }

    /** The PIN wrap cannot open: the legacy PIN answers while the keyring is disposable, and a new SK follows. */
    private fun verifyWithUnusableWrap(pin: String): PinVerification = synchronized(encryptedPreferences) {
        if (!isKeyringDisposable()) {
            WalletPinHealth.replaceKeyring(PinHealth.UNREADABLE)
            return PinVerification.UNREADABLE
        }
        val verification = verifyLegacy(pin)
        if (verification == PinVerification.CORRECT) replaceDisposableKeyringQuietly(pin)
        return verification
    }

    /** The legacy `pin_code` check. The stored PIN is judged before the typed one: an unreadable PIN is never a wrong guess. */
    private fun verifyLegacy(pin: String): PinVerification {
        val stored = try {
            encryptedPreferences.getDecryptedString(PinPreferenceSnapshot.PIN_KEY)
        } catch (_: ClassCastException) {
            return PinVerification.UNREADABLE
        }
        if (PinHealth.assess(true, stored) != PinHealth.HEALTHY) return PinVerification.UNREADABLE
        val matches = isWalletPin(pin) &&
            MessageDigest.isEqual(checkNotNull(stored).toByteArray(Charsets.US_ASCII), pin.toByteArray(Charsets.US_ASCII))
        return if (matches) PinVerification.CORRECT else PinVerification.WRONG
    }

    /** The legacy `pin_code` alone, never the keyring's published health. */
    private fun legacyPinHealth(): PinHealth = try {
        PinHealth.assess(
            encryptedPreferences.hasKey(PinPreferenceSnapshot.PIN_KEY),
            encryptedPreferences.getDecryptedString(PinPreferenceSnapshot.PIN_KEY)
        )
    } catch (_: ClassCastException) {
        PinHealth.UNREADABLE
    }

    private fun createKeyringQuietly(pin: String): Boolean {
        if (!sealer.isSealingEnabled() || keyring.exists()) return false
        val created = try {
            keyring.create(pin)
        } catch (failure: Exception) {
            if (failure is InterruptedException) throw failure
            // The legacy PIN keeps working; the next unlock tries again.
            return false
        }
        opened(created, WalletSecretSession.Origin.NEW_KEYRING)
        return true
    }

    /** A new SK for a disposable keyring, under the PIN just proved; failure leaves the legacy PIN in charge. */
    private fun replaceDisposableKeyringQuietly(pin: String): Boolean {
        if (!sealer.isSealingEnabled()) return false
        val current = keyring.currentKeyId() ?: return false
        val replaced = try {
            keyring.replaceUnusable(pin, current)
        } catch (failure: Exception) {
            if (failure is InterruptedException) throw failure
            return false
        }
        opened(replaced, WalletSecretSession.Origin.NEW_KEYRING)
        return true
    }

    private fun opened(key: WalletSecretKeyring.OpenedKey, origin: WalletSecretSession.Origin) {
        try {
            openSession(key, origin)
            WalletPinHealth.replaceKeyring(PinHealth.HEALTHY)
        } finally {
            key.close()
        }
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
        const val MAX_SCANNED_KEY_BYTES = 1_024
    }
}
