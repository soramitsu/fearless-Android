package jp.co.soramitsu.common.data.storage.encrypt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val NO_WALLET_SECRET_QUARANTINE_CHANGES = MutableStateFlow(0L)

class WalletSecretConcurrentMutationException(
    message: String
) : IllegalStateException(message)

/**
 * Moves one exact validated preference value from an active key to a
 * quarantine key as part of a larger snapshot-bound durable replacement.
 *
 * A retry is accepted when the source is already absent and the destination
 * still contains the exact snapshot. A conflicting destination or a changed
 * source rejects the complete replacement without publishing any value.
 */
data class EncryptedPreferenceSnapshotMove(
    val sourceKey: String,
    val destinationKey: String,
    val expectedSnapshot: EncryptedPreferenceSnapshot
)

class EncryptedPreferenceSnapshot private constructor(
    val plaintext: String,
    private val rawCiphertextFingerprint: String,
    private val oversizedCiphertext: Boolean
) {

    /**
     * Exact-match predicate for plaintext-backed implementations used by
     * tests. Keeping the fingerprint opaque prevents callers from rebuilding
     * a snapshot from the validation token alone.
     */
    fun matchesUnencryptedStorageValue(value: String): Boolean {
        return !oversizedCiphertext &&
            plaintext == value &&
            rawCiphertextFingerprint == walletCiphertextFingerprint(value)
    }

    fun matchesExactSnapshot(other: EncryptedPreferenceSnapshot): Boolean {
        return plaintext == other.plaintext &&
            oversizedCiphertext == other.oversizedCiphertext &&
            rawCiphertextFingerprint == other.rawCiphertextFingerprint
    }

    internal fun matchesStoredCiphertext(
        ciphertext: String,
        isOversized: Boolean
    ): Boolean {
        return oversizedCiphertext == isOversized &&
            rawCiphertextFingerprint ==
            walletCiphertextFingerprint(ciphertext)
    }

    companion object {
        internal fun encrypted(
            plaintext: String,
            rawCiphertext: String,
            oversizedCiphertext: Boolean
        ) = EncryptedPreferenceSnapshot(
            plaintext = plaintext,
            rawCiphertextFingerprint =
            walletCiphertextFingerprint(rawCiphertext),
            oversizedCiphertext = oversizedCiphertext
        )

        /**
         * Compatibility snapshot for plaintext-backed test implementations.
         * Production encrypted storage must use [encrypted].
         */
        fun unencrypted(plaintext: String) = EncryptedPreferenceSnapshot(
            plaintext = plaintext,
            rawCiphertextFingerprint =
            walletCiphertextFingerprint(plaintext),
            oversizedCiphertext = false
        )
    }
}

interface EncryptedPreferences {

    /**
     * Process-local, monotonic invalidation version for durable wallet-secret
     * quarantines. The flow never contains preference keys or secret material.
     * Production implementations that quarantine values must override this.
     */
    val walletSecretQuarantineVersion: StateFlow<Long>
        get() = NO_WALLET_SECRET_QUARANTINE_CHANGES

    fun putEncryptedString(field: String, value: String)

    fun getDecryptedString(field: String): String?

    fun getDecryptedStringSnapshot(
        field: String
    ): EncryptedPreferenceSnapshot? {
        return getDecryptedString(field)?.let(
            EncryptedPreferenceSnapshot::unencrypted
        )
    }

    fun hasKey(field: String): Boolean

    /**
     * Returns whether an encrypted preference key begins with [prefix].
     * Implementations must inspect names only and must not decrypt values.
     */
    fun hasKeyWithPrefix(prefix: String): Boolean {
        error("Encrypted preference key-prefix inspection is unavailable")
    }

    /**
     * Returns exact encrypted preference key names under bounded allowlisted
     * prefixes without reading or decrypting any value.
     */
    fun keysWithPrefixes(
        prefixes: Set<String>,
        maxResultCount: Int,
        maxKeyBytes: Int,
        maxTotalKeyBytes: Int,
        failOnOversizedMatch: Boolean = false
    ): Set<String> {
        error("Encrypted preference key-name inspection is unavailable")
    }

    fun removeKey(field: String)

    /**
     * Encrypts and atomically persists all replacements before returning.
     * Implementations must throw if durability cannot be guaranteed.
     */
    fun replaceEncryptedStringsDurably(
        valuesToPut: Map<String, String>,
        keysToRemove: Set<String>
    )

