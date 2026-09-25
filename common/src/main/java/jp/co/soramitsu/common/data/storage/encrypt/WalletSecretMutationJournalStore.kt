package jp.co.soramitsu.common.data.storage.encrypt

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.StringReader
import java.io.StringWriter
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId

/**
 * Durable intent record for cross-store wallet mutations.
 *
 * The journal and all new secret plaintexts are encrypted and committed in one
 * [EncryptedPreferences.replaceEncryptedStringsDurably] call. Database callers
 * can therefore replay the public after-image after process death without ever
 * publishing an address before its final signing material is durable.
 *
 * This store deliberately does not mutate the database or interpret secret
 * payloads. Callers must cryptographically bind each decoded secret to the
 * public after-image before applying or finalizing a database mutation.
 */
class WalletSecretMutationJournalStore(
    private val encryptedPreferences: EncryptedPreferences
) {

    enum class Operation {
        CREATE,
        ADD_EVM,
        DELETE
    }

    enum class SubstrateCryptoType {
        SR25519,
        ED25519,
        ECDSA
    }

    /**
     * Canonical public database state to apply during reconciliation.
     *
     * Byte fields use lowercase, prefix-free, even-length hexadecimal. This
     * keeps the JSON independent from platform Base64 variants and prevents
     * multiple encodings of the same public value.
     */
    data class PublicAfterImage(
        val name: String,
        val substratePublicKeyHex: String?,
        val substrateAccountIdHex: String?,
        val substrateCryptoType: SubstrateCryptoType?,
        val ethereumPublicKeyHex: String?,
        val ethereumAddressHex: String?,
        val tonPublicKeyHex: String?,
        val isSelected: Boolean,
        val position: Int,
        val isBackedUp: Boolean,
        val googleBackupAddress: String?,
        val initialized: Boolean
    )

    data class Journal(
        val version: Int = CURRENT_VERSION,
        val operationId: String,
        val operation: Operation,
        val metaId: Long,
        val beforeImage: PublicAfterImage?,
        val afterImage: PublicAfterImage?,
        val selectedMetaIdAfterDelete: Long?,
        val chainAccountIdsHex: Set<String>,
        val secretKeysToPut: Set<String>,
        val secretKeysToRemove: Set<String>
    )

    data class StagedMutation(
        val journal: Journal,
        val secretPlaintexts: Map<String, String>
    )

    enum class FailureReason {
        INVALID_ARGUMENT,
        CONFLICT,
        MALFORMED_STORED_JOURNAL,
        UNSUPPORTED_VERSION,
        AMBIGUOUS_DURABILITY
    }

    class JournalException(
        val reason: FailureReason,
        message: String,
        cause: Throwable? = null
    ) : IllegalStateException(message, cause)

    /**
     * Validates and durably stages one mutation intent and its exact secrets.
     *
     * Existing target secret keys are never overwritten. A failed or ambiguous
     * durability boundary is propagated and must be reconciled via [load];
     * this method never retries a write.
     */
    @Synchronized
    fun stage(
        journal: Journal,
        finalSecretPlaintexts: Map<String, String>
    ) {
        validate(journal)
        validateFinalSecretPlaintexts(
            journal = journal,
            finalSecretPlaintexts = finalSecretPlaintexts,
            failureReason = FailureReason.INVALID_ARGUMENT
        )

        if (encryptedPreferences.hasKey(JOURNAL_KEY)) {
            fail(
                FailureReason.CONFLICT,
                "A wallet-secret mutation journal is already active"
            )
        }

        journal.secretKeysToPut.forEach { secretKey ->
            if (encryptedPreferences.hasKey(secretKey)) {
                fail(
                    FailureReason.CONFLICT,
                    "A target wallet-secret key already exists"
                )
            }
        }

        val encodedJournal = encode(journal)
        val valuesToPut = buildMap {
            put(JOURNAL_KEY, encodedJournal)
            finalSecretPlaintexts
                .toSortedMap()
                .forEach { (key, value) -> put(key, value) }
        }

        durabilityBoundary("Unable to determine whether the wallet mutation was staged") {
            encryptedPreferences.requireDurableStorageHealthy()
            encryptedPreferences.replaceEncryptedStringsDurably(
                valuesToPut = valuesToPut,
                keysToRemove = emptySet()
            )

            val storedMutation = loadValidated()
                ?: error("The committed wallet mutation journal is missing")
            check(storedMutation.journal == journal) {
                "The committed wallet mutation journal differs from its intent"
            }
            check(storedMutation.secretPlaintexts == finalSecretPlaintexts) {
                "A committed wallet secret differs from its intended plaintext"
            }
        }
    }

    /**
     * Loads and strictly validates the active journal and all staged secrets.
     *
     * Returns `null` only when the journal preference key is absent. An existing
     * but unreadable, malformed, incomplete, or unsupported journal fails closed.
     */
    @Synchronized
    fun load(): Journal? {
        return loadValidated()?.journal
    }

    /**
     * Loads the active journal together with the exact staged plaintexts.
     *
     * Startup reconciliation must cryptographically bind these plaintexts to
     * the journal's public image before it publishes or accepts a Room row.
     */
    @Synchronized
    fun loadStagedMutation(): StagedMutation? {
        return loadValidated()
    }

    /**
     * Validates an in-memory journal without reading or writing storage.
     */
    fun validate(journal: Journal) {
        validateJournal(
            journal = journal,
            failureReason = FailureReason.INVALID_ARGUMENT
        )
    }

    /**
     * Detects active or quarantined encrypted material owned by [metaId].
     *
     * Wallet creation must call this before reserving an explicit id. It also
     * catches orphaned V2 chain-account and wallet-scoped TON Connect secrets,
     * not only the known root keys.
     */
    fun hasSecretNamespace(metaId: Long): Boolean {
        if (metaId <= 0) {
            fail(
                FailureReason.INVALID_ARGUMENT,
                "A wallet-secret namespace requires a positive wallet id"
            )
        }

        val tonConnectPrefix = TonConnectStorageKeys.scopedPrefix(metaId)
        val quarantinedTonConnectPrefix =
            WalletSecretQuarantine.keyFor(tonConnectPrefix)
        return encryptedPreferences.hasKeyWithPrefix("$metaId:") ||
            encryptedPreferences.hasKeyWithPrefix("$QUARANTINE_PREFIX$metaId:") ||
            encryptedPreferences.hasKeyWithPrefix(
                "${WalletPublicIdentityRecovery.KEY_PREFIX}$metaId:"
            ) ||
            encryptedPreferences.hasKeyWithPrefix(
                "$QUARANTINE_PREFIX$LEGACY_V1_META_PREFIX$metaId"
            ) ||
            encryptedPreferences.hasKeyWithPrefix(
                "$QUARANTINE_PREFIX$LEGACY_V04_META_PREFIX$metaId"
            ) ||
            encryptedPreferences.hasKeyWithPrefix(tonConnectPrefix) ||
            encryptedPreferences.hasKeyWithPrefix(quarantinedTonConnectPrefix)
    }

    fun hasTonConnectMutationJournal(): Boolean {
        return encryptedPreferences.hasKey(
            TonConnectStorageKeys.MUTATION_JOURNAL_KEY
        )
    }

    /**
     * Enumerates active and quarantined wallet-scoped TON Connect keys without
     * decrypting their values.
     *
     * Database rows remain the authority for shared legacy keys, but scoped
     * keys encode their owning wallet id and can therefore be safely recovered
     * even when their Room row is missing. Every returned key is bounded and
     * required to use the canonical scoped-key shape before it may enter a
     * durable wallet-deletion journal.
     */
    fun tonConnectScopedDeletionKeys(metaId: Long): Set<String> {
        if (metaId <= 0L) {
            fail(
                FailureReason.INVALID_ARGUMENT,
                "A TON Connect scoped-key inventory requires a positive wallet id"
            )
        }

        val activePrefix = TonConnectStorageKeys.scopedPrefix(metaId)
        val quarantinePrefix = WalletSecretQuarantine.keyFor(activePrefix)
        val candidates = try {
            encryptedPreferences.keysWithPrefixes(
                prefixes = setOf(activePrefix, quarantinePrefix),
                maxResultCount = MAX_TON_CONNECT_SCOPED_NAMESPACE_KEYS,
                maxKeyBytes = MAX_TON_CONNECT_SCOPED_KEY_BYTES,
                maxTotalKeyBytes = MAX_TON_CONNECT_SCOPED_NAMESPACE_BYTES,
                failOnOversizedMatch = true
            )
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw JournalException(
                reason = FailureReason.CONFLICT,
                message = "Unable to enumerate the bounded TON Connect secret namespace",
                cause = failure
            )
        }

        if (candidates.size > MAX_TON_CONNECT_SCOPED_NAMESPACE_KEYS) {
            fail(
                FailureReason.CONFLICT,
                "A wallet owns too many scoped TON Connect secret keys"
            )
        }

        var totalKeyBytes = 0
        return buildSet {
            candidates.forEach { key ->
                if (key.length > MAX_TON_CONNECT_SCOPED_KEY_BYTES) {
                    fail(
                        FailureReason.CONFLICT,
                        "A scoped TON Connect secret key exceeds its safe bound"
                    )
                }
                val keyBytes = key.toByteArray(Charsets.UTF_8).size
                if (
                    keyBytes > MAX_TON_CONNECT_SCOPED_KEY_BYTES ||
                    totalKeyBytes >
                    MAX_TON_CONNECT_SCOPED_NAMESPACE_BYTES - keyBytes
                ) {
                    fail(
                        FailureReason.CONFLICT,
                        "A scoped TON Connect secret namespace exceeds its safe bounds"
                    )
                }
                totalKeyBytes += keyBytes

                val activeKey = when {
                    key.startsWith(activePrefix) -> key
                    key.startsWith(quarantinePrefix) -> {
                        activePrefix + key.removePrefix(quarantinePrefix)
                    }
                    else -> fail(
                        FailureReason.CONFLICT,
                        "A TON Connect secret key escaped its wallet namespace"
                    )
                }
                if (
                    !TonConnectStorageKeys.isScopedKeyForMeta(activeKey, metaId) ||
                    (
                        key.startsWith(quarantinePrefix) &&
                            key != WalletSecretQuarantine.keyFor(activeKey)
                        )
                ) {
                    fail(
                        FailureReason.CONFLICT,
                        "A wallet contains a malformed scoped TON Connect secret key"
                    )
                }
                add(key)
            }
        }
    }

    /**
     * Derives the complete bounded removal set for a wallet deletion journal.
     *
     * Callers cannot redirect cleanup to unrelated preferences: every returned
     * key is deterministically bound to the wallet id, canonical chain-account
     * ids, or the wallet's legacy public-key quarantine.
     */
    fun deletionSecretKeys(
        metaId: Long,
        beforeImage: PublicAfterImage,
        chainAccountIdsHex: Set<String>,
        tonConnectSecretKeys: Set<String> = emptySet()
    ): Set<String> {
        if (metaId <= 0) {
            fail(
                FailureReason.INVALID_ARGUMENT,
                "A wallet deletion requires a positive wallet id"
            )
        }
        validateAfterImage(beforeImage, FailureReason.INVALID_ARGUMENT)
        if (chainAccountIdsHex.size > MAX_CHAIN_ACCOUNT_IDS) {
            fail(
                FailureReason.INVALID_ARGUMENT,
                "A wallet deletion references too many chain accounts"
            )
        }
        chainAccountIdsHex.forEach { accountIdHex ->
            validateCanonicalHex(
                value = accountIdHex,
                fieldName = "chain account id",
                allowedByteRange = 20..64,
                failureReason = FailureReason.INVALID_ARGUMENT
            )
        }
        validateTonConnectDeletionKeys(
            keys = tonConnectSecretKeys,
            metaId = metaId,
            failureReason = FailureReason.INVALID_ARGUMENT
        )

        val deterministicKeys = deriveDeletionSecretKeys(
            metaId = metaId,
            substratePublicKeyHex = beforeImage.substratePublicKeyHex,
            chainAccountIdsHex = chainAccountIdsHex
        )
        val legacyKeys = discoverOwnedLegacySecretKeys(
            substrateAccountIdHex = beforeImage.substrateAccountIdHex
        )
        val ownedV2Keys = discoverOwnedV2SecretKeys(
            metaId = metaId,
            knownChainAccountIdsHex = chainAccountIdsHex,
            failureReason = FailureReason.INVALID_ARGUMENT
        )

        return deterministicKeys + legacyKeys + ownedV2Keys +
            tonConnectSecretKeys
    }

    /**
     * Clears only the exact previously validated staged mutation after database
     * reconciliation succeeds.
     *
     * Final secret keys remain untouched. Every staged-secret snapshot is bound
     * into the same compare-and-swap as the journal removal, so a secret writer
     * cannot replace validated signing material between database publication and
     * journal finalization.
     */
    @Synchronized
    fun clear(expectedMutation: StagedMutation) {
        validateJournal(
            journal = expectedMutation.journal,
            failureReason = FailureReason.INVALID_ARGUMENT
        )
        validateFinalSecretPlaintexts(
            journal = expectedMutation.journal,
            finalSecretPlaintexts = expectedMutation.secretPlaintexts,
            failureReason = FailureReason.INVALID_ARGUMENT
        )

        val journalSnapshot = encryptedPreferences
            .getDecryptedStringSnapshot(JOURNAL_KEY)
            ?: fail(
                FailureReason.CONFLICT,
                "There is no wallet-secret mutation journal to clear"
            )
        val current = loadValidated() ?: fail(
            FailureReason.CONFLICT,
            "There is no wallet-secret mutation journal to clear"
        )
        val snapshotJournal = normalizeLegacyPublicScopedDeleteJournal(
            decode(journalSnapshot.plaintext)
        )
        validateJournal(
            journal = snapshotJournal,
            failureReason = FailureReason.MALFORMED_STORED_JOURNAL
        )
        if (snapshotJournal != current.journal) {
            fail(
                FailureReason.CONFLICT,
                "The wallet-secret mutation journal changed while it was being cleared"
            )
        }
        if (current != expectedMutation) {
            fail(
                FailureReason.CONFLICT,
                "The active wallet-secret mutation differs from the validated operation"
            )
        }
        if (current.journal.operation == Operation.DELETE) {
            fail(
                FailureReason.CONFLICT,
                "A wallet deletion journal requires secret-removal completion"
            )
        }

        val expectedStates =
            linkedMapOf<String, EncryptedPreferenceSnapshot?>(
                JOURNAL_KEY to journalSnapshot
            )
        val keysToRemove = linkedSetOf(JOURNAL_KEY)
        current.secretPlaintexts.toSortedMap().forEach {
            (activeSecretKey, expectedPlaintext) ->
            val secretSnapshot =
                encryptedPreferences.getDecryptedStringSnapshot(activeSecretKey)
                    ?: fail(
                        FailureReason.CONFLICT,
                        "A validated staged wallet secret disappeared before clear"
                    )
            if (secretSnapshot.plaintext != expectedPlaintext) {
                fail(
                    FailureReason.CONFLICT,
                    "A staged wallet secret changed after public-image validation"
                )
            }
            expectedStates[activeSecretKey] = secretSnapshot

            val markerKey = WalletPublicIdentityRecovery.keyFor(
                metaId = current.journal.metaId,
                activeSecretKey = activeSecretKey
            )
            val markerSnapshot =
                encryptedPreferences.getDecryptedStringSnapshot(markerKey)
            if (
                markerSnapshot != null &&
                markerSnapshot.plaintext !=
                WalletPublicIdentityRecovery.MARKER_VALUE
            ) {
                fail(
                    FailureReason.CONFLICT,
                    "A staged wallet secret has an invalid recovery marker"
                )
            }
            expectedStates[markerKey] = markerSnapshot
            if (markerSnapshot != null) {
                keysToRemove += markerKey
            }
        }

        durabilityBoundary("Unable to determine whether the wallet mutation journal was cleared") {
            encryptedPreferences.requireDurableStorageHealthy()
            encryptedPreferences.replaceEncryptedStringsForStatesDurably(
                expectedStates = expectedStates,
                valuesToPut = emptyMap(),
                keysToRemove = keysToRemove
            )
            check(keysToRemove.none(encryptedPreferences::hasKey)) {
                "The wallet mutation journal or stale recovery marker remains after clear"
            }
        }
    }

    /**
     * Atomically removes the exact secrets recorded by a matching deletion and
     * clears its journal. This is the only valid finalizer for [Operation.DELETE].
     *
     * The database deletion must already be committed and verified by the
     * caller. Retrying after process death is safe: absent removal keys are
     * accepted, while a stale operation id or non-delete journal fails closed.
     */
    @Synchronized
    fun completeDelete(expectedOperationId: String) {
        validateOperationId(
            operationId = expectedOperationId,
            failureReason = FailureReason.INVALID_ARGUMENT
        )

        val current = loadValidated()?.journal ?: fail(
            FailureReason.CONFLICT,
            "There is no wallet-secret mutation journal to complete"
        )
        if (current.operationId != expectedOperationId) {
            fail(
                FailureReason.CONFLICT,
                "The active wallet-secret mutation journal belongs to another operation"
            )
        }
        if (current.operation != Operation.DELETE) {
            fail(
                FailureReason.CONFLICT,
                "Only a wallet deletion journal can remove existing wallet secrets"
            )
        }

        // Version-one journals emitted by an earlier build may not list V2
        // chain secrets whose chain-account row was already orphaned. Discover
        // those exact names again at replay time before clearing the journal.
        val replayDiscoveredV2Keys = discoverOwnedV2SecretKeys(
            metaId = current.metaId,
            knownChainAccountIdsHex = current.chainAccountIdsHex,
            failureReason = FailureReason.MALFORMED_STORED_JOURNAL
        )
        val keysToRemove =
            current.secretKeysToRemove + replayDiscoveredV2Keys + JOURNAL_KEY
        durabilityBoundary("Unable to determine whether wallet deletion secrets were removed") {
            encryptedPreferences.requireDurableStorageHealthy()
            encryptedPreferences.replaceEncryptedStringsDurably(
                valuesToPut = emptyMap(),
                keysToRemove = keysToRemove
            )
            check(keysToRemove.none(encryptedPreferences::hasKey)) {
                "A wallet deletion secret or journal remains after durable completion"
            }
        }
    }

    private fun loadValidated(): StagedMutation? {
        val journalExists = try {
            encryptedPreferences.hasKey(JOURNAL_KEY)
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw JournalException(
                reason = FailureReason.MALFORMED_STORED_JOURNAL,
                message = "Unable to inspect the wallet-secret mutation journal",
                cause = failure
            )
        }
        if (!journalExists) return null

        val encodedJournal = try {
            encryptedPreferences.getDecryptedString(JOURNAL_KEY)
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw JournalException(
                reason = FailureReason.MALFORMED_STORED_JOURNAL,
                message = "Unable to read the wallet-secret mutation journal",
                cause = failure
            )
        }
        if (encodedJournal.isNullOrEmpty()) {
            fail(
                FailureReason.MALFORMED_STORED_JOURNAL,
                "The wallet-secret mutation journal is empty or unreadable"
            )
        }
        if (encodedJournal.length > MAX_JOURNAL_PLAINTEXT_CHARS) {
            fail(
                FailureReason.MALFORMED_STORED_JOURNAL,
                "The wallet-secret mutation journal exceeds its safe size limit"
            )
        }

        val journal = normalizeLegacyPublicScopedDeleteJournal(
            decode(encodedJournal)
        )
        validateJournal(
            journal = journal,
            failureReason = FailureReason.MALFORMED_STORED_JOURNAL
        )

        val secretPlaintexts = linkedMapOf<String, String>()
        journal.secretKeysToPut.sorted().forEach { secretKey ->
            val secretExists = try {
                encryptedPreferences.hasKey(secretKey)
            } catch (failure: WalletSecureStorageUnavailableException) {
                throw failure
            } catch (failure: Exception) {
                throw JournalException(
                    reason = FailureReason.MALFORMED_STORED_JOURNAL,
                    message = "Unable to inspect a staged wallet secret",
                    cause = failure
                )
            }
            if (!secretExists) {
                fail(
                    FailureReason.MALFORMED_STORED_JOURNAL,
                    "A staged wallet secret referenced by the journal is missing"
                )
            }

            val secretPlaintext = try {
                encryptedPreferences.getDecryptedString(secretKey)
            } catch (failure: WalletSecureStorageUnavailableException) {
                throw failure
            } catch (failure: Exception) {
                throw JournalException(
                    reason = FailureReason.MALFORMED_STORED_JOURNAL,
                    message = "Unable to read a staged wallet secret",
                    cause = failure
                )
            }
            if (
                secretPlaintext.isNullOrEmpty() ||
                secretPlaintext.length > MAX_SECRET_PLAINTEXT_CHARS
            ) {
                fail(
                    FailureReason.MALFORMED_STORED_JOURNAL,
                    "A staged wallet secret is empty, unreadable, or oversized"
                )
            }
            secretPlaintexts[secretKey] = secretPlaintext
        }

        return StagedMutation(
            journal = journal,
            secretPlaintexts = secretPlaintexts
        )
    }

    /**
     * Early version-one delete journals used one public-key-scoped V1 marker.
     * A duplicate wallet could therefore share that removal key. Validate the
     * historical shape first, then drop only that ambiguous key and add the
     * current meta-scoped deterministic keys before replay.
     */
    private fun normalizeLegacyPublicScopedDeleteJournal(
        journal: Journal
    ): Journal {
        if (journal.operation != Operation.DELETE) return journal
        val beforeImage = journal.beforeImage ?: return journal
        val publicKeyHex = beforeImage.substratePublicKeyHex ?: return journal
        val legacyPublicMarker =
            "$QUARANTINE_PREFIX$LEGACY_V1_PUBLIC_PREFIX$publicKeyHex"
        if (legacyPublicMarker !in journal.secretKeysToRemove) return journal

        val legacyDeterministic = deriveLegacyDeletionSecretKeys(
            metaId = journal.metaId,
            substratePublicKeyHex = publicKeyHex,
            chainAccountIdsHex = journal.chainAccountIdsHex
        )
        requireValidDeletionSecretKeys(
            actual = journal.secretKeysToRemove,
            deterministic = legacyDeterministic,
            metaId = journal.metaId,
            chainAccountIdsHex = journal.chainAccountIdsHex,
            substrateAccountIdHex = beforeImage.substrateAccountIdHex,
            failureReason = FailureReason.MALFORMED_STORED_JOURNAL
        )

        return journal.copy(
            secretKeysToRemove =
            (journal.secretKeysToRemove - legacyPublicMarker) +
                deriveDeletionSecretKeys(
                    metaId = journal.metaId,
                    substratePublicKeyHex = publicKeyHex,
                    chainAccountIdsHex = journal.chainAccountIdsHex
                )
        )
    }

    private fun validateJournal(
        journal: Journal,
        failureReason: FailureReason
    ) {
        if (journal.version != CURRENT_VERSION) {
            fail(
                FailureReason.UNSUPPORTED_VERSION,
                "Unsupported wallet-secret mutation journal version"
            )
        }
        validateOperationId(journal.operationId, failureReason)
        if (journal.metaId <= 0) {
            fail(failureReason, "A wallet-secret mutation requires a positive wallet id")
        }
        if (journal.secretKeysToPut.size > MAX_SECRET_KEYS_TO_PUT) {
            fail(failureReason, "A wallet-secret mutation adds too many secret keys")
        }
        if (journal.secretKeysToRemove.size > MAX_SECRET_KEYS_TO_REMOVE) {
            fail(failureReason, "A wallet-secret mutation removes too many secret keys")
        }
        if (journal.chainAccountIdsHex.size > MAX_CHAIN_ACCOUNT_IDS) {
            fail(failureReason, "A wallet-secret mutation references too many chain accounts")
        }
        journal.chainAccountIdsHex.forEach { accountIdHex ->
            validateCanonicalHex(
                value = accountIdHex,
                fieldName = "chain account id",
                allowedByteRange = 20..64,
                failureReason = failureReason
            )
        }

        when (journal.operation) {
            Operation.CREATE -> {
                if (journal.beforeImage != null) {
                    fail(
                        failureReason,
                        "A wallet creation journal cannot contain a public before-image"
                    )
                }
                val afterImage = journal.afterImage ?: fail(
                    failureReason,
                    "A wallet creation journal requires a public after-image"
                )
                validateAfterImage(afterImage, failureReason)
                requireCommonNonDeleteFields(journal, failureReason)

                val expectedSecretKeys = buildSet {
                    if (afterImage.substratePublicKeyHex != null) {
                        add(secretKey(journal.metaId, SUBSTRATE_SECRET_SUFFIX))
                    }
                    if (afterImage.ethereumPublicKeyHex != null) {
                        add(secretKey(journal.metaId, ETHEREUM_SECRET_SUFFIX))
                    }
                    if (afterImage.tonPublicKeyHex != null) {
                        add(secretKey(journal.metaId, TON_SECRET_SUFFIX))
                    }
                }
                if (expectedSecretKeys.isEmpty()) {
                    fail(
                        failureReason,
                        "A wallet creation journal must contain at least one ecosystem"
                    )
                }
                requireExactSecretKeys(
                    actual = journal.secretKeysToPut,
                    expected = expectedSecretKeys,
                    failureReason = failureReason
                )
                requireExactSecretKeys(
                    actual = journal.secretKeysToRemove,
                    expected = emptySet(),
                    failureReason = failureReason
                )
            }

            Operation.ADD_EVM -> {
                val beforeImage = journal.beforeImage ?: fail(
                    failureReason,
                    "An EVM addition journal requires a public before-image"
                )
                val afterImage = journal.afterImage ?: fail(
                    failureReason,
                    "An EVM addition journal requires a public after-image"
                )
                validateAfterImage(beforeImage, failureReason)
                validateAfterImage(afterImage, failureReason)
                requireCommonNonDeleteFields(journal, failureReason)
                validateEvmOnlyTransition(
                    beforeImage = beforeImage,
                    afterImage = afterImage,
                    failureReason = failureReason
                )
                if (
                    beforeImage.ethereumPublicKeyHex != null ||
                    beforeImage.ethereumAddressHex != null ||
                    afterImage.ethereumPublicKeyHex == null ||
                    afterImage.ethereumAddressHex == null
                ) {
                    fail(
                        failureReason,
                        "An EVM addition journal requires a previously absent Ethereum identity"
                    )
                }
                requireExactSecretKeys(
                    actual = journal.secretKeysToPut,
                    expected = setOf(secretKey(journal.metaId, ETHEREUM_SECRET_SUFFIX)),
                    failureReason = failureReason
                )
                requireExactSecretKeys(
                    actual = journal.secretKeysToRemove,
                    expected = emptySet(),
                    failureReason = failureReason
                )
            }

            Operation.DELETE -> {
                val beforeImage = journal.beforeImage ?: fail(
                    failureReason,
                    "A wallet deletion journal requires a public before-image"
                )
                validateAfterImage(beforeImage, failureReason)
                if (journal.afterImage != null) {
                    fail(
                        failureReason,
                        "A wallet deletion journal cannot contain a public after-image"
                    )
                }
                if (
                    journal.selectedMetaIdAfterDelete != null &&
                    (
                        journal.selectedMetaIdAfterDelete <= 0 ||
                            journal.selectedMetaIdAfterDelete == journal.metaId
                        )
                ) {
                    fail(
                        failureReason,
                        "A wallet deletion journal has an invalid selected successor"
                    )
                }
                requireExactSecretKeys(
                    actual = journal.secretKeysToPut,
                    expected = emptySet(),
                    failureReason = failureReason
                )
                requireValidDeletionSecretKeys(
                    actual = journal.secretKeysToRemove,
                    deterministic = deriveDeletionSecretKeys(
                        metaId = journal.metaId,
                        substratePublicKeyHex = beforeImage.substratePublicKeyHex,
                        chainAccountIdsHex = journal.chainAccountIdsHex
                    ),
                    metaId = journal.metaId,
                    chainAccountIdsHex = journal.chainAccountIdsHex,
                    substrateAccountIdHex = beforeImage.substrateAccountIdHex,
                    failureReason = failureReason
                )
            }
        }
    }

    private fun requireCommonNonDeleteFields(
        journal: Journal,
        failureReason: FailureReason
    ) {
        if (journal.selectedMetaIdAfterDelete != null) {
            fail(
                failureReason,
                "A non-deletion journal cannot select a deletion successor"
            )
        }
        if (journal.chainAccountIdsHex.isNotEmpty()) {
            fail(
                failureReason,
                "A non-deletion journal cannot contain chain-account deletion state"
            )
        }
    }

    private fun validateEvmOnlyTransition(
        beforeImage: PublicAfterImage,
        afterImage: PublicAfterImage,
        failureReason: FailureReason
    ) {
        val beforeWithoutMutableEvmFields = beforeImage.copy(
            ethereumPublicKeyHex = null,
            ethereumAddressHex = null,
            isBackedUp = afterImage.isBackedUp
        )
        val afterWithoutMutableEvmFields = afterImage.copy(
            ethereumPublicKeyHex = null,
            ethereumAddressHex = null
        )
        if (beforeWithoutMutableEvmFields != afterWithoutMutableEvmFields) {
            fail(
                failureReason,
                "An EVM addition journal changes unrelated wallet state"
            )
        }
    }

    private fun deriveDeletionSecretKeys(
        metaId: Long,
        substratePublicKeyHex: String?,
        chainAccountIdsHex: Set<String>
    ): Set<String> {
        val activeKeys = buildSet {
            add(secretKey(metaId, ACCESS_SECRET_SUFFIX))
            add(secretKey(metaId, SUBSTRATE_SECRET_SUFFIX))
            add(secretKey(metaId, ETHEREUM_SECRET_SUFFIX))
            add(secretKey(metaId, TON_SECRET_SUFFIX))
            chainAccountIdsHex.forEach { accountIdHex ->
                add("$metaId:$accountIdHex:$ACCESS_SECRET_SUFFIX")
            }
        }

        return buildSet {
            addAll(activeKeys)
            activeKeys.mapTo(this, WalletSecretQuarantine::keyFor)
            activeKeys.mapTo(this) {
                WalletPublicIdentityRecovery.keyFor(metaId, it)
            }
            substratePublicKeyHex?.let { publicKeyHex ->
                add(
                    "$QUARANTINE_PREFIX$LEGACY_V1_META_PREFIX" +
                        "${metaId}_public_$publicKeyHex"
                )
                add(
                    "$QUARANTINE_PREFIX$LEGACY_V04_META_PREFIX" +
                        "${metaId}_public_$publicKeyHex"
                )
            }
        }
    }

    private fun deriveLegacyDeletionSecretKeys(
        metaId: Long,
        substratePublicKeyHex: String?,
        chainAccountIdsHex: Set<String>
    ): Set<String> {
        val activeKeys = buildSet {
            add(secretKey(metaId, ACCESS_SECRET_SUFFIX))
            add(secretKey(metaId, SUBSTRATE_SECRET_SUFFIX))
            add(secretKey(metaId, ETHEREUM_SECRET_SUFFIX))
            add(secretKey(metaId, TON_SECRET_SUFFIX))
            chainAccountIdsHex.forEach { accountIdHex ->
                add("$metaId:$accountIdHex:$ACCESS_SECRET_SUFFIX")
            }
        }

        return buildSet {
            addAll(activeKeys)
            activeKeys.mapTo(this, WalletSecretQuarantine::keyFor)
            substratePublicKeyHex?.let {
                add("$QUARANTINE_PREFIX$LEGACY_V1_PUBLIC_PREFIX$it")
            }
        }
    }

    private fun discoverOwnedLegacySecretKeys(
        substrateAccountIdHex: String?
    ): Set<String> {
        if (substrateAccountIdHex == null) return emptySet()

        val candidates = try {
            encryptedPreferences.keysWithPrefixes(
                prefixes = LEGACY_SECRET_PREFIXES,
                maxResultCount = MAX_LEGACY_SECRET_CANDIDATES,
                maxKeyBytes = MAX_LEGACY_SECRET_KEY_BYTES,
                maxTotalKeyBytes = MAX_LEGACY_SECRET_CANDIDATE_BYTES
            )
        } catch (failure: Exception) {
            throw JournalException(
                reason = FailureReason.INVALID_ARGUMENT,
                message = "Unable to enumerate a bounded legacy wallet-secret namespace",
                cause = failure
            )
        }

        val owned = linkedSetOf<String>()
        candidates.forEach { candidate ->
            when (legacySecretOwnership(candidate, substrateAccountIdHex)) {
                LegacySecretOwnership.OWNED -> {
                    if (owned.size >= MAX_OWNED_LEGACY_SECRET_KEYS) {
                        fail(
                            FailureReason.INVALID_ARGUMENT,
                            "A wallet owns too many legacy secret keys"
                        )
                    }
                    owned += candidate
                }
                LegacySecretOwnership.UNRELATED,
                LegacySecretOwnership.CLEARLY_INVALID -> Unit
                LegacySecretOwnership.AMBIGUOUS -> fail(
                    FailureReason.INVALID_ARGUMENT,
                    "A legacy wallet-secret key has ambiguous SS58 ownership"
                )
            }
        }

        return owned
    }

    private fun discoverOwnedV2SecretKeys(
        metaId: Long,
        knownChainAccountIdsHex: Set<String>,
        failureReason: FailureReason
    ): Set<String> {
        val activePrefix = "$metaId:"
        val quarantinePrefix = "$QUARANTINE_PREFIX$activePrefix"
        val candidates = try {
            encryptedPreferences.keysWithPrefixes(
                prefixes = setOf(activePrefix, quarantinePrefix),
                maxResultCount = MAX_V2_NAMESPACE_CANDIDATES,
                maxKeyBytes = MAX_V2_NAMESPACE_KEY_BYTES,
                maxTotalKeyBytes = MAX_V2_NAMESPACE_CANDIDATE_BYTES,
                failOnOversizedMatch = true
            )
        } catch (failure: Exception) {
            throw JournalException(
                reason = failureReason,
                message = "Unable to enumerate a bounded V2 wallet-secret namespace",
                cause = failure
            )
        }

        val discoveredAccountIds = linkedSetOf<String>()
        val ownedKeys = linkedSetOf<String>()
        candidates.forEach { candidate ->
            val accountIdHex = v2ChainAccountId(candidate, metaId) ?: return@forEach
            discoveredAccountIds += accountIdHex
            ownedKeys += candidate
        }

        if (
            (knownChainAccountIdsHex + discoveredAccountIds).size >
            MAX_CHAIN_ACCOUNT_IDS
        ) {
            fail(
                failureReason,
                "A wallet owns too many current or orphaned V2 chain-secret namespaces"
            )
        }
        if (ownedKeys.size > MAX_OWNED_V2_SECRET_KEYS) {
            fail(failureReason, "A wallet owns too many V2 chain-secret keys")
        }

        return ownedKeys
    }

    private fun requireValidDeletionSecretKeys(
        actual: Set<String>,
        deterministic: Set<String>,
        metaId: Long,
        chainAccountIdsHex: Set<String>,
        substrateAccountIdHex: String?,
        failureReason: FailureReason
    ) {
        if (!actual.containsAll(deterministic)) {
            fail(
                failureReason,
                "A wallet deletion omits a deterministic wallet-secret key"
            )
        }

        val legacyKeys = linkedSetOf<String>()
        val tonConnectKeys = linkedSetOf<String>()
        val v2ChainAccountIds = linkedSetOf<String>()
        (actual - deterministic).forEach { key ->
            val v2ChainAccountId = v2ChainAccountId(key, metaId)
            if (v2ChainAccountId != null) {
                v2ChainAccountIds += v2ChainAccountId
            } else if (isTonConnectDeletionKey(key, metaId)) {
                tonConnectKeys += key
            } else {
                legacyKeys += key
            }
        }
        if ((chainAccountIdsHex + v2ChainAccountIds).size > MAX_CHAIN_ACCOUNT_IDS) {
            fail(failureReason, "A wallet deletion contains too many V2 namespaces")
        }
        if (legacyKeys.size > MAX_OWNED_LEGACY_SECRET_KEYS) {
            fail(failureReason, "A wallet deletion contains too many legacy secret keys")
        }
        validateTonConnectDeletionKeys(
            keys = tonConnectKeys,
            metaId = metaId,
            failureReason = failureReason
        )
        if (
            legacyKeys.any {
                substrateAccountIdHex == null ||
                    legacySecretOwnership(it, substrateAccountIdHex) !=
                    LegacySecretOwnership.OWNED
            }
        ) {
            fail(
                failureReason,
                "A wallet deletion contains a legacy key not owned by the wallet"
            )
        }
    }

    private fun validateTonConnectDeletionKeys(
        keys: Set<String>,
        metaId: Long,
        failureReason: FailureReason
    ) {
        if (
            keys.size > MAX_TON_CONNECT_SECRET_KEYS ||
            keys.any { !isTonConnectDeletionKey(it, metaId) }
        ) {
            fail(
                failureReason,
                "A wallet deletion contains invalid TON Connect secret keys"
            )
        }
    }

    private fun isTonConnectDeletionKey(
        key: String,
        metaId: Long
    ): Boolean {
        val activeKey = if (key.startsWith(QUARANTINE_PREFIX)) {
            key.removePrefix(QUARANTINE_PREFIX)
        } else {
            key
        }
        return TonConnectStorageKeys.isScopedKeyForMeta(activeKey, metaId) ||
            TonConnectStorageKeys.isLegacyKey(activeKey)
    }

    private fun v2ChainAccountId(key: String, metaId: Long): String? {
        val activePrefix = "$metaId:"
        val activeKey = when {
            key.startsWith("$QUARANTINE_PREFIX$activePrefix") -> {
                key.removePrefix(QUARANTINE_PREFIX)
            }
            key.startsWith(activePrefix) -> key
            else -> return null
        }
        val chainSuffix = ":$ACCESS_SECRET_SUFFIX"
        if (!activeKey.endsWith(chainSuffix)) return null

        val accountIdHex = activeKey
            .removePrefix(activePrefix)
            .removeSuffix(chainSuffix)
        return accountIdHex.takeIf {
            it.length % 2 == 0 &&
                it.length / 2 in 20..64 &&
                LOWERCASE_HEX.matches(it)
        }
    }

    private fun legacySecretOwnership(
        key: String,
        substrateAccountIdHex: String
    ): LegacySecretOwnership {
        val prefix = LEGACY_SECRET_PREFIXES.firstOrNull(key::startsWith)
            ?: return LegacySecretOwnership.CLEARLY_INVALID
        val address = key.removePrefix(prefix)
        if (
            address.length !in MIN_SS58_ADDRESS_CHARS..MAX_SS58_ADDRESS_CHARS ||
            !BASE58_ADDRESS.matches(address)
        ) {
            return LegacySecretOwnership.CLEARLY_INVALID
        }

        val accountId = try {
            address.toAccountId()
        } catch (_: Exception) {
            return LegacySecretOwnership.AMBIGUOUS
        }
        if (accountId.size != SUBSTRATE_ACCOUNT_ID_BYTES) {
            return LegacySecretOwnership.AMBIGUOUS
        }

        return if (accountId.toCanonicalHex() == substrateAccountIdHex) {
            LegacySecretOwnership.OWNED
        } else {
            LegacySecretOwnership.UNRELATED
        }
    }

    private fun ByteArray.toCanonicalHex(): String {
        return joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(HEX_RADIX).padStart(2, '0')
        }
    }

    private enum class LegacySecretOwnership {
        OWNED,
        UNRELATED,
        CLEARLY_INVALID,
        AMBIGUOUS
    }

    private fun validateAfterImage(
        afterImage: PublicAfterImage,
        failureReason: FailureReason
    ) {
        validatePublicText(
            value = afterImage.name,
            fieldName = "wallet name",
            maxLength = MAX_NAME_CHARS,
            allowEmpty = false,
            failureReason = failureReason
        )
        afterImage.googleBackupAddress?.let {
            validatePublicText(
                value = it,
                fieldName = "Google backup address",
                maxLength = MAX_GOOGLE_BACKUP_ADDRESS_CHARS,
                allowEmpty = true,
                failureReason = failureReason
            )
        }
        if (afterImage.position < 0) {
            fail(failureReason, "A wallet public after-image has a negative position")
        }

        val substrateFieldsPresent = listOf(
            afterImage.substratePublicKeyHex,
            afterImage.substrateAccountIdHex,
            afterImage.substrateCryptoType
        ).count { it != null }
        if (substrateFieldsPresent !in setOf(0, 3)) {
            fail(
                failureReason,
                "Substrate public fields must be either all present or all absent"
            )
        }
        afterImage.substratePublicKeyHex?.let {
            validateCanonicalHex(
                value = it,
                fieldName = "Substrate public key",
                allowedByteRange = 32..65,
                failureReason = failureReason
            )
        }
        afterImage.substrateAccountIdHex?.let {
            validateCanonicalHex(
                value = it,
                fieldName = "Substrate account id",
                allowedByteRange = 32..32,
                failureReason = failureReason
            )
        }

        val ethereumFieldsPresent = listOf(
            afterImage.ethereumPublicKeyHex,
            afterImage.ethereumAddressHex
        ).count { it != null }
        if (ethereumFieldsPresent !in setOf(0, 2)) {
            fail(
                failureReason,
                "Ethereum public fields must be either both present or both absent"
            )
        }
        afterImage.ethereumPublicKeyHex?.let {
            validateCanonicalHex(
                value = it,
                fieldName = "Ethereum public key",
                allowedByteRange = 32..65,
                failureReason = failureReason
            )
        }
        afterImage.ethereumAddressHex?.let {
            validateCanonicalHex(
                value = it,
                fieldName = "Ethereum address",
                allowedByteRange = 20..20,
                failureReason = failureReason
            )
        }
        afterImage.tonPublicKeyHex?.let {
            validateCanonicalHex(
                value = it,
                fieldName = "TON public key",
                allowedByteRange = 32..32,
                failureReason = failureReason
            )
        }
    }

    private fun validateFinalSecretPlaintexts(
        journal: Journal,
        finalSecretPlaintexts: Map<String, String>,
        failureReason: FailureReason
    ) {
        requireExactSecretKeys(
            actual = finalSecretPlaintexts.keys,
            expected = journal.secretKeysToPut,
            failureReason = failureReason
        )
        finalSecretPlaintexts.values.forEach { plaintext ->
            if (plaintext.isEmpty()) {
                fail(failureReason, "A final wallet-secret plaintext cannot be empty")
            }
            if (plaintext.length > MAX_SECRET_PLAINTEXT_CHARS) {
                fail(failureReason, "A final wallet-secret plaintext is oversized")
            }
        }
    }

    private fun requireExactSecretKeys(
        actual: Set<String>,
        expected: Set<String>,
        failureReason: FailureReason
    ) {
        if (actual != expected) {
            fail(
                failureReason,
                "A wallet-secret mutation contains missing or unexpected secret keys"
            )
        }
    }

    private fun validateOperationId(
        operationId: String,
        failureReason: FailureReason
    ) {
        if (!CANONICAL_UUID.matches(operationId)) {
            fail(failureReason, "A wallet-secret mutation requires a canonical operation id")
        }
    }

    private fun validatePublicText(
        value: String,
        fieldName: String,
        maxLength: Int,
        allowEmpty: Boolean,
        failureReason: FailureReason
    ) {
        if ((!allowEmpty && value.isBlank()) || value.length > maxLength) {
            fail(failureReason, "The $fieldName has an invalid length")
        }
        if (!value.hasWellFormedUnicode() || value.any(Char::isISOControl)) {
            fail(failureReason, "The $fieldName contains unsafe characters")
        }
    }

    private fun validateCanonicalHex(
        value: String,
        fieldName: String,
        allowedByteRange: IntRange,
        failureReason: FailureReason
    ) {
        val byteLength = value.length / 2
        if (
            value.length % 2 != 0 ||
            byteLength !in allowedByteRange ||
            !LOWERCASE_HEX.matches(value)
        ) {
            fail(failureReason, "The $fieldName is not canonical hexadecimal")
        }
    }

    private fun encode(journal: Journal): String {
        val output = StringWriter()
        JsonWriter(output).use { writer ->
            writer.isLenient = false
            writer.isHtmlSafe = true
            writer.serializeNulls = true
            writer.beginObject()
            writer.name(FIELD_VERSION).value(journal.version.toLong())
            writer.name(FIELD_OPERATION_ID).value(journal.operationId)
            writer.name(FIELD_OPERATION).value(journal.operation.name)
            writer.name(FIELD_META_ID).value(journal.metaId)
            writer.name(FIELD_BEFORE_IMAGE)
            journal.beforeImage?.let { writer.writePublicImage(it) } ?: writer.nullValue()
            writer.name(FIELD_AFTER_IMAGE)
            journal.afterImage?.let { writer.writePublicImage(it) } ?: writer.nullValue()
            writer.name(FIELD_SELECTED_META_ID_AFTER_DELETE)
            journal.selectedMetaIdAfterDelete?.let(writer::value) ?: writer.nullValue()
            writer.name(FIELD_CHAIN_ACCOUNT_IDS_HEX)
            writer.beginArray()
            journal.chainAccountIdsHex.sorted().forEach(writer::value)
            writer.endArray()
            writer.name(FIELD_SECRET_KEYS_TO_PUT)
            writer.beginArray()
            journal.secretKeysToPut.sorted().forEach(writer::value)
            writer.endArray()
            writer.name(FIELD_SECRET_KEYS_TO_REMOVE)
            writer.beginArray()
            journal.secretKeysToRemove.sorted().forEach(writer::value)
            writer.endArray()
            writer.endObject()
        }

        return output.toString().also { encoded ->
            check(encoded.length <= MAX_JOURNAL_PLAINTEXT_CHARS) {
                "A validated wallet-secret mutation journal exceeded its encoded bound"
            }
        }
    }

    private fun JsonWriter.writePublicImage(afterImage: PublicAfterImage) {
        beginObject()
        name(FIELD_NAME).value(afterImage.name)
        name(FIELD_SUBSTRATE_PUBLIC_KEY).nullableValue(afterImage.substratePublicKeyHex)
        name(FIELD_SUBSTRATE_ACCOUNT_ID).nullableValue(afterImage.substrateAccountIdHex)
        name(FIELD_SUBSTRATE_CRYPTO_TYPE)
            .nullableValue(afterImage.substrateCryptoType?.name)
        name(FIELD_ETHEREUM_PUBLIC_KEY).nullableValue(afterImage.ethereumPublicKeyHex)
        name(FIELD_ETHEREUM_ADDRESS).nullableValue(afterImage.ethereumAddressHex)
        name(FIELD_TON_PUBLIC_KEY).nullableValue(afterImage.tonPublicKeyHex)
        name(FIELD_IS_SELECTED).value(afterImage.isSelected)
        name(FIELD_POSITION).value(afterImage.position.toLong())
        name(FIELD_IS_BACKED_UP).value(afterImage.isBackedUp)
        name(FIELD_GOOGLE_BACKUP_ADDRESS).nullableValue(afterImage.googleBackupAddress)
        name(FIELD_INITIALIZED).value(afterImage.initialized)
        endObject()
    }

    private fun JsonWriter.nullableValue(value: String?) {
        if (value == null) {
            nullValue()
        } else {
            value(value)
        }
    }

    private fun decode(encoded: String): Journal {
        try {
            JsonReader(StringReader(encoded)).use { reader ->
                reader.isLenient = false
                reader.requireToken(JsonToken.BEGIN_OBJECT, "journal object")
                reader.beginObject()

                val seenFields = mutableSetOf<String>()
                var version: Int? = null
                var operationId: String? = null
                var operation: Operation? = null
                var metaId: Long? = null
                var beforeImage: PublicAfterImage? = null
                var afterImage: PublicAfterImage? = null
                var selectedMetaIdAfterDelete: Long? = null
                var chainAccountIdsHex: Set<String>? = null
                var secretKeysToPut: Set<String>? = null
                var secretKeysToRemove: Set<String>? = null

                while (reader.hasNext()) {
                    val field = reader.nextUniqueName(seenFields)
                    when (field) {
                        FIELD_VERSION -> version = reader.nextCanonicalInt(field)
                        FIELD_OPERATION_ID -> operationId = reader.nextRequiredString(field)
                        FIELD_OPERATION -> {
                            val encodedOperation = reader.nextRequiredString(field)
                            operation = Operation.entries.singleOrNull {
                                it.name == encodedOperation
                            } ?: malformed("The journal contains an unknown operation")
                        }
                        FIELD_META_ID -> metaId = reader.nextCanonicalLong(field)
                        FIELD_BEFORE_IMAGE -> {
                            beforeImage = if (reader.peek() == JsonToken.NULL) {
                                reader.nextNull()
                                null
                            } else {
                                reader.readPublicImage()
                            }
                        }
                        FIELD_AFTER_IMAGE -> {
                            afterImage = if (reader.peek() == JsonToken.NULL) {
                                reader.nextNull()
                                null
                            } else {
                                reader.readPublicImage()
                            }
                        }
                        FIELD_SELECTED_META_ID_AFTER_DELETE -> {
                            selectedMetaIdAfterDelete = reader.nextNullableCanonicalLong(field)
                        }
                        FIELD_CHAIN_ACCOUNT_IDS_HEX -> {
                            chainAccountIdsHex = reader.readUniqueStrings(
                                field = field,
                                maxItems = MAX_CHAIN_ACCOUNT_IDS
                            )
                        }
                        FIELD_SECRET_KEYS_TO_PUT -> {
                            secretKeysToPut = reader.readUniqueStrings(
                                field = field,
                                maxItems = MAX_SECRET_KEYS_TO_PUT
                            )
                        }
                        FIELD_SECRET_KEYS_TO_REMOVE -> {
                            secretKeysToRemove = reader.readUniqueStrings(
                                field = field,
                                maxItems = MAX_SECRET_KEYS_TO_REMOVE
                            )
                        }
                        else -> malformed("The journal contains an unknown field")
                    }
                }
                reader.endObject()
                reader.requireToken(JsonToken.END_DOCUMENT, "end of journal")
                requireExactFields(seenFields, JOURNAL_FIELDS, "journal")

                return Journal(
                    version = requireNotNull(version),
                    operationId = requireNotNull(operationId),
                    operation = requireNotNull(operation),
                    metaId = requireNotNull(metaId),
                    beforeImage = beforeImage,
                    afterImage = afterImage,
                    selectedMetaIdAfterDelete = selectedMetaIdAfterDelete,
                    chainAccountIdsHex = requireNotNull(chainAccountIdsHex),
                    secretKeysToPut = requireNotNull(secretKeysToPut),
                    secretKeysToRemove = requireNotNull(secretKeysToRemove)
                )
            }
        } catch (failure: JournalException) {
            throw failure
        } catch (failure: Exception) {
            throw JournalException(
                reason = FailureReason.MALFORMED_STORED_JOURNAL,
                message = "The wallet-secret mutation journal is malformed",
                cause = failure
            )
        }
    }

    private fun JsonReader.readPublicImage(): PublicAfterImage {
        requireToken(JsonToken.BEGIN_OBJECT, "public image object")
        beginObject()

        val seenFields = mutableSetOf<String>()
        var name: String? = null
        var substratePublicKeyHex: String? = null
        var substrateAccountIdHex: String? = null
        var substrateCryptoType: SubstrateCryptoType? = null
        var ethereumPublicKeyHex: String? = null
        var ethereumAddressHex: String? = null
        var tonPublicKeyHex: String? = null
        var isSelected: Boolean? = null
        var position: Int? = null
        var isBackedUp: Boolean? = null
        var googleBackupAddress: String? = null
        var initialized: Boolean? = null

        while (hasNext()) {
            val field = nextUniqueName(seenFields)
            when (field) {
                FIELD_NAME -> name = nextRequiredString(field)
                FIELD_SUBSTRATE_PUBLIC_KEY -> {
                    substratePublicKeyHex = nextNullableString(field)
                }
                FIELD_SUBSTRATE_ACCOUNT_ID -> {
                    substrateAccountIdHex = nextNullableString(field)
                }
                FIELD_SUBSTRATE_CRYPTO_TYPE -> {
                    val encodedCryptoType = nextNullableString(field)
                    substrateCryptoType = encodedCryptoType?.let { encoded ->
                        SubstrateCryptoType.entries.singleOrNull {
                            it.name == encoded
                        } ?: malformed("The after-image has an unknown Substrate crypto type")
                    }
                }
                FIELD_ETHEREUM_PUBLIC_KEY -> {
                    ethereumPublicKeyHex = nextNullableString(field)
                }
                FIELD_ETHEREUM_ADDRESS -> {
                    ethereumAddressHex = nextNullableString(field)
                }
                FIELD_TON_PUBLIC_KEY -> tonPublicKeyHex = nextNullableString(field)
                FIELD_IS_SELECTED -> isSelected = nextRequiredBoolean(field)
                FIELD_POSITION -> position = nextCanonicalInt(field)
                FIELD_IS_BACKED_UP -> isBackedUp = nextRequiredBoolean(field)
                FIELD_GOOGLE_BACKUP_ADDRESS -> {
                    googleBackupAddress = nextNullableString(field)
                }
                FIELD_INITIALIZED -> initialized = nextRequiredBoolean(field)
                else -> malformed("The public after-image contains an unknown field")
            }
        }
        endObject()
        requireExactFields(seenFields, AFTER_IMAGE_FIELDS, "public after-image")

        return PublicAfterImage(
            name = requireNotNull(name),
            substratePublicKeyHex = substratePublicKeyHex,
            substrateAccountIdHex = substrateAccountIdHex,
            substrateCryptoType = substrateCryptoType,
            ethereumPublicKeyHex = ethereumPublicKeyHex,
            ethereumAddressHex = ethereumAddressHex,
            tonPublicKeyHex = tonPublicKeyHex,
            isSelected = requireNotNull(isSelected),
            position = requireNotNull(position),
            isBackedUp = requireNotNull(isBackedUp),
            googleBackupAddress = googleBackupAddress,
            initialized = requireNotNull(initialized)
        )
    }

    private fun JsonReader.readUniqueStrings(
        field: String,
        maxItems: Int
    ): Set<String> {
        requireToken(JsonToken.BEGIN_ARRAY, "$field array")
        beginArray()
        val result = linkedSetOf<String>()
        while (hasNext()) {
            if (result.size >= maxItems) {
                malformed("The journal contains too many $field entries")
            }
            val value = nextRequiredString(field)
            if (!result.add(value)) {
                malformed("The journal contains a duplicate $field entry")
            }
        }
        endArray()
        return result
    }

    private fun JsonReader.nextUniqueName(seenFields: MutableSet<String>): String {
        requireToken(JsonToken.NAME, "field name")
        val field = nextName()
        if (!seenFields.add(field)) {
            malformed("The journal contains a duplicate field")
        }
        return field
    }

    private fun JsonReader.nextRequiredString(field: String): String {
        requireToken(JsonToken.STRING, field)
        return nextString()
    }

    private fun JsonReader.nextNullableString(field: String): String? {
        return when (peek()) {
            JsonToken.NULL -> {
                nextNull()
                null
            }
            JsonToken.STRING -> nextString()
            else -> malformed("The $field field has an invalid JSON type")
        }
    }

    private fun JsonReader.nextRequiredBoolean(field: String): Boolean {
        requireToken(JsonToken.BOOLEAN, field)
        return nextBoolean()
    }

    private fun JsonReader.nextCanonicalInt(field: String): Int {
        val value = nextCanonicalLong(field)
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) {
            malformed("The $field field is outside the supported integer range")
        }
        return value.toInt()
    }

    private fun JsonReader.nextNullableCanonicalLong(field: String): Long? {
        return if (peek() == JsonToken.NULL) {
            nextNull()
            null
        } else {
            nextCanonicalLong(field)
        }
    }

    private fun JsonReader.nextCanonicalLong(field: String): Long {
        requireToken(JsonToken.NUMBER, field)
        val encodedNumber = nextString()
        if (!CANONICAL_INTEGER.matches(encodedNumber)) {
            malformed("The $field field is not a canonical integer")
        }
        return encodedNumber.toLongOrNull()
            ?: malformed("The $field field is outside the supported integer range")
    }

    private fun JsonReader.requireToken(expected: JsonToken, description: String) {
        if (peek() != expected) {
            malformed("Expected $description in the wallet-secret mutation journal")
        }
    }

    private fun requireExactFields(
        actual: Set<String>,
        expected: Set<String>,
        objectName: String
    ) {
        if (actual != expected) {
            malformed("The $objectName is missing required fields")
        }
    }

    private inline fun <T> durabilityBoundary(
        message: String,
        operation: () -> T
    ): T {
        return try {
            operation()
        } catch (failure: WalletSecretConcurrentMutationException) {
            throw JournalException(
                reason = FailureReason.CONFLICT,
                message = "Wallet-secret state changed during durable finalization",
                cause = failure
            )
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw JournalException(
                reason = FailureReason.AMBIGUOUS_DURABILITY,
                message = message,
                cause = failure
            )
        }
    }

    private fun malformed(message: String): Nothing {
        fail(FailureReason.MALFORMED_STORED_JOURNAL, message)
    }

    private fun fail(reason: FailureReason, message: String): Nothing {
        throw JournalException(reason, message)
    }

    private fun secretKey(metaId: Long, suffix: String): String {
        return "$metaId:$suffix"
    }

    private fun String.hasWellFormedUnicode(): Boolean {
        var index = 0
        while (index < length) {
            val current = this[index]
            when {
                current.isHighSurrogate() -> {
                    if (index + 1 >= length || !this[index + 1].isLowSurrogate()) {
                        return false
                    }
                    index += 2
                }
                current.isLowSurrogate() -> return false
                else -> index += 1
            }
        }
        return true
    }

    companion object {
        const val JOURNAL_KEY = "wallet_secret_mutation_journal_v1"
        const val CURRENT_VERSION = 1

        private const val SUBSTRATE_SECRET_SUFFIX = "SUBSTRATE_SECRETS"
        private const val ETHEREUM_SECRET_SUFFIX = "ETHEREUM_SECRETS"
        private const val TON_SECRET_SUFFIX = "TON_SECRETS"
        private const val ACCESS_SECRET_SUFFIX = "ACCESS_SECRETS"
        private const val QUARANTINE_PREFIX = "wallet_secret_quarantine:"
        private const val LEGACY_V1_META_PREFIX = "legacy_v1_meta_"
        private const val LEGACY_V04_META_PREFIX = "legacy_v04_meta_"
        private const val LEGACY_V1_PUBLIC_PREFIX = "legacy_v1_public_"
        private const val LEGACY_V1_ACTIVE_PREFIX = "security_source_"
        private const val LEGACY_V04_PRIVATE_PREFIX = "private_"
        private const val LEGACY_V04_SEED_PREFIX = "seed_"
        private const val LEGACY_V04_ENTROPY_PREFIX = "entropy_"
        private const val LEGACY_V04_DERIVATION_PREFIX = "derivation_"

        private const val MAX_JOURNAL_PLAINTEXT_CHARS = 262_144
        private const val MAX_SECRET_PLAINTEXT_CHARS = 1_048_576
        private const val MAX_CHAIN_ACCOUNT_IDS = 512
        private const val MAX_SECRET_KEYS_TO_PUT = 3
        private const val MAX_LEGACY_SECRET_CANDIDATES = 4_096
        private const val MAX_LEGACY_SECRET_KEY_BYTES = 256
        private const val MAX_LEGACY_SECRET_CANDIDATE_BYTES = 524_288
        private const val MAX_OWNED_LEGACY_SECRET_KEYS = 128
        private const val MAX_V2_NAMESPACE_CANDIDATES = 4_096
        private const val MAX_V2_NAMESPACE_KEY_BYTES = 1_024
        private const val MAX_V2_NAMESPACE_CANDIDATE_BYTES = 1_048_576
        private const val MAX_OWNED_V2_SECRET_KEYS = MAX_CHAIN_ACCOUNT_IDS * 2
        private const val MAX_TON_CONNECT_SECRET_KEYS =
            TonConnectStorageKeys.MAX_CONNECTIONS_PER_WALLET * 4
        private const val MAX_TON_CONNECT_SCOPED_NAMESPACE_KEYS =
            TonConnectStorageKeys.MAX_CONNECTIONS_PER_WALLET * 2
        private const val MAX_TON_CONNECT_SCOPED_KEY_BYTES = 256
        private const val MAX_TON_CONNECT_SCOPED_NAMESPACE_BYTES =
            MAX_TON_CONNECT_SCOPED_NAMESPACE_KEYS *
                MAX_TON_CONNECT_SCOPED_KEY_BYTES
        private const val ROOT_SECRET_KEYS = 4
        private const val ACTIVE_QUARANTINE_AND_IDENTITY_VARIANTS = 3
        private const val LEGACY_QUARANTINE_KEYS = 2
        private const val MAX_SECRET_KEYS_TO_REMOVE =
            (ROOT_SECRET_KEYS + MAX_CHAIN_ACCOUNT_IDS) *
                ACTIVE_QUARANTINE_AND_IDENTITY_VARIANTS +
                LEGACY_QUARANTINE_KEYS +
                MAX_OWNED_LEGACY_SECRET_KEYS +
                MAX_TON_CONNECT_SECRET_KEYS
        private const val MAX_NAME_CHARS = 256
        private const val MAX_GOOGLE_BACKUP_ADDRESS_CHARS = 2_048
        private const val MIN_SS58_ADDRESS_CHARS = 47
        private const val MAX_SS58_ADDRESS_CHARS = 64
        private const val SUBSTRATE_ACCOUNT_ID_BYTES = 32
        private const val HEX_RADIX = 16

        private const val FIELD_VERSION = "version"
        private const val FIELD_OPERATION_ID = "operationId"
        private const val FIELD_OPERATION = "operation"
        private const val FIELD_META_ID = "metaId"
        private const val FIELD_BEFORE_IMAGE = "beforeImage"
        private const val FIELD_AFTER_IMAGE = "afterImage"
        private const val FIELD_SELECTED_META_ID_AFTER_DELETE = "selectedMetaIdAfterDelete"
        private const val FIELD_CHAIN_ACCOUNT_IDS_HEX = "chainAccountIdsHex"
        private const val FIELD_SECRET_KEYS_TO_PUT = "secretKeysToPut"
        private const val FIELD_SECRET_KEYS_TO_REMOVE = "secretKeysToRemove"

        private const val FIELD_NAME = "name"
        private const val FIELD_SUBSTRATE_PUBLIC_KEY = "substratePublicKeyHex"
        private const val FIELD_SUBSTRATE_ACCOUNT_ID = "substrateAccountIdHex"
        private const val FIELD_SUBSTRATE_CRYPTO_TYPE = "substrateCryptoType"
        private const val FIELD_ETHEREUM_PUBLIC_KEY = "ethereumPublicKeyHex"
        private const val FIELD_ETHEREUM_ADDRESS = "ethereumAddressHex"
        private const val FIELD_TON_PUBLIC_KEY = "tonPublicKeyHex"
        private const val FIELD_IS_SELECTED = "isSelected"
        private const val FIELD_POSITION = "position"
        private const val FIELD_IS_BACKED_UP = "isBackedUp"
        private const val FIELD_GOOGLE_BACKUP_ADDRESS = "googleBackupAddress"
        private const val FIELD_INITIALIZED = "initialized"

        private val JOURNAL_FIELDS = setOf(
            FIELD_VERSION,
            FIELD_OPERATION_ID,
            FIELD_OPERATION,
            FIELD_META_ID,
            FIELD_BEFORE_IMAGE,
            FIELD_AFTER_IMAGE,
            FIELD_SELECTED_META_ID_AFTER_DELETE,
            FIELD_CHAIN_ACCOUNT_IDS_HEX,
            FIELD_SECRET_KEYS_TO_PUT,
            FIELD_SECRET_KEYS_TO_REMOVE
        )
        private val AFTER_IMAGE_FIELDS = setOf(
            FIELD_NAME,
            FIELD_SUBSTRATE_PUBLIC_KEY,
            FIELD_SUBSTRATE_ACCOUNT_ID,
            FIELD_SUBSTRATE_CRYPTO_TYPE,
            FIELD_ETHEREUM_PUBLIC_KEY,
            FIELD_ETHEREUM_ADDRESS,
            FIELD_TON_PUBLIC_KEY,
            FIELD_IS_SELECTED,
            FIELD_POSITION,
            FIELD_IS_BACKED_UP,
            FIELD_GOOGLE_BACKUP_ADDRESS,
            FIELD_INITIALIZED
        )

        private val CANONICAL_UUID = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        )
        private val CANONICAL_INTEGER = Regex("^-?(0|[1-9][0-9]*)$")
        private val LOWERCASE_HEX = Regex("^[0-9a-f]+$")
        private val BASE58_ADDRESS = Regex("^[1-9A-HJ-NP-Za-km-z]+$")
        private val LEGACY_ACTIVE_SECRET_PREFIXES = setOf(
            LEGACY_V1_ACTIVE_PREFIX,
            LEGACY_V04_PRIVATE_PREFIX,
            LEGACY_V04_SEED_PREFIX,
            LEGACY_V04_ENTROPY_PREFIX,
            LEGACY_V04_DERIVATION_PREFIX
        )
        private val LEGACY_SECRET_PREFIXES = buildSet {
            addAll(LEGACY_ACTIVE_SECRET_PREFIXES)
            LEGACY_ACTIVE_SECRET_PREFIXES.mapTo(this) { "$QUARANTINE_PREFIX$it" }
        }
    }
}
