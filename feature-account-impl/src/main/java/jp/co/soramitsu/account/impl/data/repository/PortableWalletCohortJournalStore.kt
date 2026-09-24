package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/**
 * Encrypted, durable staging for an entire portable wallet cohort. This journal is deliberately
 * read-only at replay: it never publishes Room rows, writes target secret keys, or marks recovery
 * complete. It durably marks exact target IDs in encrypted preferences alongside the journal;
 * current wallet allocators treat those markers as occupied.
 * A future installer must atomically reconcile every destination before removing it.
 *
 * Wire: ASCII FPWCJ001, u8 version, canonical ASCII UUID, u32 FPWCAI01 length, exact FPWCAI01
 * bytes, SHA-256 of all preceding bytes. Version 2 atomically stages a separate, bounded encrypted
 * original-source sidecar containing exact opaque auxiliary records and full-wallet commitments.
 * Normalized root/V1/V2 private-key slots remain in this journal until a future installer verifies
 * target stores. Opaque auxiliary source bytes may also be secret and remain encrypted separately.
 */
internal class PortableWalletCohortJournalStore(
    private val preferences: EncryptedPreferences,
) {
    private val singleWalletJournal = WalletSecretMutationJournalStore(preferences)

    internal enum class FailureReason {
        INVALID_ARGUMENT,
        CONFLICT,
        MALFORMED_STORED_JOURNAL,
        UNSUPPORTED_VERSION,
        AMBIGUOUS_DURABILITY,
    }

    internal class JournalException(
        val reason: FailureReason,
        message: String,
        cause: Throwable? = null,
    ) : IllegalStateException(message, cause)

    internal class Token internal constructor(
        val operationId: String,
        val afterImageSha256: String,
    ) {
        override fun toString(): String = "PortableWalletCohortJournalStore.Token(redacted)"
    }

    internal class Entry internal constructor(
        val token: Token,
        val afterImage: PortableWalletCohortAfterImage.Record,
        val journalVersion: Int,
    ) {
        fun clearSecrets() = afterImage.clearSecrets()

        override fun toString(): String = "PortableWalletCohortJournalStore.Entry(redacted)"
    }

    /**
     * Stages only the encrypted after-image, its narrow original-source sidecar, and exact ID markers. Caller-supplied
     * IDs must be freshly allocated and unoccupied; Room reservation and all target-store writes
     * remain unresolved blockers. Prefix-based namespace inspection cannot join the exact-key
     * preference CAS, and a bypass or cross-process writer can occupy an ID after staging, so
     * every replay checks the namespace again. A staged record is not installation authorization.
     * An uncertain commit is never retried: reload it in a fresh healthy process.
     */
    @Synchronized
    fun stage(operationId: String, afterImage: PortableWalletCohortAfterImage.Record): Token {
        validateOperationId(operationId)
        val afterBytes = PortableWalletCohortAfterImage.encode(afterImage)
        try {
            requireNoConflicts(afterImage)
            val wire = encode(operationId, afterBytes, VERSION)
            val token = Token(operationId, digestHex(afterBytes))
            val originalSource = PortableWalletOriginalSourceSidecar.encode(afterImage, token)
            val committed = durabilityBoundary {
                preferences.requireDurableStorageHealthy()
                preferences.replaceEncryptedStringsDurablyIfStatesMatch(
                    expectedStates = expectedAbsentStates(afterImage),
                    valuesToPut = buildMap {
                        put(JOURNAL_KEY, wire)
                        put(ORIGINAL_SOURCE_KEY, originalSource)
                        reservationKeys(afterImage).forEach { put(it, reservationValue(token)) }
                    },
                    keysToRemove = emptySet(),
                )
            }
            if (!committed) {
                throw JournalException(
                    FailureReason.CONFLICT,
                    "A portable cohort destination or active journal changed",
                )
            }
            durabilityBoundary {
                val readback = loadValidated()
                    ?: error("The committed portable cohort journal is missing")
                try {
                    val exact = PortableWalletCohortAfterImage.encode(readback.afterImage)
                    try {
                        check(
                            readback.token.operationId == operationId &&
                                readback.token.afterImageSha256 == token.afterImageSha256 &&
                                readback.journalVersion == VERSION &&
                                exact.contentEquals(afterBytes)
                        ) { "The committed portable cohort differs from its intent" }
                    } finally {
                        exact.fill(0)
                    }
                } finally {
                    readback.clearSecrets()
                }
            }
            return token
        } finally {
            afterBytes.fill(0)
        }
    }

    /** Replays only validation; a valid result still contains installer and source blockers. */
    @Synchronized
    fun load(): Entry? = loadValidated()

    /** Upgrades an exact pre-sidecar v1 journal without publishing any wallet or target secret. */
    @Synchronized
    fun ensureOriginalSourceSidecar(expected: Token) {
        validateOperationId(expected.operationId)
        require(expected.afterImageSha256.matches(HEX_SHA256)) { "Portable cohort commitment is invalid" }
        val snapshot = preferences.getDecryptedStringSnapshot(JOURNAL_KEY)
            ?: fail(FailureReason.CONFLICT, "No portable cohort journal exists")
        val entry = decode(snapshot.plaintext)
        try {
            if (entry.token.operationId != expected.operationId ||
                entry.token.afterImageSha256 != expected.afterImageSha256
            ) {
                fail(FailureReason.CONFLICT, "Portable cohort journal changed")
            }
            val reservations = requireNoConflicts(entry.afterImage, ownToken = entry.token)
            val original = requireMatchingSidecar(entry)
            if (entry.journalVersion == VERSION) return
            val afterBytes = PortableWalletCohortAfterImage.encode(entry.afterImage)
            try {
                val upgradedWire = encode(expected.operationId, afterBytes, VERSION)
                val originalSource = PortableWalletOriginalSourceSidecar.encode(entry.afterImage, expected)
                val committed = durabilityBoundary {
                    preferences.requireDurableStorageHealthy()
                    preferences.replaceEncryptedStringsDurablyIfStatesMatch(
                        expectedStates = expectedAbsentStates(entry.afterImage) +
                            (JOURNAL_KEY to snapshot) + reservations + (ORIGINAL_SOURCE_KEY to original),
                        valuesToPut = mapOf(JOURNAL_KEY to upgradedWire, ORIGINAL_SOURCE_KEY to originalSource),
                        keysToRemove = emptySet(),
                    )
                }
                if (!committed) fail(FailureReason.CONFLICT, "Portable cohort journal changed")
                durabilityBoundary {
                    val readback = requireNotNull(loadValidated())
                    try {
                        check(readback.journalVersion == VERSION &&
                            readback.token.operationId == expected.operationId &&
                            readback.token.afterImageSha256 == expected.afterImageSha256
                        ) { "Portable cohort source upgrade differs from its intent" }
                    } finally {
                        readback.clearSecrets()
                    }
                }
            } finally {
                afterBytes.fill(0)
            }
        } finally {
            entry.clearSecrets()
        }
    }

    /** Removes only this exact, still-uninstalled after-image; never removes wallet material. */
    @Synchronized
    fun abandon(expected: Token) {
        validateOperationId(expected.operationId)
        require(expected.afterImageSha256.matches(HEX_SHA256)) {
            "Portable cohort commitment is invalid"
        }
        val snapshot = preferences.getDecryptedStringSnapshot(JOURNAL_KEY)
            ?: fail(FailureReason.CONFLICT, "No portable cohort journal exists")
        val entry = decode(snapshot.plaintext)
        try {
            val reservationSnapshots = requireNoConflicts(entry.afterImage, ownToken = entry.token)
            val original = requireMatchingSidecar(entry)
            if (
                entry.token.operationId != expected.operationId ||
                entry.token.afterImageSha256 != expected.afterImageSha256
            ) {
                fail(FailureReason.CONFLICT, "Portable cohort journal changed")
            }
            val removed = durabilityBoundary {
                preferences.requireDurableStorageHealthy()
                preferences.replaceEncryptedStringsDurablyIfStatesMatch(
                    expectedStates = expectedAbsentStates(entry.afterImage) +
                        (JOURNAL_KEY to snapshot) + reservationSnapshots + (ORIGINAL_SOURCE_KEY to original),
                    valuesToPut = emptyMap(),
                    keysToRemove = setOf(JOURNAL_KEY) + reservationSnapshots.keys +
                        if (original == null) emptySet() else setOf(ORIGINAL_SOURCE_KEY),
                )
            }
            if (!removed) fail(FailureReason.CONFLICT, "Portable cohort journal changed")
            durabilityBoundary {
                check(!preferences.hasKey(JOURNAL_KEY) && sourceNamespaceKeys().isEmpty() &&
                    reservationSnapshots.keys.none(preferences::hasKey)) {
                    "Portable cohort reservation remains after abandon"
                }
            }
        } finally {
            entry.clearSecrets()
        }
    }

    private fun loadValidated(): Entry? {
        val exists = preferences.hasKey(JOURNAL_KEY)
        if (!exists) {
            if (preferences.hasKeyWithPrefix(RESERVATION_PREFIX) || sourceNamespaceKeys().isNotEmpty()) {
                fail(FailureReason.CONFLICT, "An orphaned portable cohort reservation requires reconciliation")
            }
            return null
        }
        val stored = preferences.getDecryptedString(JOURNAL_KEY)
            ?: fail(
                FailureReason.MALFORMED_STORED_JOURNAL,
                "Portable cohort journal is unreadable",
            )
        val entry = decode(stored)
        try {
            requireNoConflicts(entry.afterImage, ownToken = entry.token)
            requireMatchingSidecar(entry)
            return entry
        } catch (failure: Throwable) {
            entry.clearSecrets()
            throw failure
        }
    }

    private fun requireNoConflicts(
        afterImage: PortableWalletCohortAfterImage.Record,
        ownToken: Token? = null,
    ): Map<String, EncryptedPreferenceSnapshot> {
        val journalPresent = ownToken == null && preferences.hasKey(JOURNAL_KEY)
        val priorMutationPresent = preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
        val tonMutationPresent = singleWalletJournal.hasTonConnectMutationJournal()
        if (journalPresent || priorMutationPresent || tonMutationPresent) {
            fail(FailureReason.CONFLICT, "A wallet mutation journal is active")
        }
        if (ownToken == null && sourceNamespaceKeys().isNotEmpty()) {
            fail(FailureReason.CONFLICT, "A portable original-source namespace is occupied")
        }
        afterImage.localMetaIdsCopy().forEach { id ->
            if (singleWalletJournal.hasSecretNamespace(id)) {
                fail(FailureReason.CONFLICT, "A target wallet namespace is occupied")
            }
        }
        afterImage.destinations.mapNotNull { it.candidateSecretKey }.forEach { key ->
            if (preferences.hasKey(key)) {
                fail(FailureReason.CONFLICT, "A target secret key is occupied")
            }
        }
        val keys = reservationKeys(afterImage)
        if (ownToken == null) {
            if (preferences.hasKeyWithPrefix(RESERVATION_PREFIX)) {
                fail(FailureReason.CONFLICT, "A portable cohort reservation is already present")
            }
            return emptyMap()
        }
        val expected = reservationValue(ownToken)
        return keys.associateWith { key ->
            val snapshot = preferences.getDecryptedStringSnapshot(key)
                ?: fail(FailureReason.CONFLICT, "A target wallet ID reservation is missing")
            if (snapshot.plaintext != expected) {
                fail(FailureReason.CONFLICT, "A target wallet ID reservation changed")
            }
            snapshot
        }
    }

    private fun expectedAbsentStates(
        afterImage: PortableWalletCohortAfterImage.Record,
    ): Map<String, EncryptedPreferenceSnapshot?> = buildMap {
        put(JOURNAL_KEY, null)
        put(ORIGINAL_SOURCE_KEY, null)
        put(WalletSecretMutationJournalStore.JOURNAL_KEY, null)
        put(TonConnectStorageKeys.MUTATION_JOURNAL_KEY, null)
        reservationKeys(afterImage).forEach { put(it, null) }
        afterImage.destinations.mapNotNull { it.candidateSecretKey }.forEach { put(it, null) }
    }

    private fun reservationKeys(afterImage: PortableWalletCohortAfterImage.Record): Set<String> =
        afterImage.localMetaIdsCopy().mapTo(linkedSetOf(), ::reservationKey)

    private fun reservationValue(token: Token): String = "${token.operationId}:${token.afterImageSha256}"

    private fun sourceNamespaceKeys(): Set<String> = preferences.keysWithPrefixes(
        prefixes = setOf(ORIGINAL_SOURCE_PREFIX),
        maxResultCount = MAX_SOURCE_KEYS,
        maxKeyBytes = MAX_SOURCE_KEY_BYTES,
        maxTotalKeyBytes = MAX_SOURCE_KEY_BYTES * MAX_SOURCE_KEYS,
        failOnOversizedMatch = true,
    )

    private fun requireMatchingSidecar(entry: Entry): EncryptedPreferenceSnapshot? {
        val keys = sourceNamespaceKeys()
        if (keys != emptySet<String>() && keys != setOf(ORIGINAL_SOURCE_KEY)) {
            fail(FailureReason.CONFLICT, "Portable original-source namespace changed")
        }
        val snapshot = preferences.getDecryptedStringSnapshot(ORIGINAL_SOURCE_KEY)
        if (snapshot == null) {
            if (entry.journalVersion == VERSION) {
                fail(FailureReason.CONFLICT, "Portable original-source sidecar is missing")
            }
            return null
        }
        val expected = PortableWalletOriginalSourceSidecar.encode(entry.afterImage, entry.token)
        if (snapshot.plaintext != expected) {
            fail(FailureReason.CONFLICT, "Portable original-source sidecar differs from its journal")
        }
        return snapshot
    }

    private fun encode(operationId: String, afterImage: ByteArray, version: Int): String {
        val prefix = ByteBuffer.allocate(HEADER_BYTES + afterImage.size).apply {
            put(MAGIC)
            put(version.toByte())
            put(operationId.toByteArray(Charsets.US_ASCII))
            putInt(afterImage.size)
            put(afterImage)
        }.array()
        try {
            val digest = MessageDigest.getInstance("SHA-256").digest(prefix)
            val wire = prefix + digest
            try {
                return Base64.getEncoder().encodeToString(wire)
            } finally {
                wire.fill(0)
                digest.fill(0)
            }
        } finally {
            prefix.fill(0)
        }
    }

    private fun decode(stored: String): Entry {
        if (stored.isEmpty() || stored.length > MAX_BASE64_CHARS) {
            fail(FailureReason.MALFORMED_STORED_JOURNAL, "Portable cohort journal size is invalid")
        }
        val wire = try {
            Base64.getDecoder().decode(stored)
        } catch (failure: IllegalArgumentException) {
            fail(FailureReason.MALFORMED_STORED_JOURNAL, "Portable cohort journal is malformed", failure)
        }
        try {
            return decodeWire(wire, stored)
        } catch (failure: JournalException) {
            throw failure
        } catch (failure: Exception) {
            throw JournalException(FailureReason.MALFORMED_STORED_JOURNAL, "Portable cohort journal is invalid", failure)
        } finally {
            wire.fill(0)
        }
    }

    private fun decodeWire(wire: ByteArray, stored: String): Entry {
        if (
            wire.size !in HEADER_BYTES + 1 + SHA256_BYTES..MAX_WIRE_BYTES ||
            Base64.getEncoder().encodeToString(wire) != stored
        ) {
            fail(FailureReason.MALFORMED_STORED_JOURNAL, "Portable cohort journal wire is invalid")
        }
        val reader = ByteBuffer.wrap(wire)
        val foundMagic = ByteArray(MAGIC.size)
        reader.get(foundMagic)
        if (!foundMagic.contentEquals(MAGIC)) {
            fail(FailureReason.MALFORMED_STORED_JOURNAL, "Portable cohort journal magic is invalid")
        }
        val version = reader.get().toInt()
        if (version != LEGACY_VERSION && version != VERSION) {
            fail(FailureReason.UNSUPPORTED_VERSION, "Portable cohort journal version is unsupported")
        }
        val idBytes = ByteArray(UUID_CHARS)
        reader.get(idBytes)
        val operationId = idBytes.toString(Charsets.US_ASCII)
        idBytes.fill(0)
        if (!isCanonicalOperationId(operationId)) {
            fail(FailureReason.MALFORMED_STORED_JOURNAL, "Portable cohort operation ID is invalid")
        }
        val afterLength = reader.int
        if (afterLength !in 1..MAX_AFTER_IMAGE_BYTES || reader.remaining() != afterLength + SHA256_BYTES) {
            fail(FailureReason.MALFORMED_STORED_JOURNAL, "Portable cohort after-image length is invalid")
        }
        val afterBytes = ByteArray(afterLength)
        reader.get(afterBytes)
        val expectedDigest = MessageDigest.getInstance("SHA-256").apply {
            update(wire, 0, HEADER_BYTES + afterLength)
        }.digest()
        val actualDigest = ByteArray(SHA256_BYTES)
        reader.get(actualDigest)
        try {
            if (!MessageDigest.isEqual(expectedDigest, actualDigest)) {
                fail(FailureReason.MALFORMED_STORED_JOURNAL, "Portable cohort journal digest differs")
            }
            val afterImage = PortableWalletCohortAfterImage.decode(afterBytes)
            return Entry(Token(operationId, digestHex(afterBytes)), afterImage, version)
        } finally {
            afterBytes.fill(0)
            expectedDigest.fill(0)
            actualDigest.fill(0)
        }
    }

    private fun validateOperationId(operationId: String) {
        if (!isCanonicalOperationId(operationId)) {
            throw JournalException(FailureReason.INVALID_ARGUMENT, "Portable cohort operation ID is invalid")
        }
    }

    private fun isCanonicalOperationId(operationId: String): Boolean {
        val parsed = runCatching { UUID.fromString(operationId) }.getOrNull() ?: return false
        return operationId.length == UUID_CHARS && parsed.toString() == operationId &&
            parsed.version() == UUID_V4 && parsed.variant() == RFC_UUID_VARIANT
    }

    private fun digestHex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        try {
            return digest.joinToString("") { "%02x".format(it) }
        } finally {
            digest.fill(0)
        }
    }

    private inline fun <T> durabilityBoundary(action: () -> T): T {
        return try {
            action()
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            fail(FailureReason.AMBIGUOUS_DURABILITY, "Portable cohort durability is uncertain", failure)
        }
    }

    private fun fail(
        reason: FailureReason,
        message: String,
        cause: Throwable? = null,
    ): Nothing {
        throw JournalException(reason, message, cause)
    }

    internal companion object {
        const val JOURNAL_KEY = "portable_wallet_cohort_journal_v1"
        private const val ORIGINAL_SOURCE_PREFIX = "portable_wallet_cohort_original_source_"
        const val ORIGINAL_SOURCE_KEY = "${ORIGINAL_SOURCE_PREFIX}v1"
        private const val RESERVATION_PREFIX = "portable_wallet_cohort_id_reservation_v1:"
        private val MAGIC = "FPWCJ001".toByteArray(Charsets.US_ASCII)
        private const val LEGACY_VERSION = 1
        private const val VERSION = 2
        private const val MAX_SOURCE_KEYS = 16
        private const val MAX_SOURCE_KEY_BYTES = 128
        private const val UUID_CHARS = 36
        private const val UUID_V4 = 4
        private const val RFC_UUID_VARIANT = 2
        private const val SHA256_BYTES = 32
        private const val MAX_AFTER_IMAGE_BYTES = 8 + 1 + 2 + 128 * Long.SIZE_BYTES + 4 + 256 * 1024
        private const val HEADER_BYTES = 8 + 1 + UUID_CHARS + Int.SIZE_BYTES
        private const val MAX_WIRE_BYTES = HEADER_BYTES + MAX_AFTER_IMAGE_BYTES + SHA256_BYTES
        private const val MAX_BASE64_CHARS = (MAX_WIRE_BYTES + 2) / 3 * 4
        private val HEX_SHA256 = Regex("[0-9a-f]{64}")

        fun reservationKey(metaId: Long): String {
            require(metaId > 0L) { "Portable cohort reservation requires a positive wallet ID" }
            return "$RESERVATION_PREFIX$metaId"
        }
    }
}