    /**
     * Atomically publishes replacements only while every allowlisted key still
     * has its expected exact state. A null snapshot means the key must remain
     * absent. Every written or removed key must have an expected state, which
     * prevents silently overwriting a concurrently created target.
     * [snapshotMoves] bind raw active values to disjoint quarantine
     * destinations in that same durable replacement and accept an
     * already-completed exact move on retry.
     *
     * Production encrypted implementations must override this method so all
     * raw-ciphertext comparisons and the commit share one mutation lock. The
     * default supports plaintext-backed test implementations.
     */
    fun replaceEncryptedStringsDurablyIfStatesMatch(
        expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
        valuesToPut: Map<String, String>,
        keysToRemove: Set<String>,
        snapshotMoves: List<EncryptedPreferenceSnapshotMove> = emptyList()
    ): Boolean = synchronized(this) {
        validateSnapshotBoundReplacement(
            expectedStates = expectedStates,
            valuesToPut = valuesToPut,
            keysToRemove = keysToRemove,
            snapshotMoves = snapshotMoves
        )
        val allStatesMatch = expectedStates.all { (key, expectedSnapshot) ->
            val currentSnapshot = getDecryptedStringSnapshot(key)
            if (expectedSnapshot == null) {
                currentSnapshot == null
            } else {
                currentSnapshot != null &&
                    expectedSnapshot.matchesUnencryptedStorageValue(
                        currentSnapshot.plaintext
                    )
            }
        }
        if (!allStatesMatch) {
            return@synchronized false
        }

        val movedValuesToPut = linkedMapOf<String, String>()
        val movedKeysToRemove = linkedSetOf<String>()
        for (move in snapshotMoves) {
            val sourceExists = hasKey(move.sourceKey)
            val destinationExists = hasKey(move.destinationKey)
            if (sourceExists) {
                val sourceValue =
                    getDecryptedString(move.sourceKey)
                        ?: return@synchronized false
                if (
                    !move.expectedSnapshot
                        .matchesUnencryptedStorageValue(sourceValue)
                ) {
                    return@synchronized false
                }
                if (destinationExists) {
                    val destinationValue =
                        getDecryptedString(move.destinationKey)
                            ?: return@synchronized false
                    if (
                        !move.expectedSnapshot
                            .matchesUnencryptedStorageValue(destinationValue)
                    ) {
                        return@synchronized false
                    }
                } else {
                    movedValuesToPut[move.destinationKey] = sourceValue
                }
                movedKeysToRemove += move.sourceKey
            } else {
                if (!destinationExists) {
                    return@synchronized false
                }
                val destinationValue =
                    getDecryptedString(move.destinationKey)
                        ?: return@synchronized false
                if (
                    !move.expectedSnapshot
                        .matchesUnencryptedStorageValue(destinationValue)
                ) {
                    return@synchronized false
                }
            }
        }

        val combinedValuesToPut = valuesToPut + movedValuesToPut
        val combinedKeysToRemove = keysToRemove + movedKeysToRemove
        if (
            combinedValuesToPut.isEmpty() &&
            combinedKeysToRemove.isEmpty()
        ) {
            return@synchronized true
        }
        replaceEncryptedStringsDurably(
            valuesToPut = combinedValuesToPut,
            keysToRemove = combinedKeysToRemove
        )
        true
    }

    /**
     * Atomically moves the exact stored ciphertext out of active use only when
     * its current raw-ciphertext fingerprint and representation kind still
     * equal [expectedSnapshot]. The opaque snapshot also carries the exact
     * plaintext returned to the validating caller.
     *
     * The implementation may decrypt only for the equality check; it must not
     * re-encrypt, log, or otherwise transform the stored value. The comparison
     * and raw-ciphertext move must run under the same mutation lock as durable
     * replacements. A false return means a
     * concurrent writer changed the active value, so callers must retry
     * validation and must not surface recovery for the stale snapshot.
     *
     * Provider/decryption failures must throw without changing either key.
     */
    fun quarantineEncryptedStringDurably(
        sourceKey: String,
        quarantineKey: String,
        expectedSnapshot: EncryptedPreferenceSnapshot
    ): Boolean

    /**
     * Fails every encrypted read or write in the same process after any secure
     * storage instance reports an ambiguous durable write. A new process can
     * safely retry from the last committed preference file.
     */
    fun requireDurableStorageHealthy()
}

fun EncryptedPreferences.quarantineEncryptedStringSnapshotDurably(
    sourceKey: String,
    quarantineKey: String,
    expectedSnapshot: EncryptedPreferenceSnapshot
) {
    if (
        !quarantineEncryptedStringDurably(
            sourceKey = sourceKey,
            quarantineKey = quarantineKey,
            expectedSnapshot = expectedSnapshot
        )
    ) {
        throw WalletSecretConcurrentMutationException(
            "Wallet secret changed while its stale snapshot was being quarantined"
        )
    }
}

