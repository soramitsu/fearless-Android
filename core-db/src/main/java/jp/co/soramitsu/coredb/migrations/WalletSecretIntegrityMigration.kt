package jp.co.soramitsu.coredb.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidation
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidation
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.data.storage.encrypt.quarantineEncryptedStringSnapshotDurably
import jp.co.soramitsu.common.data.storage.encrypt.replaceEncryptedStringsForStatesDurably
import jp.co.soramitsu.common.data.storage.encrypt.requireSnapshotMovesReady
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.isValidEthereumCompressedPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.extensions.toHexString

/**
 * Adjacent integrity pass for databases that already committed the historical
 * TON migration.
 *
 * Room wraps this migration in a SQL transaction. Preference operations are
 * independently synchronous and durable, so every operation is idempotent:
 * after a process death or SQL rollback, an exact quarantine is accepted as
 * recovery evidence and a completed replacement validates on the next retry.
 */
internal class WalletSecretIntegrityMigration(
    private val encryptedPreferences: EncryptedPreferences,
    private val chainAccountSecretValidation: ChainAccountSecretValidation =
        ChainAccountSecretValidator,
    private val walletRootSecretValidation: WalletRootSecretValidation =
        WalletRootSecretValidator,
    private val rowLimits: WalletMigrationRowLimits =
        WalletMigrationRowLimits.PRODUCTION
) : Migration(76, 77) {

    override fun migrate(db: SupportSQLiteDatabase) {
        encryptedPreferences.requireDurableStorageHealthy()
        WalletSecretIntegrityQueryPreflight.requireReadable(
            database = db,
            rowLimits = rowLimits
        )
        WalletSecretMigrationPreflight.requireSafeToMutate(
            database = db,
            encryptedPreferences = encryptedPreferences,
            includeTonPublicKey = true,
            rowLimits = rowLimits
        )
        val mutationJournalStore =
            WalletSecretMutationJournalStore(encryptedPreferences)
        val mutationJournal = mutationJournalStore.load()
        val stagedMutationSecretKeys = mutationJournal?.let {
            it.secretKeysToPut + it.secretKeysToRemove
        }.orEmpty()

        val walletIdentities = mutableListOf<WalletPublicIdentity>()
        forEachBoundedWalletPublicIdentity(
            database = db,
            includeTonPublicKey = true,
            maximumRows = rowLimits.maxWalletRows
        ) { identity ->
            walletIdentities += identity
        }
        val orphanRecoveryPlan = WalletOrphanSecretInventory(
            encryptedPreferences = encryptedPreferences,
            rowLimits = rowLimits
        ).prepare(
            database = db,
            excludedActiveKeys = stagedMutationSecretKeys,
            // The TON journal codec lives above core-db. While a valid journal
            // exists, defer every scoped TON key until startup reconciles that
            // journal and runs the current-schema inventory.
            includeTonConnectScopedKeys =
            !mutationJournalStore.hasTonConnectMutationJournal()
        )

        // Preference storage is outside Room's SQL transaction. Prepare every
        // wallet, chain account and orphan first so a deterministic conflict
        // in a late row cannot occur after an earlier wallet was mutated.
        listOf(false, true).forEach { executeActions ->
            walletIdentities.forEach { identity ->
                finishLegacyMigration(
                    identity,
                    stagedMutationSecretKeys,
                    executeActions
                )
                validateSeparatedSecrets(
                    identity,
                    stagedMutationSecretKeys,
                    executeActions
                )
            }
            forEachChainAccountIdentity(db) { identity ->
                validateChainAccountSecret(
                    identity = identity,
                    stagedMutationSecretKeys = stagedMutationSecretKeys,
                    executeActions = executeActions
                )
            }
            if (executeActions) {
                orphanRecoveryPlan.commit()
            } else {
                orphanRecoveryPlan.requireReady()
            }
        }
    }

    private fun finishLegacyMigration(
        identity: WalletPublicIdentity,
        stagedMutationSecretKeys: Set<String>,
        executeActions: Boolean
    ) {
        val activeKey = legacySecretKey(identity.metaId)
        // A valid mutation journal owns these exact active keys until startup
        // reconciliation publishes or rejects its public after-image. Treating
        // a staged ADD_EVM/CREATE secret as an orphan here would leave a false
        // recovery marker after the coordinator successfully commits it.
        if (activeKey in stagedMutationSecretKeys) return
        if (hasPublicIdentityRecovery(identity.metaId, activeKey)) return
        if (!encryptedPreferences.hasKey(activeKey)) {
            // A previous attempt may already have moved the exact ciphertext.
            // Its quarantine key is durable recovery evidence and must not be
            // decoded, overwritten, or treated as a reason to block SQL retry.
            if (encryptedPreferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))) {
                return
            }
            return
        }
        if (
            !validatePublicIdentityOrMarkRecovery(
                identity = identity,
                activeKey = activeKey,
                executeActions = executeActions,
                validate = {
                    validateSubstratePublicIdentity(identity)
                    validateEthereumPublicIdentityIfPresent(identity)
                }
            )
        ) {
            return
        }

        // Preference access is outside the payload-validation catch. Any
        // generic read/provider failure is transient or global and must roll
        // Room back without moving this ciphertext.
        val snapshot = checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(activeKey)
        ) {
            "A legacy wallet secret disappeared during validation"
        }
        val encoded = snapshot.plaintext

        val action = try {
            val replacement =
                WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                    encoded = encoded,
                    identity = identity,
                    walletRootSecretValidation = walletRootSecretValidation
                )
            val replacements = buildMap {
                put(substrateSecretKey(identity.metaId), replacement.substratePlaintext)
                replacement.ethereumPlaintext?.let {
                    put(ethereumSecretKey(identity.metaId), it)
                }
            }
            prepareLegacyReplacement(
                activeKey = activeKey,
                activeSnapshot = snapshot,
                replacements = replacements
            )
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: WalletPublicIdentityIntegrityException) {
            SecretAction.MarkPublicIdentityRecovery(
                metaId = identity.metaId,
                activeKey = activeKey
            )
        } catch (failure: WalletSecretIntegrityCorruptionException) {
            SecretAction.Quarantine(
                activeKey = activeKey,
                expectedSnapshot = snapshot
            )
        }

        // Never place durable storage operations in the payload-validation
        // catch above: a commit/readback failure is global or transient, not
        // evidence that this wallet ciphertext is corrupt.
        execute(action, executeActions)
    }

    private fun prepareLegacyReplacement(
        activeKey: String,
        activeSnapshot: EncryptedPreferenceSnapshot,
        replacements: Map<String, String>
    ): SecretAction.Replace {
        val expectedStates =
            linkedMapOf<String, EncryptedPreferenceSnapshot?>(
                activeKey to activeSnapshot
            )
        val absentTargetValues = linkedMapOf<String, String>()
        replacements.forEach { (targetKey, replacementValue) ->
            val targetSnapshot = if (
                encryptedPreferences.hasKey(targetKey)
            ) {
                checkNotNull(
                    encryptedPreferences.getDecryptedStringSnapshot(targetKey)
                ) {
                    "A wallet-secret integrity target disappeared during preparation"
                }
            } else {
                null
            }
            if (
                targetSnapshot != null &&
                targetSnapshot.plaintext != replacementValue
            ) {
                throw WalletSecretConcurrentMutationException(
                    "A wallet-secret integrity target contains conflicting material"
                )
            }
            expectedStates[targetKey] = targetSnapshot
            if (targetSnapshot == null) {
                absentTargetValues[targetKey] = replacementValue
            }
        }
        return SecretAction.Replace(
            expectedStates = expectedStates,
            valuesToPut = absentTargetValues,
            keysToRemove = setOf(activeKey)
        )
    }

    private fun validateSeparatedSecrets(
        identity: WalletPublicIdentity,
        stagedMutationSecretKeys: Set<String>,
        executeActions: Boolean
    ) {
        val substrateActiveKey = substrateSecretKey(identity.metaId)
        if (
            substrateActiveKey !in stagedMutationSecretKeys &&
            encryptedPreferences.hasKey(substrateActiveKey) &&
            !hasPublicIdentityRecovery(
                metaId = identity.metaId,
                activeKey = substrateActiveKey
            ) &&
            validatePublicIdentityOrMarkRecovery(
                identity = identity,
                activeKey = substrateActiveKey,
                executeActions = executeActions,
                validate = { validateSubstratePublicIdentity(identity) }
            )
        ) {
            validateActiveSecret(
                metaId = identity.metaId,
                activeKey = substrateActiveKey,
                isLocalCorruption = {
                    it is WalletRootSecretCorruptionException
                },
                executeActions = executeActions
            ) {
                walletRootSecretValidation.validateSubstrateAndSanitize(
                    encoded = it,
                    expectedPublicKey = identity.substratePublicKey
                        ?: throw WalletPublicIdentityIntegrityException(
                            "An active Substrate secret has no durable public key"
                        ),
                    expectedCryptoType = identity.substrateCryptoType
                        ?: throw WalletPublicIdentityIntegrityException(
                            "An active Substrate secret has no durable crypto type"
                        ),
                    expectedAccountId = identity.substrateAccountId
                        ?: throw WalletPublicIdentityIntegrityException(
                            "An active Substrate secret has no durable account id"
                        )
                )
            }
        }

        val ethereumActiveKey = ethereumSecretKey(identity.metaId)
        if (
            ethereumActiveKey !in stagedMutationSecretKeys &&
            encryptedPreferences.hasKey(ethereumActiveKey) &&
            !hasPublicIdentityRecovery(
                metaId = identity.metaId,
                activeKey = ethereumActiveKey
            ) &&
            validatePublicIdentityOrMarkRecovery(
                identity = identity,
                activeKey = ethereumActiveKey,
                executeActions = executeActions,
                validate = { validateEthereumPublicIdentity(identity) }
            )
        ) {
            validateActiveSecret(
                metaId = identity.metaId,
                activeKey = ethereumActiveKey,
                isLocalCorruption = {
                    it is WalletRootSecretCorruptionException
                },
                executeActions = executeActions
            ) {
                walletRootSecretValidation.validateEthereumAndSanitize(
                    encoded = it,
                    expectedPublicKey = identity.ethereumPublicKey
                        ?: throw WalletPublicIdentityIntegrityException(
                            "An active Ethereum secret has no durable public key"
                        ),
                    expectedAddress = identity.ethereumAddress
                        ?: throw WalletPublicIdentityIntegrityException(
                            "An active Ethereum secret has no durable address"
                        )
                )
            }
        }
        val tonActiveKey = tonSecretKey(identity.metaId)
        if (
            tonActiveKey !in stagedMutationSecretKeys &&
            encryptedPreferences.hasKey(tonActiveKey) &&
            !hasPublicIdentityRecovery(
                metaId = identity.metaId,
                activeKey = tonActiveKey
            ) &&
            validatePublicIdentityOrMarkRecovery(
                identity = identity,
                activeKey = tonActiveKey,
                executeActions = executeActions,
                validate = { validateTonPublicIdentity(identity) }
            )
        ) {
            validateActiveSecret(
                metaId = identity.metaId,
                activeKey = tonActiveKey,
                isLocalCorruption = {
                    it is WalletRootSecretCorruptionException
                },
                executeActions = executeActions
            ) {
                walletRootSecretValidation.validateTonAndSanitize(
                    encoded = it,
                    expectedPublicKey = identity.tonPublicKey
                        ?: throw WalletPublicIdentityIntegrityException(
                            "An active TON secret has no durable public key"
                        )
                )
            }
        }
    }

    private fun validateActiveSecret(
        metaId: Long,
        activeKey: String,
        isLocalCorruption: (Exception) -> Boolean,
        executeActions: Boolean,
        validateAndSanitize: (String) -> String
    ) {
        if (hasPublicIdentityRecovery(metaId, activeKey)) return
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        if (!encryptedPreferences.hasKey(activeKey)) {
            // Exact-ciphertext quarantine is the stable recovery state. Its
            // presence deliberately permits a retry to commit version 77.
            if (encryptedPreferences.hasKey(quarantineKey)) {
                return
            }
            return
        }

        // Keep storage/provider reads outside the validation catch so a
        // transient exception cannot be converted into a quarantine action.
        val snapshot = checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(activeKey)
        ) {
            "A wallet secret disappeared during validation"
        }
        val encoded = snapshot.plaintext
        val action = try {
            val sanitized = validateAndSanitize(encoded)
            SecretAction.Replace(
                expectedStates = mapOf(activeKey to snapshot),
                valuesToPut = if (sanitized == encoded) {
                    emptyMap()
                } else {
                    mapOf(activeKey to sanitized)
                },
                keysToRemove = emptySet()
            )
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: WalletPublicIdentityIntegrityException) {
            SecretAction.MarkPublicIdentityRecovery(
                metaId = metaId,
                activeKey = activeKey
            )
        } catch (failure: Exception) {
            if (isLocalCorruption(failure)) {
                SecretAction.Quarantine(
                    activeKey = activeKey,
                    expectedSnapshot = snapshot
                )
            } else {
                throw failure
            }
        }

        execute(action, executeActions)
    }

    private fun validateChainAccountSecret(
        identity: ChainAccountPublicIdentity,
        stagedMutationSecretKeys: Set<String>,
        executeActions: Boolean
    ) {
        val activeKey = chainAccountSecretKey(identity)
        if (activeKey in stagedMutationSecretKeys) return
        if (!encryptedPreferences.hasKey(activeKey)) {
            // A previous attempt may already have durably moved this exact
            // ciphertext before Room rolled its SQL transaction back.
            if (encryptedPreferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))) {
                return
            }
            return
        }
        if (hasPublicIdentityRecovery(identity.metaId, activeKey)) return
        val cryptoType = identity.cryptoTypeText?.let {
            try {
                CryptoType.valueOf(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        if (cryptoType == null) {
            execute(
                SecretAction.MarkPublicIdentityRecovery(
                    metaId = identity.metaId,
                    activeKey = activeKey
                ),
                executeActions
            )
            return
        }
        val addressKind = identity.addressKind()
        if (addressKind == null) {
            execute(
                SecretAction.MarkPublicIdentityRecovery(
                    metaId = identity.metaId,
                    activeKey = activeKey
                ),
                executeActions
            )
            return
        }
        val expectedAccountIdBytes = when (addressKind) {
            ChainAccountAddressKind.SUBSTRATE ->
                SUBSTRATE_ACCOUNT_ID_BYTES

            ChainAccountAddressKind.ETHEREUM ->
                ETHEREUM_ADDRESS_BYTES
        }
        if (
            identity.accountId.size != expectedAccountIdBytes ||
            (
                addressKind == ChainAccountAddressKind.ETHEREUM &&
                    cryptoType != CryptoType.ECDSA
                )
        ) {
            execute(
                SecretAction.MarkPublicIdentityRecovery(
                    metaId = identity.metaId,
                    activeKey = activeKey
                ),
                executeActions
            )
            return
        }
        if (identity.publicKey.size != cryptoType.publicKeyBytes()) {
            execute(
                SecretAction.MarkPublicIdentityRecovery(
                    metaId = identity.metaId,
                    activeKey = activeKey
                ),
                executeActions
            )
            return
        }
        if (
            addressKind == ChainAccountAddressKind.ETHEREUM &&
            !identity.publicKey.isValidEthereumCompressedPublicKey()
        ) {
            execute(
                SecretAction.MarkPublicIdentityRecovery(
                    metaId = identity.metaId,
                    activeKey = activeKey
                ),
                executeActions
            )
            return
        }
        val durableAccountId = performPublicIdentityCryptography(
            operation = "chain-account public identity derivation"
        ) {
            when (addressKind) {
                ChainAccountAddressKind.SUBSTRATE ->
                    identity.publicKey.substrateAccountId()

                ChainAccountAddressKind.ETHEREUM ->
                    identity.publicKey.ethereumAddressFromPublicKey()
            }
        }
        if (!durableAccountId.contentEquals(identity.accountId)) {
            execute(
                SecretAction.MarkPublicIdentityRecovery(
                    metaId = identity.metaId,
                    activeKey = activeKey
                ),
                executeActions
            )
            return
        }
        validateActiveSecret(
            metaId = identity.metaId,
            activeKey = activeKey,
            isLocalCorruption = {
                it is ChainAccountSecretCorruptionException
            },
            executeActions = executeActions
        ) {
            chainAccountSecretValidation.validateAndSanitize(
                encoded = it,
                expectedAccountId = identity.accountId,
                expectedPublicKey = identity.publicKey,
                expectedCryptoType = cryptoType
            )
        }
    }

    private fun validatePublicIdentityOrMarkRecovery(
        identity: WalletPublicIdentity,
        activeKey: String,
        executeActions: Boolean,
        validate: () -> Unit
    ): Boolean {
        return try {
            validate()
            true
        } catch (_: WalletPublicIdentityIntegrityException) {
            execute(
                SecretAction.MarkPublicIdentityRecovery(
                    metaId = identity.metaId,
                    activeKey = activeKey
                ),
                executeActions
            )
            false
        }
    }

    private fun validateSubstratePublicIdentity(
        identity: WalletPublicIdentity
    ) {
        val publicKey = identity.substratePublicKey
            ?: throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has no durable public key"
            )
        val cryptoType = identity.substrateCryptoType
            ?: throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has no durable crypto type"
            )
        val accountId = identity.substrateAccountId
            ?: throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has no durable account id"
            )
        if (
            publicKey.size != cryptoType.publicKeyBytes() ||
            accountId.size != SUBSTRATE_ACCOUNT_ID_BYTES
        ) {
            throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has a malformed public identity"
            )
        }
        val durableAccountId = performPublicIdentityCryptography(
            operation = "Substrate public identity derivation"
        ) {
            publicKey.substrateAccountId()
        }
        if (!durableAccountId.contentEquals(accountId)) {
            throw WalletPublicIdentityIntegrityException(
                "A Substrate public key does not match its account id"
            )
        }
    }

    private fun validateEthereumPublicIdentityIfPresent(
        identity: WalletPublicIdentity
    ) {
        if (
            identity.ethereumPublicKey == null &&
            identity.ethereumAddress == null
        ) {
            return
        }
        validateEthereumPublicIdentity(identity)
    }

    private fun validateEthereumPublicIdentity(
        identity: WalletPublicIdentity
    ) {
        val publicKey = identity.ethereumPublicKey
            ?: throw WalletPublicIdentityIntegrityException(
                "An active Ethereum secret has no durable public key"
            )
        val address = identity.ethereumAddress
            ?: throw WalletPublicIdentityIntegrityException(
                "An active Ethereum secret has no durable address"
            )
        if (
            !publicKey.isValidEthereumCompressedPublicKey() ||
            address.size != ETHEREUM_ADDRESS_BYTES
        ) {
            throw WalletPublicIdentityIntegrityException(
                "An active Ethereum secret has a malformed public identity"
            )
        }
        val durableAddress = performPublicIdentityCryptography(
            operation = "Ethereum public identity derivation"
        ) {
            publicKey.ethereumAddressFromPublicKey()
        }
        if (!durableAddress.contentEquals(address)) {
            throw WalletPublicIdentityIntegrityException(
                "An Ethereum public key does not match its address"
            )
        }
    }

    private fun validateTonPublicIdentity(identity: WalletPublicIdentity) {
        if (identity.tonPublicKey?.size != TON_PUBLIC_KEY_BYTES) {
            throw WalletPublicIdentityIntegrityException(
                "An active TON secret has a malformed public identity"
            )
        }
    }

    private inline fun <T> performPublicIdentityCryptography(
        operation: String,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw WalletSecureStorageUnavailableException(
                "Wallet cryptography is unavailable during $operation",
                failure
            )
        }
    }

    private fun execute(
        action: SecretAction,
        executeActions: Boolean
    ) {
        when (action) {
            is SecretAction.Replace -> {
                if (executeActions) {
                    encryptedPreferences.replaceEncryptedStringsForStatesDurably(
                        expectedStates = action.expectedStates,
                        valuesToPut = action.valuesToPut,
                        keysToRemove = action.keysToRemove
                    )
                }
            }
            is SecretAction.Quarantine -> {
                val snapshotMoves = listOf(
                    EncryptedPreferenceSnapshotMove(
                        sourceKey = action.activeKey,
                        destinationKey =
                            WalletSecretQuarantine.keyFor(action.activeKey),
                        expectedSnapshot = action.expectedSnapshot
                    )
                )
                if (executeActions) {
                    encryptedPreferences.quarantineEncryptedStringSnapshotDurably(
                        sourceKey = action.activeKey,
                        quarantineKey =
                            WalletSecretQuarantine.keyFor(action.activeKey),
                        expectedSnapshot = action.expectedSnapshot
                    )
                } else {
                    encryptedPreferences.requireSnapshotMovesReady(
                        snapshotMoves
                    )
                }
            }
            is SecretAction.MarkPublicIdentityRecovery -> {
                if (
                    !hasPublicIdentityRecovery(
                        metaId = action.metaId,
                        activeKey = action.activeKey
                    )
                ) {
                    val markerKey = WalletPublicIdentityRecovery.keyFor(
                        metaId = action.metaId,
                        activeSecretKey = action.activeKey
                    )
                    val expectedMarkerState:
                        Map<String, EncryptedPreferenceSnapshot?> =
                        mapOf(markerKey to null)
                    if (executeActions) {
                        encryptedPreferences
                            .replaceEncryptedStringsForStatesDurably(
                                expectedStates = expectedMarkerState,
                                valuesToPut = mapOf(
                                    markerKey to
                                        WalletPublicIdentityRecovery.MARKER_VALUE
                                ),
                                keysToRemove = emptySet()
                            )
                    }
                }
            }
        }
    }

    private fun hasPublicIdentityRecovery(
        metaId: Long,
        activeKey: String
    ): Boolean {
        return validateExistingPublicIdentityRecovery(
            metaId = metaId,
            activeKey = activeKey
        )
    }

    private fun validateExistingPublicIdentityRecovery(
        metaId: Long,
        activeKey: String
    ): Boolean {
        return WalletSecretMigrationPreflight.hasValidPublicIdentityRecovery(
            encryptedPreferences = encryptedPreferences,
            metaId = metaId,
            activeSecretKey = activeKey
        )
    }

    private fun forEachChainAccountIdentity(
        database: SupportSQLiteDatabase,
        action: (ChainAccountPublicIdentity) -> Unit
    ) {
        val metaId = boundedIntegerProjection(
            column = "metaId",
            alias = BOUNDED_CHAIN_META_ID
        )
        val accountId = boundedBlobProjection(
            column = "accountId",
            alias = BOUNDED_CHAIN_ACCOUNT_ID,
            maxBytes = MAX_CHAIN_ACCOUNT_ID_READ_BYTES
        )
        val publicKey = boundedBlobProjection(
            column = "chain_accounts.publicKey",
            alias = BOUNDED_CHAIN_PUBLIC_KEY,
            maxBytes = MAX_CHAIN_ACCOUNT_PUBLIC_KEY_BYTES
        )
        val cryptoType = boundedTextProjection(
            column = "chain_accounts.cryptoType",
            alias = BOUNDED_CHAIN_CRYPTO_TYPE,
            maxBytes = MAX_CRYPTO_TYPE_BYTES
        )
        val ecosystem = boundedTextProjection(
            column = "chains.ecosystem",
            alias = BOUNDED_CHAIN_ECOSYSTEM,
            maxBytes = MAX_ECOSYSTEM_BYTES
        )

        database.query(
            "SELECT $metaId, $accountId, $publicKey, $cryptoType, $ecosystem " +
                "FROM chain_accounts " +
                "INNER JOIN chains ON chains.id = chain_accounts.chainId " +
                "ORDER BY chain_accounts.rowid ASC " +
                "LIMIT ${rowLimits.maxChainAccountRows + 1}"
        ).use { cursor ->
            var rowCount = 0
            while (cursor.moveToNext()) {
                if (rowCount == rowLimits.maxChainAccountRows) {
                    throw WalletPublicIdentityIntegrityException(
                        "Chain-account rows exceed the safe migration row limit"
                    )
                }
                rowCount += 1
                val boundedMetaId = cursor.readBoundedInteger(
                    BOUNDED_CHAIN_META_ID
                )
                val exactMetaId = boundedMetaId.value
                if (
                    !boundedMetaId.hasExpectedStorageClass ||
                    exactMetaId == null ||
                    exactMetaId <= 0L
                ) {
                    throw WalletPublicIdentityIntegrityException(
                        "A chain-account row has no safe positive integer wallet id"
                    )
                }
                val boundedAccountId = cursor.readBoundedBlob(
                    BOUNDED_CHAIN_ACCOUNT_ID,
                    MAX_CHAIN_ACCOUNT_ID_READ_BYTES
                )
                if (boundedAccountId.isOversized) {
                    throw WalletPublicIdentityIntegrityException(
                        "A chain-account id exceeds the safe database read limit"
                    )
                }
                val exactAccountId = boundedAccountId.value
                    ?: throw WalletPublicIdentityIntegrityException(
                        "A chain-account row has no account id"
                    )
                val boundedCryptoType = cursor.readBoundedText(
                    BOUNDED_CHAIN_CRYPTO_TYPE,
                    MAX_CRYPTO_TYPE_BYTES
                )
                action(
                    ChainAccountPublicIdentity(
                        metaId = exactMetaId,
                        accountId = exactAccountId,
                        publicKey = cursor.readBoundedBlob(
                            BOUNDED_CHAIN_PUBLIC_KEY,
                            MAX_CHAIN_ACCOUNT_PUBLIC_KEY_BYTES
                        ).valueOrInvalidPresentSentinel()
                            ?: ByteArray(0),
                        cryptoTypeText = boundedCryptoType.value,
                        ecosystemText = cursor.readBoundedText(
                            BOUNDED_CHAIN_ECOSYSTEM,
                            MAX_ECOSYSTEM_BYTES
                        ).value
                    )
                )
            }
        }
    }

    private fun chainAccountSecretKey(
        identity: ChainAccountPublicIdentity
    ): String {
        return "${identity.metaId}:" +
            "${identity.accountId.toHexString()}:ACCESS_SECRETS"
    }

    private sealed interface SecretAction {
        data class Replace(
            val expectedStates:
            Map<String, EncryptedPreferenceSnapshot?>,
            val valuesToPut: Map<String, String>,
            val keysToRemove: Set<String>
        ) : SecretAction

        data class Quarantine(
            val activeKey: String,
            val expectedSnapshot: EncryptedPreferenceSnapshot
        ) : SecretAction

        data class MarkPublicIdentityRecovery(
            val metaId: Long,
            val activeKey: String
        ) : SecretAction
    }

    private data class ChainAccountPublicIdentity(
        val metaId: Long,
        val accountId: ByteArray,
        val publicKey: ByteArray,
        val cryptoTypeText: String?,
        val ecosystemText: String?
    ) {
        fun addressKind(): ChainAccountAddressKind? {
            val ecosystem = Ecosystem.entries.firstOrNull {
                it.name.equals(ecosystemText, ignoreCase = true)
            } ?: return null
            return when (ecosystem) {
                Ecosystem.Substrate -> ChainAccountAddressKind.SUBSTRATE
                Ecosystem.Ethereum,
                Ecosystem.EthereumBased -> ChainAccountAddressKind.ETHEREUM
                Ecosystem.Ton -> null
            }
        }
    }

    private enum class ChainAccountAddressKind {
        SUBSTRATE,
        ETHEREUM
    }

    private companion object {
        const val SUBSTRATE_ACCOUNT_ID_BYTES = 32
        const val SUBSTRATE_PUBLIC_KEY_BYTES = 32
        const val ECDSA_PUBLIC_KEY_BYTES = 33
        const val ETHEREUM_ADDRESS_BYTES = 20
        const val TON_PUBLIC_KEY_BYTES = 32
        const val MAX_CHAIN_ACCOUNT_ID_READ_BYTES = 64
        const val MAX_CHAIN_ACCOUNT_PUBLIC_KEY_BYTES = 33
        const val MAX_ECOSYSTEM_BYTES = 32
        const val BOUNDED_CHAIN_META_ID = "boundedChainMetaId"
        const val BOUNDED_CHAIN_ACCOUNT_ID = "boundedChainAccountId"
        const val BOUNDED_CHAIN_PUBLIC_KEY = "boundedChainPublicKey"
        const val BOUNDED_CHAIN_CRYPTO_TYPE = "boundedChainCryptoType"
        const val BOUNDED_CHAIN_ECOSYSTEM = "boundedChainEcosystem"

        fun CryptoType.publicKeyBytes(): Int = when (this) {
            CryptoType.SR25519,
            CryptoType.ED25519 -> SUBSTRATE_PUBLIC_KEY_BYTES
            CryptoType.ECDSA -> ECDSA_PUBLIC_KEY_BYTES
        }

        fun legacySecretKey(metaId: Long) = "$metaId:ACCESS_SECRETS"
        fun substrateSecretKey(metaId: Long) = "$metaId:SUBSTRATE_SECRETS"
        fun ethereumSecretKey(metaId: Long) = "$metaId:ETHEREUM_SECRETS"
        fun tonSecretKey(metaId: Long) = "$metaId:TON_SECRETS"
    }
}
