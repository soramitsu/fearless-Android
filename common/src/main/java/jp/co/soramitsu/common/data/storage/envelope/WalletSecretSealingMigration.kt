package jp.co.soramitsu.common.data.storage.envelope

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/**
 * Seals every allowlisted wallet secret that is still stored without the
 * inner envelope, after an unlock put SK in the session. It resumes after
 * every later unlock and is idempotent: a sealed value is skipped.
 *
 * One key at a time, under [WalletCrossStoreMutationMutex]:
 * 1. the root's backup wraps are stored first, so a sealed root can always be
 *    opened by proving its backup;
 * 2. the sealed value replaces the exact outer snapshot by compare-and-set;
 * 3. the stored result is decrypted and opened again and must equal the
 *    original before the next key; if it does not, the original is put back
 *    and the pass stops.
 *
 * Nothing is archived and no second plaintext copy is written. Keys a pending
 * mutation journal owns are left for that journal. Once nothing sealable is
 * left, the legacy `pin_code` and its history are removed, since the PIN wrap
 * is then the only verifier.
 */
@Singleton
class WalletSecretSealingMigration @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val keyring: WalletSecretKeyring,
    private val sealer: WalletSecretSealer,
    private val pinAuthority: WalletPinAuthority,
    private val journalStore: WalletSecretMutationJournalStore
) {

    data class Report(
        val sealed: Int,
        val alreadySealed: Int,
        /** Readable values left unsealed: journal-owned, changed concurrently, or sealing is off. */
        val remaining: Int,
        val failed: Int,
        val legacyPinRetired: Boolean,
        val stoppedOnMismatch: Boolean = false
    ) {
        companion object {
            val NOTHING = Report(0, 0, 0, 0, false)
        }
    }

    suspend fun run(): Report = WalletCrossStoreMutationMutex.instance.withLock { runLocked() }

    /** For callers that already hold [WalletCrossStoreMutationMutex]. */
    fun runLocked(): Report {
        encryptedPreferences.requireDurableStorageHealthy()
        if (!keyring.exists()) return Report.NOTHING
        val opened = pinAuthority.sessionKey() ?: return Report.NOTHING
        try {
            val current = keyring.currentKeyId() ?: return Report.NOTHING
            if (!current.contentEquals(opened.keyId)) return Report.NOTHING
            return sealAll(opened)
        } finally {
            opened.close()
        }
    }

    private fun sealAll(opened: WalletSecretKeyring.OpenedKey): Report {
        val journalOwned = journalOwnedKeys() ?: return Report.NOTHING.copy(remaining = 1)
        val sealingEnabled = sealer.isSealingEnabled()
        var sealed = 0
        var alreadySealed = 0
        var remaining = 0
        var failed = 0
        for (key in sealableKeys()) {
            // A lock during the pass stops it; the next unlock resumes where it left off.
            if (!WalletSecretSession.isOpen()) {
                return Report(sealed, alreadySealed, remaining + 1, failed, legacyPinRetired = false)
            }
            val target = WalletSecretSealPolicy.classify(key) ?: continue
            if (key in journalOwned.keys || target.metaId?.let(journalOwned.metaIds::contains) == true) {
                remaining++
                continue
            }
            val outcome = try {
                sealOne(target, opened, sealingEnabled)
            } catch (failure: WalletSecureStorageUnavailableException) {
                throw failure
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: SealMismatchException) {
                return Report(sealed, alreadySealed, remaining + 1, failed, legacyPinRetired = false, stoppedOnMismatch = true)
            } catch (failure: Exception) {
                Outcome.FAILED
            }
            when (outcome) {
                Outcome.SEALED -> sealed++
                Outcome.ALREADY_SEALED -> alreadySealed++
                Outcome.UNREADABLE -> Unit
                Outcome.LEFT -> remaining++
                Outcome.FAILED -> failed++
            }
        }
        val complete = sealingEnabled && remaining == 0 && failed == 0 && journalOwned.isEmpty
        val retired = complete && retireLegacyPin()
        return Report(sealed, alreadySealed, remaining, failed, retired)
    }

    /**
     * Uses the same monitor as ordinary encrypted writes and PIN keyring
     * replacement. Holding it through sealing, commit and read-back prevents
     * a concurrent PIN recovery from discarding this SK before its first
     * sealed value becomes visible. Never call the PIN authority under this
     * monitor: its lock precedes the storage lock.
     */
    private fun sealOne(
        target: WalletSecretSealPolicy.Target,
        opened: WalletSecretKeyring.OpenedKey,
        sealingEnabled: Boolean
    ): Outcome = synchronized(encryptedPreferences) {
        val key = target.storageKey
        if (!encryptedPreferences.hasKey(key)) return Outcome.UNREADABLE
        val snapshot = encryptedPreferences.getDecryptedStringSnapshot(key) ?: return Outcome.UNREADABLE
        // A value the device key cannot read (or an oversized one) has nothing to seal; it stays as it is.
        if (!snapshot.hasDecryptedValue) return Outcome.UNREADABLE
        val plaintext = snapshot.plaintext
        if (WalletSecretEnvelope.isSealed(plaintext)) {
            // Sealed by a writer: make sure its root still has its backup wraps.
            val inner = WalletSecretEnvelopes.open(plaintext, opened.keyId, opened.key)
            sealer.ensureBackupWraps(target, inner, opened)
            return Outcome.ALREADY_SEALED
        }
        // Longer than any reader accepts: not a usable wallet secret, and its envelope could not be read either.
        if (!sealer.isSealable(plaintext)) return Outcome.UNREADABLE
        if (!sealingEnabled) {
            // The kill switch stops new sealing; backup wraps keep recovery possible for what is sealed.
            return Outcome.LEFT
        }
        val sealed = sealer.seal(target, plaintext, opened)
        val committed = encryptedPreferences.replaceEncryptedStringsDurablyIfStatesMatch(
            expectedStates = mapOf(key to snapshot),
            valuesToPut = mapOf(key to sealed),
            keysToRemove = emptySet()
        )
        if (!committed) return Outcome.LEFT
        verifyOrRestore(key, sealed, plaintext, opened)
        return Outcome.SEALED
    }

    /** Decrypts what was stored and opens it again; anything else puts the original back and stops. */
    private fun verifyOrRestore(key: String, sealed: String, original: String, opened: WalletSecretKeyring.OpenedKey) {
        val stored = encryptedPreferences.getDecryptedStringSnapshot(key)
        val reopened = try {
            stored?.plaintext?.takeIf { it == sealed }?.let { WalletSecretEnvelopes.open(it, opened.keyId, opened.key) }
        } catch (_: WalletSecretLockedException) {
            null
        }
        if (reopened == original) return
        if (stored != null) {
            encryptedPreferences.replaceEncryptedStringUnsealedIfStateMatches(key, stored, original)
        }
        throw SealMismatchException()
    }

    private fun retireLegacyPin(): Boolean = try {
        pinAuthority.retireLegacyPin()
        true
    } catch (failure: WalletSecureStorageUnavailableException) {
        throw failure
    } catch (_: Exception) {
        false
    }

    private fun sealableKeys(): List<String> {
        val keys = mutableListOf<String>()
        encryptedPreferences.forEachKeyWithPrefixes(WalletSecretSealPolicy.SCANNED_PREFIXES, MAX_KEY_BYTES) { key ->
            if (WalletSecretSealPolicy.isSealable(key)) keys += key
        }
        return keys.sorted()
    }

    private class JournalOwned(val keys: Set<String>, val metaIds: Set<Long>) {
        val isEmpty: Boolean get() = keys.isEmpty() && metaIds.isEmpty()
    }

    /**
     * Keys a pending mutation journal owns: its staged and removed keys and
     * everything of its wallet. Null when a journal exists but cannot be read,
     * so nothing is sealed until it is reconciled.
     */
    private fun journalOwnedKeys(): JournalOwned? {
        if (!encryptedPreferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)) return JournalOwned(emptySet(), emptySet())
        val journal = try {
            journalStore.loadIntent()
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (_: Exception) {
            return null
        } ?: return JournalOwned(emptySet(), emptySet())
        return JournalOwned(journal.secretKeysToPut + journal.secretKeysToRemove, setOf(journal.metaId))
    }

    private enum class Outcome { SEALED, ALREADY_SEALED, UNREADABLE, LEFT, FAILED }

    private class SealMismatchException : IllegalStateException("A sealed value did not read back")

    private companion object {
        const val MAX_KEY_BYTES = 1_024
    }
}
