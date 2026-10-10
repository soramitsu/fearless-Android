package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.common.data.storage.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class EncryptedPreferencesImpl(
    private val preferences: Preferences,
    private val encryptionUtil: EncryptionUtil
) : EncryptedPreferences {

    private val mutableWalletSecretQuarantineVersion = MutableStateFlow(0L)

    override val walletSecretQuarantineVersion: StateFlow<Long> =
        mutableWalletSecretQuarantineVersion.asStateFlow()

    override fun putEncryptedString(field: String, value: String) {
        replaceEncryptedStringsDurably(
            valuesToPut = mapOf(field to value),
            keysToRemove = emptySet()
        )
    }

    override fun getDecryptedString(field: String): String? {
        return getDecryptedStringSnapshot(field)?.plaintext
    }

    override fun getDecryptedStringSnapshot(
        field: String
    ): EncryptedPreferenceSnapshot? {
        requireDurableStorageHealthy()
        val encryptedString = preferences.getString(field) ?: return null
        if (encryptedString.length > MAX_ENCRYPTED_PREFERENCE_CHARS) {
            // Do not hand an attacker-controlled multi-megabyte Base64 value
            // to the decoder. The bounded digest token lets quarantine compare
            // the exact raw snapshot under its mutation lock; two different
            // oversized ciphertexts must never compare equal.
            return EncryptedPreferenceSnapshot.encrypted(
                plaintext = oversizedCiphertextSnapshot(encryptedString),
                rawCiphertext = encryptedString,
                oversizedCiphertext = true
            )
        }
        // Reads are deliberately side-effect free. Secret-format migrations
        // perform their own verified synchronous replacement, while a failed
        // decode must leave the exact original ciphertext available for
        // quarantine and future recovery.
        return EncryptedPreferenceSnapshot.encrypted(
            plaintext = encryptionUtil.decrypt(encryptedString),
            rawCiphertext = encryptedString,
            oversizedCiphertext = false
        )
    }

    override fun hasKey(field: String): Boolean {
        requireDurableStorageHealthy()
        return preferences.contains(field)
    }

    override fun hasKeyWithPrefix(prefix: String): Boolean {
        requireDurableStorageHealthy()
        require(prefix.isNotEmpty()) { "An encrypted preference key prefix cannot be empty" }
        return preferences.hasKeyWithPrefix(prefix)
    }

    override fun keysWithPrefixes(
        prefixes: Set<String>,
        maxResultCount: Int,
        maxKeyBytes: Int,
        maxTotalKeyBytes: Int,
        failOnOversizedMatch: Boolean
    ): Set<String> {
        requireDurableStorageHealthy()
        return preferences.keysWithPrefixes(
            prefixes = prefixes,
            maxResultCount = maxResultCount,
            maxKeyBytes = maxKeyBytes,
            maxTotalKeyBytes = maxTotalKeyBytes,
            failOnOversizedMatch = failOnOversizedMatch
        )
    }

    @Synchronized
    override fun removeKey(field: String) {
        requireDurableStorageHealthy()
        preferences.removeField(field)
    }

    @Synchronized
    override fun replaceEncryptedStringsDurably(
        valuesToPut: Map<String, String>,
        keysToRemove: Set<String>
    ) {
        requireDurableStorageHealthy()

        val encryptedValues = encryptAndVerify(valuesToPut)

        latchDurabilityFailures {
            val committed = preferences.replaceStringsDurably(encryptedValues, keysToRemove)
            val verified = committed &&
                encryptedValues.all { (key, value) ->
                    preferences.getString(key) == value
                } &&
                keysToRemove.none(preferences::contains)

            check(verified) {
                "Failed to durably persist encrypted wallet-secret migration"
            }
        }
    }

    @Synchronized
    override fun replaceEncryptedStringsDurablyIfStatesMatch(
        expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
        valuesToPut: Map<String, String>,
        keysToRemove: Set<String>,
        snapshotMoves: List<EncryptedPreferenceSnapshotMove>
    ): Boolean {
        requireDurableStorageHealthy()
        validateSnapshotBoundReplacement(
            expectedStates = expectedStates,
            valuesToPut = valuesToPut,
            keysToRemove = keysToRemove,
            snapshotMoves = snapshotMoves
        )

        val exactExpectedCiphertexts = mutableMapOf<String, String?>()
        for ((key, expectedSnapshot) in expectedStates) {
            val exactCiphertext = preferences.getString(key)
            val matches = if (expectedSnapshot == null) {
                exactCiphertext == null
            } else {
                exactCiphertext != null &&
                    storedCiphertextMatchesSnapshot(
                        ciphertext = exactCiphertext,
                        expectedSnapshot = expectedSnapshot
                    )
            }
            if (!matches) return false
            exactExpectedCiphertexts[key] = exactCiphertext
        }

        val resolvedMoves = mutableListOf<ResolvedSnapshotMove>()
        for (move in snapshotMoves) {
            val sourceCiphertext = preferences.getString(move.sourceKey)
            val destinationCiphertext =
                preferences.getString(move.destinationKey)
            if (sourceCiphertext != null) {
                if (
                    !storedCiphertextMatchesSnapshot(
                        ciphertext = sourceCiphertext,
                        expectedSnapshot = move.expectedSnapshot
                    )
                ) {
                    return false
                }
                if (
                    destinationCiphertext != null &&
                    destinationCiphertext != sourceCiphertext
                ) {
                    return false
                }
                resolvedMoves += ResolvedSnapshotMove(
                    move = move,
                    exactCiphertext = sourceCiphertext,
                    sourceMustBeRemoved = true,
                    destinationMustBeWritten =
                    destinationCiphertext == null
                )
            } else {
                if (
                    destinationCiphertext == null ||
                    !storedCiphertextMatchesSnapshot(
                        ciphertext = destinationCiphertext,
                        expectedSnapshot = move.expectedSnapshot
                    )
                ) {
                    return false
                }
                resolvedMoves += ResolvedSnapshotMove(
                    move = move,
                    exactCiphertext = destinationCiphertext,
                    sourceMustBeRemoved = false,
                    destinationMustBeWritten = false
                )
            }
        }

        val encryptedValues = encryptAndVerify(valuesToPut)
        val rawValuesToPut = encryptedValues + resolvedMoves
            .filter(ResolvedSnapshotMove::destinationMustBeWritten)
            .associate {
                it.move.destinationKey to it.exactCiphertext
            }
        val allKeysToRemove = keysToRemove + resolvedMoves
            .filter(ResolvedSnapshotMove::sourceMustBeRemoved)
            .mapTo(linkedSetOf()) {
                it.move.sourceKey
            }
        if (rawValuesToPut.isEmpty() && allKeysToRemove.isEmpty()) {
            return true
        }

        latchDurabilityFailures {
            val committed = preferences.replaceStringsDurably(
                rawValuesToPut,
                allKeysToRemove
            )
            val verified = committed &&
                rawValuesToPut.all { (key, value) ->
                    preferences.getString(key) == value
                } &&
                allKeysToRemove.none(preferences::contains) &&
                exactExpectedCiphertexts.all { (key, value) ->
                    key in valuesToPut ||
                        key in keysToRemove ||
                        preferences.getString(key) == value
                } &&
                resolvedMoves.all {
                    !preferences.contains(it.move.sourceKey) &&
                        preferences.getString(
                            it.move.destinationKey
                        ) == it.exactCiphertext
                }

            check(verified) {
                "Failed to durably persist snapshot-bound wallet secrets"
            }
        }
        if (resolvedMoves.any(ResolvedSnapshotMove::sourceMustBeRemoved)) {
            mutableWalletSecretQuarantineVersion.update { it + 1 }
        }
        return true
    }

    @Synchronized
    override fun quarantineEncryptedStringDurably(
        sourceKey: String,
        quarantineKey: String,
        expectedSnapshot: EncryptedPreferenceSnapshot
    ): Boolean {
        requireDurableStorageHealthy()
        require(sourceKey != quarantineKey) {
            "A wallet secret cannot be quarantined onto its active key"
        }

        val sourceExists = preferences.contains(sourceKey)
        val quarantineExists = preferences.contains(quarantineKey)

        if (!sourceExists) {
            check(quarantineExists) {
                "Wallet secret disappeared before it could be quarantined"
            }
            val quarantinedCiphertext =
                checkNotNull(preferences.getString(quarantineKey)) {
                    "Wallet secret quarantine is not stored as ciphertext"
                }
            return storedCiphertextMatchesSnapshot(
                ciphertext = quarantinedCiphertext,
                expectedSnapshot = expectedSnapshot
            )
        }

        val exactStoredCiphertext = checkNotNull(preferences.getString(sourceKey)) {
            "Wallet secret is not stored as ciphertext"
        }
        if (
            !storedCiphertextMatchesSnapshot(
                ciphertext = exactStoredCiphertext,
                expectedSnapshot = expectedSnapshot
            )
        ) {
            return false
        }

        if (quarantineExists) {
            check(preferences.getString(quarantineKey) == exactStoredCiphertext) {
                "Wallet secret quarantine contains a conflicting ciphertext"
            }
        }

        val valuesToPut = if (quarantineExists) {
            emptyMap()
        } else {
            mapOf(quarantineKey to exactStoredCiphertext)
        }

        latchDurabilityFailures {
            val committed = preferences.replaceStringsDurably(
                valuesToPut = valuesToPut,
                keysToRemove = setOf(sourceKey)
            )
            val verified = committed &&
                !preferences.contains(sourceKey) &&
                preferences.getString(quarantineKey) == exactStoredCiphertext

            check(verified) {
                "Failed to durably quarantine unreadable wallet ciphertext"
            }
        }

        mutableWalletSecretQuarantineVersion.update { it + 1 }
        return true
    }

    override fun requireDurableStorageHealthy() {
        WalletSecureStorageHealth.requireHealthy()
    }

    private fun storedCiphertextMatchesSnapshot(
        ciphertext: String,
        expectedSnapshot: EncryptedPreferenceSnapshot
    ): Boolean {
        val oversized =
            ciphertext.length > MAX_ENCRYPTED_PREFERENCE_CHARS
        if (
            !expectedSnapshot.matchesStoredCiphertext(
                ciphertext = ciphertext,
                isOversized = oversized
            )
        ) {
            return false
        }
        return oversized ||
            encryptionUtil.decrypt(ciphertext) ==
            expectedSnapshot.plaintext
    }

    private fun encryptAndVerify(
        valuesToPut: Map<String, String>
    ): Map<String, String> = valuesToPut.mapValues { (_, value) ->
        val encrypted = encryptionUtil.encrypt(value)
        check(
            encrypted.isNotEmpty() &&
                encryptionUtil.isModernCiphertext(encrypted) &&
                encryptionUtil.decrypt(encrypted) == value
        ) {
            "Failed to encrypt and verify wallet-secret migration payload"
        }
        encrypted
    }

    private inline fun <T> latchDurabilityFailures(operation: () -> T): T {
        return try {
            operation()
        } catch (failure: Throwable) {
            // A commit may have reached disk even when its readback fails.
            // Prevent Room from retrying an ambiguous cross-store operation
            // again in this process.
            WalletSecureStorageHealth.latchProcessRestartRequired()
            throw WalletSecureStorageUnavailableException(
                "Encrypted preference durability is ambiguous until the process restarts",
                failure,
                WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED
            )
        }
    }

    private companion object {
        const val OVERSIZED_CIPHERTEXT_SNAPSHOT_PREFIX =
            "fearless-oversized-ciphertext-sha256:"

        // Valid wallet payloads are orders of magnitude smaller. This remains
        // above the decoded payload bound to allow Base64 and cipher overhead.
        const val MAX_ENCRYPTED_PREFERENCE_CHARS = 2_097_152

        fun oversizedCiphertextSnapshot(ciphertext: String): String {
            return OVERSIZED_CIPHERTEXT_SNAPSHOT_PREFIX +
                walletCiphertextFingerprint(ciphertext)
        }
    }

    private data class ResolvedSnapshotMove(
        val move: EncryptedPreferenceSnapshotMove,
        val exactCiphertext: String,
        val sourceMustBeRemoved: Boolean,
        val destinationMustBeWritten: Boolean
    )
}