fun EncryptedPreferences.replaceEncryptedStringsForStatesDurably(
    expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
    valuesToPut: Map<String, String>,
    keysToRemove: Set<String>,
    snapshotMoves: List<EncryptedPreferenceSnapshotMove> = emptyList()
) {
    if (
        !replaceEncryptedStringsDurablyIfStatesMatch(
            expectedStates = expectedStates,
            valuesToPut = valuesToPut,
            keysToRemove = keysToRemove,
            snapshotMoves = snapshotMoves
        )
    ) {
        throw WalletSecretConcurrentMutationException(
            "Wallet secret changed while its validated replacement was pending"
        )
    }
}

/**
 * Read-only equivalent of the snapshot-move conflict checks performed by the
 * durable CAS. Migrations use this while preparing every wallet so a
 * conflicting quarantine destination in a late row is rejected before any
 * earlier row can publish an external preference mutation.
 */
fun EncryptedPreferences.requireSnapshotMovesReady(
    snapshotMoves: List<EncryptedPreferenceSnapshotMove>
) {
    snapshotMoves.forEach { move ->
        val sourceSnapshot = if (hasKey(move.sourceKey)) {
            getDecryptedStringSnapshot(move.sourceKey)
                ?: throw WalletSecretConcurrentMutationException(
                    "A wallet-secret quarantine source disappeared during preflight"
                )
        } else {
            null
        }
        val destinationSnapshot = if (hasKey(move.destinationKey)) {
            getDecryptedStringSnapshot(move.destinationKey)
                ?: throw WalletSecretConcurrentMutationException(
                    "A wallet-secret quarantine destination disappeared during preflight"
                )
        } else {
            null
        }

        if (sourceSnapshot == null) {
            if (
                destinationSnapshot == null ||
                !move.expectedSnapshot.matchesExactSnapshot(
                    destinationSnapshot
                )
            ) {
                throw WalletSecretConcurrentMutationException(
                    "A wallet-secret quarantine retry has conflicting state"
                )
            }
        } else {
            if (
                !move.expectedSnapshot.matchesExactSnapshot(sourceSnapshot) ||
                (
                    destinationSnapshot != null &&
                        !move.expectedSnapshot.matchesExactSnapshot(
                            destinationSnapshot
                        )
                    )
            ) {
                throw WalletSecretConcurrentMutationException(
                    "A wallet-secret quarantine move has conflicting state"
                )
            }
        }
    }
}

internal fun validateSnapshotBoundReplacement(
    expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
    valuesToPut: Map<String, String>,
    keysToRemove: Set<String>,
    snapshotMoves: List<EncryptedPreferenceSnapshotMove>
) {
    require(valuesToPut.keys.intersect(keysToRemove).isEmpty()) {
        "A wallet-secret CAS cannot write and remove the same key"
    }
    require(
        (valuesToPut.keys + keysToRemove).all(expectedStates::containsKey)
    ) {
        "Every wallet-secret CAS target requires an expected state"
    }

    val moveSources = snapshotMoves.mapTo(linkedSetOf()) {
        it.sourceKey
    }
    val moveDestinations = snapshotMoves.mapTo(linkedSetOf()) {
        it.destinationKey
    }
    require(moveSources.size == snapshotMoves.size) {
        "A wallet-secret CAS cannot move one source more than once"
    }
    require(moveDestinations.size == snapshotMoves.size) {
        "A wallet-secret CAS cannot share a move destination"
    }
    require(
        snapshotMoves.all { it.sourceKey != it.destinationKey } &&
            moveSources.intersect(moveDestinations).isEmpty()
    ) {
        "A wallet-secret CAS move graph must be disjoint"
    }

    val ordinaryKeys =
        expectedStates.keys + valuesToPut.keys + keysToRemove
    require(
        moveSources.none(ordinaryKeys::contains) &&
            moveDestinations.none(ordinaryKeys::contains)
    ) {
        "Wallet-secret CAS moves and ordinary states must be disjoint"
    }
}

internal fun walletCiphertextFingerprint(ciphertext: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(SNAPSHOT_HASH_BUFFER_BYTES)
    var bufferedBytes = 0
    ciphertext.forEach { codeUnit ->
        if (bufferedBytes == buffer.size) {
            digest.update(buffer, 0, bufferedBytes)
            bufferedBytes = 0
        }
        buffer[bufferedBytes++] =
            (codeUnit.code ushr Byte.SIZE_BITS).toByte()
        buffer[bufferedBytes++] = codeUnit.code.toByte()
    }
    if (bufferedBytes > 0) {
        digest.update(buffer, 0, bufferedBytes)
    }
    return org.bouncycastle.util.encoders.Hex.toHexString(digest.digest())
}

private const val SNAPSHOT_HASH_BUFFER_BYTES = 8_192
