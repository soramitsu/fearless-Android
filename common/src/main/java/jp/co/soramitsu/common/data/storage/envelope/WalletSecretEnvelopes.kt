package jp.co.soramitsu.common.data.storage.envelope

import java.security.SecureRandom
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.storage.Preferences

/**
 * Opens and seals wallet secret values with this session's SK.
 *
 * Readers open a value right after reading it and before validating it,
 * outside any catch that treats a failure as corruption. A value that is not
 * sealed is returned as it is, so every older format keeps working.
 */
object WalletSecretEnvelopes {

    /**
     * The plaintext of [stored]. Throws [WalletSecretLockedException] for a
     * sealed value while no SK is held, and [WalletSecretUnopenableException]
     * for one this SK does not open. Neither ever means the value is corrupt.
     */
    fun open(stored: String): String {
        if (!WalletSecretEnvelope.isSealed(stored)) return stored
        val header = readHeader(stored)
        return WalletSecretSession.withKey { keyId, key -> openWith(header, keyId, key, stored) }
            ?: throw WalletSecretLockedException()
    }

    /** Like [open] with an SK the caller holds, as a backup-proof recovery does before the new PIN exists. */
    fun open(stored: String, keyId: ByteArray, key: ByteArray): String {
        if (!WalletSecretEnvelope.isSealed(stored)) return stored
        return openWith(readHeader(stored), keyId, key, stored)
    }

    /** The plaintext, or null for a sealed value that cannot be opened now. Never throws for corruption. */
    fun openOrNull(stored: String): String? = try {
        open(stored)
    } catch (_: WalletSecretLockedException) {
        null
    }

    /** Whether [stored] is sealed and this session cannot open it now. */
    fun isLockedFor(stored: String?): Boolean = stored != null && WalletSecretEnvelope.isSealed(stored) && openOrNull(stored) == null

    private fun readHeader(stored: String): WalletSecretEnvelope.Header = try {
        WalletSecretEnvelope.readHeader(stored)
    } catch (failure: WalletSecretEnvelopeFormatException) {
        throw WalletSecretUnopenableException("A sealed wallet secret is malformed", failure)
    }

    private fun openWith(header: WalletSecretEnvelope.Header, keyId: ByteArray, key: ByteArray, stored: String): String {
        if (!header.keyIdMatches(keyId)) {
            throw WalletSecretUnopenableException("A wallet secret is sealed under another SK")
        }
        return try {
            WalletSecretEnvelope.open(key, stored).body
        } catch (failure: WalletSecretEnvelopeAuthenticationException) {
            throw WalletSecretUnopenableException("A sealed wallet secret did not authenticate", failure)
        } catch (failure: WalletSecretEnvelopeFormatException) {
            throw WalletSecretUnopenableException("A sealed wallet secret is malformed", failure)
        }
    }
}

/**
 * The kill switch for NEW sealing: the compiled flag, then a remote veto that
 * a successful feature-toggle fetch can set. Switching it off never unseals a
 * value and never stops a sealed value from being read.
 */
object WalletSecretSealingSwitch {

    /** Remembered remote veto, written by the feature-toggle store. */
    const val REMOTE_VETO_KEY = "feature.wallet_secret_sealing.v1"

    fun isEnabled(preferences: Preferences, compiled: Boolean = BuildConfig.WALLET_SECRET_SEALING_ENABLED): Boolean =
        compiled && preferences.getBoolean(REMOTE_VETO_KEY, true)
}

/**
 * Seals allowlisted wallet secret values as they are written, whenever this
 * session holds SK and sealing is on. A root value is sealed only after a
 * backup wrap for each of its keys is stored, so every sealed root can be
 * opened by proving its backup. If anything in that fails, the value is
 * written as it was; the unlock migration seals it later.
 */
class WalletSecretSealer(
    private val preferences: Preferences,
    private val keyring: WalletSecretKeyring,
    private val random: SecureRandom = SecureRandom(),
    private val compiledSwitch: Boolean = BuildConfig.WALLET_SECRET_SEALING_ENABLED
) {

    fun isSealingEnabled(): Boolean = WalletSecretSealingSwitch.isEnabled(preferences, compiledSwitch)

    /** What to store for [storageKey]: the sealed value when eligible, else [plaintext] unchanged. */
    fun sealForWrite(storageKey: String, plaintext: String): String {
        val target = WalletSecretSealPolicy.classify(storageKey) ?: return plaintext
        if (!isSealable(plaintext) || !isSealingEnabled()) return plaintext
        return WalletSecretSession.withKey { keyId, key ->
            val opened = WalletSecretKeyring.OpenedKey(keyId, key)
            try {
                seal(target, plaintext, opened)
            } catch (failure: Exception) {
                if (failure is InterruptedException) throw failure
                plaintext
            } finally {
                opened.close()
            }
        } ?: plaintext
    }

    /**
     * Seals [plaintext] for [target] with [opened], after storing the backup
     * wraps of its keys. Throws when a wrap cannot be stored; the caller keeps
     * the value unsealed then.
     */
    fun seal(target: WalletSecretSealPolicy.Target, plaintext: String, opened: WalletSecretKeyring.OpenedKey): String {
        require(isSealable(plaintext)) { "This value cannot be sealed" }
        val current = checkNotNull(keyring.currentKeyId()) { "There is no wallet keyring" }
        check(current.contentEquals(opened.keyId)) { "The session SK is not the keyring's SK" }
        ensureBackupWraps(target, plaintext, opened)
        val header = WalletSecretEnvelope.Header(
            keyId = opened.keyId,
            scope = target.scope,
            keyName = target.logicalKey,
            publicIdentity = WalletSecretPayloads.publicIdentity(target.scope, plaintext)
        )
        val sealed = WalletSecretEnvelope.seal(opened.key, header, plaintext, random)
        check(WalletSecretEnvelopes.open(sealed, opened.keyId, opened.key) == plaintext) {
            "A sealed wallet secret does not open to its plaintext"
        }
        return sealed
    }

    /**
     * Empty and already sealed values are left as they are, and so is a value
     * longer than any wallet secret reader accepts: its envelope must stay
     * within the bound every reader opens.
     */
    fun isSealable(plaintext: String): Boolean =
        plaintext.isNotEmpty() && plaintext.length <= MAX_SEALABLE_PLAINTEXT_CHARS && !WalletSecretEnvelope.isSealed(plaintext)

    /** Backup wraps for the keys of an active root value; quarantined and archived copies get none. */
    fun ensureBackupWraps(target: WalletSecretSealPolicy.Target, plaintext: String, opened: WalletSecretKeyring.OpenedKey) {
        if (target.location != WalletSecretSealPolicy.Location.ACTIVE) return
        val keys = WalletSecretPayloads.rootKeys(target.scope, plaintext)
        try {
            keys.forEach { root ->
                val canonical = root.kind.canonical(root.privateKey) ?: return@forEach
                try {
                    keyring.addBackupWrap(opened, canonical)
                } finally {
                    canonical.fill(0)
                }
            }
        } finally {
            keys.forEach { it.privateKey.fill(0) }
        }
    }

    companion object {
        /** The largest wallet secret plaintext readers accept (MAX_WALLET_SECRET_PLAINTEXT_CHARS). */
        const val MAX_SEALABLE_PLAINTEXT_CHARS = 1_048_576
    }
}
