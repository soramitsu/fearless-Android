package jp.co.soramitsu.coredb.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.legacy.LegacyWalletV04Secrets
import jp.co.soramitsu.common.data.secrets.v1.LegacyV1SecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.LegacySubstrateSecretValidation
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidation
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.data.storage.encrypt.replaceEncryptedStringsForStatesDurably
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.WithDerivationPath
import jp.co.soramitsu.core.model.WithMnemonic
import jp.co.soramitsu.core.model.WithSeed
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.converters.CryptoTypeConverters
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.runBlocking

internal class MigratingAccount(
    val address: String,
    val publicKey: ByteArray,
    val name: String,
    val cryptoType: CryptoType
)

internal class V2MigrationSecretCorruptionException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

internal interface V2MigrationCryptography {

    fun decodeEthereumPath(path: String): JunctionDecoder.DecodeResult

    fun deriveEthereumKeypair(
        entropy: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult
    ): FearlessKeypair
}

internal data class V2MigrationLimits(
    val maxAccountPlans: Int,
    val maxRetainedSecretCharacters: Long
) {
    init {
        require(maxAccountPlans in 1 until Int.MAX_VALUE)
        require(maxRetainedSecretCharacters > 0)
    }

    companion object {
        val PRODUCTION = V2MigrationLimits(
            maxAccountPlans = 4_096,
            // Kotlin/JVM Strings retain roughly two bytes per character.
            // This permits about 64 MiB of validated plaintext while keeping
            // direct-upgrade preparation bounded on memory-constrained phones.
            maxRetainedSecretCharacters = 33_554_432L
        )
    }
}

class V2Migration internal constructor(
    private val storeV1: SecretStoreV1,
    private val encryptedPreferences: EncryptedPreferences,
    private val cryptography: V2MigrationCryptography,
    private val legacySubstrateSecretValidation:
    LegacySubstrateSecretValidation,
    private val walletRootSecretValidation: WalletRootSecretValidation,
    private val limits: V2MigrationLimits = V2MigrationLimits.PRODUCTION,
    private val walletRowLimits: WalletMigrationRowLimits =
        WalletMigrationRowLimits.PRODUCTION
) : Migration(28, 29) {

    constructor(
        storeV1: SecretStoreV1,
        encryptedPreferences: EncryptedPreferences
    ) : this(
        storeV1 = storeV1,
        encryptedPreferences = encryptedPreferences,
        cryptography = ProductionV2MigrationCryptography,
        legacySubstrateSecretValidation = WalletRootSecretValidator,
        walletRootSecretValidation = WalletRootSecretValidator
    )

    internal constructor(
        storeV1: SecretStoreV1,
        encryptedPreferences: EncryptedPreferences,
        limits: V2MigrationLimits,
        walletRowLimits: WalletMigrationRowLimits =
            WalletMigrationRowLimits.PRODUCTION
    ) : this(
        storeV1 = storeV1,
        encryptedPreferences = encryptedPreferences,
        cryptography = ProductionV2MigrationCryptography,
        legacySubstrateSecretValidation = WalletRootSecretValidator,
        walletRootSecretValidation = WalletRootSecretValidator,
        limits = limits,
        walletRowLimits = walletRowLimits
    )

    private val cryptoTypeConverters = CryptoTypeConverters()

    /**
     * Migrates from v1 db and secrets model to v2.
     * Note, than old (v1) secrets as well as accounts will not be deleted to be able to restore them in case of critical bug in this migration
     */
    override fun migrate(database: SupportSQLiteDatabase) = runBlocking {
        requireInitialSqlSafety(database)
        encryptedPreferences.requireDurableStorageHealthy()

        val ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        val decodedEthereumDerivationPath = performCryptography(
            operation = "default Ethereum derivation-path decoding"
        ) {
            cryptography.decodeEthereumPath(ethereumDerivationPath)
        }

        val migrationPlans = mutableListOf<V2AccountMigrationPlan>()
        var retainedSecretCharacters = 0L
        forEachMigratingAccount(database) { index, account ->
            if (index >= limits.maxAccountPlans) {
                throw WalletPublicIdentityIntegrityException(
                    "V2 migration exceeds the safe wallet-count limit"
                )
            }
            val legacySecretKey = "security_source_${account.address}"
            val preparation = prepareLegacySecret(
                account = account,
                legacySecretKey = legacySecretKey,
                ethereumDerivationPath = ethereumDerivationPath,
                decodedEthereumDerivationPath = decodedEthereumDerivationPath
            )
            val preparedSecrets = preparation.preparedSecrets
            val keypair = preparedSecrets?.substrateKeypair
            val ethereumKeypair = preparedSecrets?.ethereumKeypair
            val ethereumAddress = ethereumKeypair?.let {
                performCryptography("legacy Ethereum address derivation") {
                    it.publicKey.ethereumAddressFromPublicKey()
                }
            }

            val isSelected = index == 0 // mark first account as selected

            val metaAccount = MetaAccountLocal(
                substratePublicKey = keypair?.publicKey ?: account.publicKey,
                substrateAccountId = (keypair?.publicKey ?: account.publicKey).substrateAccountId(),
                substrateCryptoType = account.cryptoType,
                ethereumPublicKey = ethereumKeypair?.publicKey,
                ethereumAddress = ethereumAddress,
                tonPublicKey = null,
                name = account.name,
                isSelected = isSelected,
                position = index,
                isBackedUp = false,
                googleBackupAddress = null,
                initialized = false
            )

            val metaId = insertMetaAccount(metaAccount, database)
            if (metaId <= 0L) {
                throw WalletPublicIdentityIntegrityException(
                    "Unable to assign a safe positive wallet id during V2 migration"
                )
            }
            val deterministicTarget =
                (preparation as? LegacySecretPreparation.Prepared)
                    ?.secrets?.encodedV2Secrets
            val target = prepareV2Target(
                metaId = metaId,
                metaAccount = metaAccount,
                deterministicAccessPlaintext = deterministicTarget
            )
            val additionalSecretCharacters =
                preparation.retainedSecretCharacters() +
                    target.expectedStates.values
                        .filterNotNull()
                        .sumOf { it.plaintext.length.toLong() }
            if (
                additionalSecretCharacters >
                    limits.maxRetainedSecretCharacters -
                    retainedSecretCharacters
            ) {
                throw WalletPublicIdentityIntegrityException(
                    "V2 migration exceeds the safe prepared-secret memory limit"
                )
            }
            retainedSecretCharacters += additionalSecretCharacters
            migrationPlans += V2AccountMigrationPlan(
                metaId = metaId,
                accountPublicKey = account.publicKey,
                preparation = preparation,
                target = target
            )
        }

        // V2 is the first preference-writing edge in a direct 28 -> current
        // Room upgrade. Complete every read-only preparation and SQL insert,
        // then validate all downstream deterministic failure conditions before
        // any external preference mutation can outlive a SQL rollback.
        WalletSecretMigrationPreflight.requireSafeToMutate(
            database = database,
            encryptedPreferences = encryptedPreferences,
            includeTonPublicKey = false,
            rowLimits = walletRowLimits
        )

        val expectedStates =
            linkedMapOf<String, EncryptedPreferenceSnapshot?>()
        val valuesToPut = linkedMapOf<String, String>()
        val snapshotMoves =
            mutableListOf<EncryptedPreferenceSnapshotMove>()
        val moveDestinations = linkedSetOf<String>()
        migrationPlans.forEach { plan ->
            val prepared =
                plan.preparation as? LegacySecretPreparation.Prepared
            val target = plan.target
            prepared?.let {
                it.validationSourceStates.forEach { (key, snapshot) ->
                    check(key !in expectedStates) {
                        "A V2 migration produced a duplicate source state"
                    }
                    expectedStates[key] = snapshot
                }
                if (target.shouldWriteAccessSecret) {
                    check(target.key !in valuesToPut) {
                        "A V2 migration produced a duplicate secret write"
                    }
                    valuesToPut[target.key] =
                        it.secrets.encodedV2Secrets
                }
            }
            target.expectedStates.forEach { (key, snapshot) ->
                check(key !in expectedStates) {
                    "A V2 migration produced a duplicate secret target"
                }
                expectedStates[key] = snapshot
            }
            plan.preparation.pendingQuarantines.forEach { pending ->
                val preferredDestination = pending.target.keyFor(
                    metaId = plan.metaId,
                    accountPublicKey = plan.accountPublicKey,
                    sourceKey = pending.sourceKey
                )
                val destination = if (
                    pending.target == LegacyQuarantineTarget.V04_META &&
                    (
                        preferredDestination in moveDestinations ||
                            encryptedPreferences.hasKey(preferredDestination)
                        )
                ) {
                    WalletSecretQuarantine.keyFor(pending.sourceKey)
                } else {
                    preferredDestination
                }
                check(moveDestinations.add(destination)) {
                    "A V2 migration produced duplicate quarantine destinations"
                }
                snapshotMoves += EncryptedPreferenceSnapshotMove(
                    sourceKey = pending.sourceKey,
                    destinationKey = destination,
                    expectedSnapshot = pending.expectedSnapshot
                )
            }
        }
        if (
            expectedStates.isNotEmpty() ||
            valuesToPut.isNotEmpty() ||
            snapshotMoves.isNotEmpty()
        ) {
            encryptedPreferences
                .replaceEncryptedStringsForStatesDurably(
                    expectedStates = expectedStates,
                    valuesToPut = valuesToPut,
                    keysToRemove = emptySet(),
                    snapshotMoves = snapshotMoves
                )
        }
    }

    /**
     * A direct 28 -> current upgrade is one Room transaction. TON can therefore
     * durably split ACCESS_SECRETS before a later SQL failure rolls the database
     * all the way back to v28. On retry, accept only the exact deterministic V3
     * after-image TON would have produced. Every root key is retained in the
     * final CAS so a concurrent mixed or partial representation cannot be
     * published over.
     */
    private fun prepareV2Target(
        metaId: Long,
        metaAccount: MetaAccountLocal,
        deterministicAccessPlaintext: String?
    ): PreparedV2Target {
        val accessKey = "$metaId:ACCESS_SECRETS"
        val substrateKey = "$metaId:SUBSTRATE_SECRETS"
        val ethereumKey = "$metaId:ETHEREUM_SECRETS"
        val tonKey = "$metaId:TON_SECRETS"
        val expectedStates = linkedMapOf(
            accessKey to readOptionalSecretSnapshot(accessKey),
            substrateKey to readOptionalSecretSnapshot(substrateKey),
            ethereumKey to readOptionalSecretSnapshot(ethereumKey),
            tonKey to readOptionalSecretSnapshot(tonKey)
        )
        val accessSnapshot = expectedStates.getValue(accessKey)
        val separatedKeys = listOf(substrateKey, ethereumKey, tonKey)
        val hasSeparatedState =
            separatedKeys.any { expectedStates.getValue(it) != null }

        if (accessSnapshot != null) {
            if (hasSeparatedState) {
                throw WalletSecretConcurrentMutationException(
                    "A V2 migration target has simultaneous V2 and V3 root secrets"
                )
            }
            if (accessSnapshot.plaintext != deterministicAccessPlaintext) {
                throw WalletSecretConcurrentMutationException(
                    "A V2 migration target contains conflicting wallet material"
                )
            }
            return PreparedV2Target(
                key = accessKey,
                expectedStates = expectedStates,
                shouldWriteAccessSecret = false
            )
        }

        if (!hasSeparatedState) {
            return PreparedV2Target(
                key = accessKey,
                expectedStates = expectedStates,
                shouldWriteAccessSecret =
                    deterministicAccessPlaintext != null
            )
        }

        val exactAccessPlaintext = deterministicAccessPlaintext
            ?: throw WalletSecretConcurrentMutationException(
                "A V2 migration target has V3 roots without deterministic wallet material"
            )
        val expectedSplit =
            WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                encoded = exactAccessPlaintext,
                identity = WalletPublicIdentity(
                    metaId = metaId,
                    substratePublicKey = metaAccount.substratePublicKey,
                    substrateCryptoType = metaAccount.substrateCryptoType,
                    substrateCryptoTypeWasPresent =
                        metaAccount.substrateCryptoType != null,
                    substrateAccountId = metaAccount.substrateAccountId,
                    ethereumPublicKey = metaAccount.ethereumPublicKey,
                    ethereumAddress = metaAccount.ethereumAddress,
                    tonPublicKey = null
                ),
                walletRootSecretValidation = walletRootSecretValidation
            )
        val exactSeparatedPlaintexts = mapOf(
            substrateKey to expectedSplit.substratePlaintext,
            ethereumKey to expectedSplit.ethereumPlaintext,
            tonKey to null
        )
        val hasExactSeparatedState =
            exactSeparatedPlaintexts.all { (key, expectedPlaintext) ->
                expectedStates.getValue(key)?.plaintext == expectedPlaintext
            }
        if (!hasExactSeparatedState) {
            throw WalletSecretConcurrentMutationException(
                "A V2 migration target contains a partial or conflicting V3 root-secret split"
            )
        }

        return PreparedV2Target(
            key = accessKey,
            expectedStates = expectedStates,
            shouldWriteAccessSecret = false
        )
    }

    private fun readOptionalSecretSnapshot(
        key: String
    ): EncryptedPreferenceSnapshot? {
        if (!encryptedPreferences.hasKey(key)) return null

        return checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(key)
        ) {
            "A V2 migration root secret disappeared during preparation"
        }
    }

    /**
     * The legacy query must sort by user position to preserve selection order.
     * Version 28 has no supporting position index, so LIMIT alone would still
     * scan and sort the complete users table. These rowid-ordered probes stop
     * after max + 1 rows and run before any encrypted-preference lookup.
     *
     * The bounded foreign-key pass also validates every retained v28 child
     * relationship before legacy secret preparation. The meta/chain probes are
     * repeated by the full downstream preflight after V2 inserts its SQL rows.
     */
    private fun requireInitialSqlSafety(database: SupportSQLiteDatabase) {
        requireBoundedMigrationTableRows(
            database = database,
            tableName = "users",
            maximumRows = limits.maxAccountPlans,
            rowDescription = "Legacy wallet rows"
        )
        requireSafeLegacyUserPositions(database)
        requireBoundedMigrationTableRows(
            database = database,
            tableName = "meta_accounts",
            maximumRows = walletRowLimits.maxWalletRows,
            rowDescription = "Wallet rows"
        )
        requireBoundedMigrationTableRows(
            database = database,
            tableName = "chain_accounts",
            maximumRows = walletRowLimits.maxChainAccountRows,
            rowDescription = "Chain-account rows"
        )
        requireBoundedForeignKeyCheck(
            database = database,
            limits = DB28_TO_31_FOREIGN_KEY_CHECK_LIMITS,
            previouslyBoundedRows = mapOf(
                "chain_accounts" to walletRowLimits.maxChainAccountRows
            )
        ) { message ->
            WalletPublicIdentityIntegrityException("Version 28 $message")
        }
    }

    private fun requireSafeLegacyUserPositions(
        database: SupportSQLiteDatabase
    ) {
        val position = boundedIntegerProjection(
            column = "position",
            alias = BOUNDED_LEGACY_POSITION
        )
        database.query(
            "SELECT $position FROM users ORDER BY rowid ASC " +
                "LIMIT ${limits.maxAccountPlans + 1}"
        ).use { cursor ->
            var rowCount = 0
            while (cursor.moveToNext()) {
                if (rowCount == limits.maxAccountPlans) {
                    throw WalletPublicIdentityIntegrityException(
                        "Legacy wallet rows exceed the safe migration row limit"
                    )
                }
                rowCount += 1
                val boundedPosition = cursor.readBoundedInteger(
                    BOUNDED_LEGACY_POSITION
                )
                val exactPosition = boundedPosition.value
                if (
                    !boundedPosition.hasExpectedStorageClass ||
                    exactPosition == null ||
                    exactPosition !in 0L..Int.MAX_VALUE.toLong()
                ) {
                    throw WalletPublicIdentityIntegrityException(
                        "A legacy wallet row has no safe non-negative Int position"
                    )
                }
            }
        }
    }

    private suspend fun prepareLegacySecret(
        account: MigratingAccount,
        legacySecretKey: String,
        ethereumDerivationPath: String,
        decodedEthereumDerivationPath: JunctionDecoder.DecodeResult
    ): LegacySecretPreparation {
        if (!encryptedPreferences.hasKey(legacySecretKey)) {
            val v04Preparation = prepareV04Secret(
                account = account,
                ethereumDerivationPath = ethereumDerivationPath,
                decodedEthereumDerivationPath = decodedEthereumDerivationPath
            )
            if (v04Preparation !is LegacySecretPreparation.Absent) {
                return v04Preparation
            }

            val exactV1Quarantine =
                WalletSecretQuarantine.keyFor(legacySecretKey)
            if (encryptedPreferences.hasKey(exactV1Quarantine)) {
                val quarantinedSnapshot = checkNotNull(
                    encryptedPreferences.getDecryptedStringSnapshot(
                        exactV1Quarantine
                    )
                ) {
                    "A quarantined V1 wallet secret disappeared during migration"
                }
                return LegacySecretPreparation.RecoveryRequired(
                    pendingQuarantines = listOf(
                        PendingLegacyQuarantine(
                            sourceKey = exactV1Quarantine,
                            target = LegacyQuarantineTarget.V1_META,
                            expectedSnapshot = quarantinedSnapshot
                        )
                    )
                )
            }
            return LegacySecretPreparation.Absent
        }

        val legacySnapshot = checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(legacySecretKey)
        ) {
            "A V1 wallet secret disappeared during migration"
        }
        val encodedLegacySecret = legacySnapshot.plaintext
        val source = try {
            if (encodedLegacySecret.isEmpty()) {
                throw LegacyV1SecretCorruptionException(
                    "A V1 wallet secret is empty"
                )
            }
            checkNotNull(storeV1.getSecuritySource(account.address)) {
                "Unable to decode an active V1 wallet secret"
            }
        } catch (failure: LegacyV1SecretCorruptionException) {
            return prepareBrokenV1Fallback(
                account = account,
                legacySecretKey = legacySecretKey,
                expectedSnapshot = legacySnapshot,
                ethereumDerivationPath = ethereumDerivationPath,
                decodedEthereumDerivationPath = decodedEthereumDerivationPath
            )
        }

        val prepared = try {
            legacySubstrateSecretValidation.validate(
                source = source,
                expectedPublicKey = account.publicKey,
                expectedCryptoType = account.cryptoType,
                expectedAccountId = account.publicKey.substrateAccountId()
            )
            val mnemonic = (source as? WithMnemonic)?.let {
                parseMnemonic(it.mnemonic)
            }
            val entropy = mnemonic?.entropy
            val derivationPath =
                (source as? WithDerivationPath)?.derivationPath
            val seed = (source as? WithSeed)?.seed
            ensureLocal(
                seed == null || seed.size == SEED_LENGTH_BYTES,
                "A V1 wallet seed has an invalid length"
            )
            val ethereumKeypair = entropy?.let {
                deriveEthereumKeypair(it, decodedEthereumDerivationPath)
            }
            val secretsV2 = MetaAccountSecrets(
                substrateKeyPair = source.keypair,
                entropy = entropy,
                seed = seed,
                substrateDerivationPath = derivationPath,
                ethereumKeypair = ethereumKeypair,
                ethereumDerivationPath = ethereumDerivationPath.takeIf {
                    ethereumKeypair != null
                },
                tonKeypair = null
            )
            PreparedV2Secrets(
                substrateKeypair = source.keypair,
                ethereumKeypair = ethereumKeypair,
                encodedV2Secrets = secretsV2.toHexString()
            )
        } catch (failure: WalletRootSecretCorruptionException) {
            null
        } catch (failure: V2MigrationSecretCorruptionException) {
            null
        }
        return if (prepared == null) {
            prepareBrokenV1Fallback(
                account = account,
                legacySecretKey = legacySecretKey,
                expectedSnapshot = legacySnapshot,
                ethereumDerivationPath = ethereumDerivationPath,
                decodedEthereumDerivationPath = decodedEthereumDerivationPath
            )
        } else {
            LegacySecretPreparation.Prepared(
                secrets = prepared,
                validationSourceStates = mapOf(
                    legacySecretKey to legacySnapshot
                )
            )
        }
    }

    private fun prepareBrokenV1Fallback(
        account: MigratingAccount,
        legacySecretKey: String,
        expectedSnapshot: EncryptedPreferenceSnapshot,
        ethereumDerivationPath: String,
        decodedEthereumDerivationPath: JunctionDecoder.DecodeResult
    ): LegacySecretPreparation {
        val fallback = prepareV04Secret(
            account = account,
            ethereumDerivationPath = ethereumDerivationPath,
            decodedEthereumDerivationPath =
                decodedEthereumDerivationPath
        )
        val target = if (fallback is LegacySecretPreparation.Absent) {
            LegacyQuarantineTarget.V1_META
        } else {
            LegacyQuarantineTarget.V1_GENERIC
        }
        val pendingV1 = PendingLegacyQuarantine(
            sourceKey = legacySecretKey,
            target = target,
            expectedSnapshot = expectedSnapshot
        )

        return when (fallback) {
            LegacySecretPreparation.Absent ->
                LegacySecretPreparation.RecoveryRequired(
                    pendingQuarantines = listOf(pendingV1)
                )

            is LegacySecretPreparation.Prepared -> fallback.copy(
                pendingQuarantines =
                    listOf(pendingV1) + fallback.pendingQuarantines
            )

            is LegacySecretPreparation.RecoveryRequired -> fallback.copy(
                pendingQuarantines =
                    listOf(pendingV1) + fallback.pendingQuarantines
            )
        }
    }

    private fun prepareV04Secret(
        account: MigratingAccount,
        ethereumDerivationPath: String,
        decodedEthereumDerivationPath: JunctionDecoder.DecodeResult
    ): LegacySecretPreparation {
        val privateKey = LegacyWalletV04Secrets.privateKey(account.address)
        if (!encryptedPreferences.hasKey(privateKey)) {
            // Version 1.0.0 converted these preferences asynchronously rather
            // than through Room. A direct v0.4.x -> current upgrade can still
            // reach database v28 with only the released split-key format.
            // If only an ancillary value survived, retain it byte-for-byte in
            // quarantine and expose explicit recovery instead of silently
            // publishing an unusable watch-only account.
            val strandedSnapshots = LegacyWalletV04Secrets.allKeys(account.address)
                .asSequence()
                .filterNot { it == privateKey }
                .mapNotNull { key ->
                    if (!encryptedPreferences.hasKey(key)) {
                        null
                    } else {
                        key to checkNotNull(
                            encryptedPreferences.getDecryptedStringSnapshot(key)
                        ) {
                            "A v0.4 ancillary secret disappeared during migration"
                        }
                    }
                }
                .toList()
            return if (strandedSnapshots.isEmpty()) {
                LegacySecretPreparation.Absent
            } else {
                LegacySecretPreparation.RecoveryRequired(
                    pendingQuarantines = strandedSnapshots.mapIndexed { index, entry ->
                        val (key, snapshot) = entry
                        PendingLegacyQuarantine(
                            sourceKey = key,
                            target = if (index == 0) {
                                LegacyQuarantineTarget.V04_META
                            } else {
                                LegacyQuarantineTarget.V04_GENERIC
                            },
                            expectedSnapshot = snapshot
                        )
                    }
                )
            }
        }

        val signingDataSnapshot = checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(privateKey)
        ) {
            "A v0.4 wallet signing secret disappeared during migration"
        }
        val encodedSigningData = signingDataSnapshot.plaintext
        val seedKey = LegacyWalletV04Secrets.seedKey(account.address)
        val entropyKey = LegacyWalletV04Secrets.entropyKey(account.address)
        val derivationKey =
            LegacyWalletV04Secrets.derivationKey(account.address)
        val seedSnapshot = readOptionalV04Snapshot(seedKey)
        val entropySnapshot = readOptionalV04Snapshot(entropyKey)
        val derivationSnapshot = readOptionalV04Snapshot(derivationKey)
        val validationSourceStates = linkedMapOf(
            privateKey to signingDataSnapshot,
            seedKey to seedSnapshot,
            entropyKey to entropySnapshot,
            derivationKey to derivationSnapshot
        )
        val prepared = try {
            ensureLocal(
                encodedSigningData.isNotEmpty(),
                "A v0.4 wallet signing secret is empty"
            )
            val signingData =
                LegacyWalletV04Secrets.decodeSigningData(encodedSigningData)
            val substrateKeypair = Keypair(
                publicKey = signingData.publicKey,
                privateKey = signingData.privateKey,
                nonce = signingData.nonce
            )
            ensureLocal(
                substrateKeypair.publicKey.contentEquals(account.publicKey),
                "A v0.4 wallet secret does not match its public account"
            )

            val seed = readV04Hex(
                encoded = seedSnapshot?.plaintext,
                validSizes = setOf(SEED_LENGTH_BYTES)
            )
            val entropy = readV04Hex(
                encoded = entropySnapshot?.plaintext,
                validSizes = VALID_ENTROPY_LENGTHS
            )
            val derivationPath =
                readV04DerivationPath(derivationSnapshot?.plaintext)
            walletRootSecretValidation.validateSubstrateAndSanitize(
                encoded = SubstrateSecrets(
                    substrateKeyPair = substrateKeypair,
                    entropy = entropy,
                    seed = seed,
                    substrateDerivationPath = derivationPath
                ).toHexString(),
                expectedPublicKey = account.publicKey,
                expectedCryptoType = account.cryptoType,
                expectedAccountId = account.publicKey.substrateAccountId()
            )
            val ethereumKeypair = entropy?.let {
                deriveEthereumKeypair(it, decodedEthereumDerivationPath)
            }
            val secretsV2 = MetaAccountSecrets(
                substrateKeyPair = substrateKeypair,
                entropy = entropy,
                seed = seed,
                substrateDerivationPath = derivationPath,
                ethereumKeypair = ethereumKeypair,
                ethereumDerivationPath = ethereumDerivationPath.takeIf {
                    ethereumKeypair != null
                },
                tonKeypair = null
            )

            PreparedV2Secrets(
                substrateKeypair = substrateKeypair,
                ethereumKeypair = ethereumKeypair,
                encodedV2Secrets = secretsV2.toHexString()
            )
        } catch (failure: WalletRootSecretCorruptionException) {
            null
        } catch (failure: V2MigrationSecretCorruptionException) {
            null
        } catch (failure: jp.co.soramitsu.common.data.secrets.WalletSecretScaleCorruptionException) {
            null
        }
        return prepared?.let {
            LegacySecretPreparation.Prepared(
                secrets = it,
                validationSourceStates = validationSourceStates
            )
        }
            ?: LegacySecretPreparation.RecoveryRequired(
                pendingQuarantines = buildList {
                    add(
                        PendingLegacyQuarantine(
                            sourceKey = privateKey,
                            target = LegacyQuarantineTarget.V04_META,
                            expectedSnapshot = signingDataSnapshot
                        )
                    )
                    listOf(
                        seedKey to seedSnapshot,
                        entropyKey to entropySnapshot,
                        derivationKey to derivationSnapshot
                    ).forEach { (key, optionalSnapshot) ->
                        optionalSnapshot?.let { snapshot ->
                            add(
                                PendingLegacyQuarantine(
                                    sourceKey = key,
                                    target = LegacyQuarantineTarget.V04_GENERIC,
                                    expectedSnapshot = snapshot
                                )
                            )
                        }
                    }
                }
            )
    }

    private fun readOptionalV04Snapshot(
        key: String
    ): EncryptedPreferenceSnapshot? {
        if (!encryptedPreferences.hasKey(key)) return null
        return checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(key)
        ) {
            "A v0.4 wallet value disappeared during migration"
        }
    }

    private fun readV04Hex(
        encoded: String?,
        validSizes: Set<Int>
    ): ByteArray? {
        if (encoded == null) return null
        ensureLocal(
            encoded.isNotEmpty() &&
                encoded.length <= MAX_V04_HEX_CHARS,
            "Unable to decode bounded v0.4 wallet material"
        )
        ensureLocal(
            encoded.length % 2 == 0 &&
                encoded.all { character ->
                    character in '0'..'9' ||
                        character in 'a'..'f'
                },
            "v0.4 wallet material is not canonical hexadecimal data"
        )

        val decoded = try {
            encoded.fromHex()
        } catch (failure: IllegalArgumentException) {
            throw V2MigrationSecretCorruptionException(
                "Unable to decode v0.4 hexadecimal wallet material",
                failure
            )
        }
        ensureLocal(
            decoded.size in validSizes,
            "v0.4 wallet material has an invalid length"
        )
        return decoded
    }

    private fun readV04DerivationPath(path: String?): String? {
        if (path == null) return null
        ensureLocal(
            path.length <= MAX_DERIVATION_PATH_CHARS,
            "Unable to decode a bounded v0.4 derivation path"
        )
        return path
    }

    private fun parseMnemonic(
        mnemonicText: String
    ): jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic {
        return try {
            MnemonicCreator.fromWords(mnemonicText)
        } catch (failure: Bip39Exception) {
            throw V2MigrationSecretCorruptionException(
                "A V1 wallet mnemonic is invalid",
                failure
            )
        } catch (failure: Exception) {
            throw WalletSecureStorageUnavailableException(
                "Wallet cryptography is unavailable during V1 mnemonic parsing",
                failure
            )
        }
    }

    private fun deriveEthereumKeypair(
        entropy: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult
    ): FearlessKeypair {
        return performCryptography("legacy Ethereum key derivation") {
            cryptography.deriveEthereumKeypair(
                entropy = entropy.clone(),
                decodedPath = decodedPath
            )
        }
    }

    private inline fun <T> performCryptography(
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

    private fun ensureLocal(condition: Boolean, message: String) {
        if (!condition) {
            throw V2MigrationSecretCorruptionException(message)
        }
    }

    private data class PreparedV2Secrets(
        val substrateKeypair: FearlessKeypair,
        val ethereumKeypair: FearlessKeypair?,
        val encodedV2Secrets: String
    )

    private data class V2AccountMigrationPlan(
        val metaId: Long,
        val accountPublicKey: ByteArray,
        val preparation: LegacySecretPreparation,
        val target: PreparedV2Target
    )

    private data class PreparedV2Target(
        val key: String,
        val expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
        val shouldWriteAccessSecret: Boolean
    )

    private sealed interface LegacySecretPreparation {
        val preparedSecrets: PreparedV2Secrets?
        val pendingQuarantines: List<PendingLegacyQuarantine>

        data class Prepared(
            val secrets: PreparedV2Secrets,
            val validationSourceStates:
            Map<String, EncryptedPreferenceSnapshot?>,
            override val pendingQuarantines:
            List<PendingLegacyQuarantine> = emptyList()
        ) : LegacySecretPreparation {
            override val preparedSecrets = secrets
        }

        data class RecoveryRequired(
            override val pendingQuarantines:
                List<PendingLegacyQuarantine>
        ) : LegacySecretPreparation {
            override val preparedSecrets: PreparedV2Secrets? = null
        }

        data object Absent : LegacySecretPreparation {
            override val preparedSecrets: PreparedV2Secrets? = null
            override val pendingQuarantines:
                List<PendingLegacyQuarantine> = emptyList()
        }
    }

    private fun LegacySecretPreparation.retainedSecretCharacters(): Long {
        val preparedCharacters = (this as? LegacySecretPreparation.Prepared)
            ?.let {
                it.secrets.encodedV2Secrets.length.toLong() +
                    it.validationSourceStates.values
                        .filterNotNull()
                        .sumOf { snapshot ->
                            snapshot.plaintext.length.toLong()
                        }
            } ?: 0L
        val quarantineCharacters = pendingQuarantines.sumOf {
            it.expectedSnapshot.plaintext.length.toLong()
        }
        return preparedCharacters + quarantineCharacters
    }

    private data class PendingLegacyQuarantine(
        val sourceKey: String,
        val target: LegacyQuarantineTarget,
        val expectedSnapshot: EncryptedPreferenceSnapshot
    )

    private enum class LegacyQuarantineTarget {
        V1_GENERIC,
        V1_META,
        V04_META,
        V04_GENERIC;

        fun keyFor(
            metaId: Long,
            accountPublicKey: ByteArray,
            sourceKey: String
        ): String {
            return when (this) {
                V1_GENERIC -> WalletSecretQuarantine.keyFor(sourceKey)
                V1_META -> WalletSecretQuarantine.legacyV1KeyForMetaId(
                    metaId = metaId,
                    publicKey = accountPublicKey
                )
                V04_META -> WalletSecretQuarantine.legacyV04KeyForMetaId(
                    metaId = metaId,
                    publicKey = accountPublicKey
                )
                V04_GENERIC -> WalletSecretQuarantine.keyFor(sourceKey)
            }
        }
    }

    private suspend fun forEachMigratingAccount(
        database: SupportSQLiteDatabase,
        action: suspend (Int, MigratingAccount) -> Unit
    ) {
        val address = boundedTextProjection(
            column = "address",
            alias = "boundedAddress",
            maxBytes = MAX_LEGACY_ADDRESS_BYTES
        )
        val publicKey = boundedTextProjection(
            column = "publicKey",
            alias = "boundedPublicKey",
            maxBytes = MAX_LEGACY_PUBLIC_KEY_HEX_BYTES
        )
        val name = boundedTextProjection(
            column = "username",
            alias = "boundedName",
            maxBytes = MAX_LEGACY_NAME_BYTES
        )
        val cryptoType = boundedIntegerProjection(
            column = "cryptoType",
            alias = BOUNDED_LEGACY_CRYPTO_TYPE
        )
        val cursor = database.query(
            "SELECT $address, $publicKey, $cryptoType, $name " +
                "FROM users ORDER BY position ASC, rowid ASC " +
                "LIMIT ${limits.maxAccountPlans + 1}"
        )
        try {
            var index = 0
            while (cursor.moveToNext()) {
                val boundedAddress = cursor.readBoundedText(
                    alias = "boundedAddress",
                    maxBytes = MAX_LEGACY_ADDRESS_BYTES
                )
                val encodedPublicKey = cursor.readBoundedText(
                    alias = "boundedPublicKey",
                    maxBytes = MAX_LEGACY_PUBLIC_KEY_HEX_BYTES
                )
                val boundedName = cursor.readBoundedText(
                    alias = "boundedName",
                    maxBytes = MAX_LEGACY_NAME_BYTES
                )
                if (
                    boundedAddress.isOversized ||
                    encodedPublicKey.isOversized ||
                    boundedName.isOversized
                ) {
                    throw WalletPublicIdentityIntegrityException(
                        "A legacy wallet row exceeds its bounded identity schema"
                    )
                }
                val exactAddress = boundedAddress.value
                    ?.takeIf(String::isNotBlank)
                    ?: throw WalletPublicIdentityIntegrityException(
                        "A legacy wallet row has no valid account address"
                    )
                val exactPublicKey = encodedPublicKey.value
                    ?.takeIf(String::isNotEmpty)
                    ?: throw WalletPublicIdentityIntegrityException(
                        "A legacy wallet row has no public key"
                    )
                val exactName = boundedName.value
                    ?: throw WalletPublicIdentityIntegrityException(
                        "A legacy wallet row has no name"
                    )
                val boundedCryptoType = cursor.readBoundedInteger(
                    BOUNDED_LEGACY_CRYPTO_TYPE
                )
                val cryptoTypeOrdinal = boundedCryptoType.value
                if (
                    !boundedCryptoType.hasExpectedStorageClass ||
                    cryptoTypeOrdinal == null
                ) {
                    throw WalletPublicIdentityIntegrityException(
                        "A legacy wallet row has an invalid crypto type"
                    )
                }
                if (
                    cryptoTypeOrdinal < 0 ||
                    cryptoTypeOrdinal >= CryptoType.entries.size.toLong()
                ) {
                    throw WalletPublicIdentityIntegrityException(
                        "A legacy wallet row has an invalid crypto type"
                    )
                }
                val cryptoType =
                    CryptoType.entries[cryptoTypeOrdinal.toInt()]
                val decodedPublicKey = try {
                    exactPublicKey.fromHex()
                } catch (failure: Exception) {
                    throw WalletPublicIdentityIntegrityException(
                        "Unable to decode a legacy account public key",
                        failure
                    )
                }
                val expectedPublicKeyBytes = when (cryptoType) {
                    CryptoType.SR25519,
                    CryptoType.ED25519 -> 32
                    CryptoType.ECDSA -> 33
                }
                if (decodedPublicKey.size != expectedPublicKeyBytes) {
                    throw WalletPublicIdentityIntegrityException(
                        "A legacy account public key has an invalid length"
                    )
                }

                action(
                    index,
                    MigratingAccount(
                        address = exactAddress,
                        publicKey = decodedPublicKey,
                        cryptoType = cryptoType,
                        name = exactName
                    )
                )
                index += 1
            }
        } finally {
            cursor.close()
        }
    }

    /**
     * @return id of newly inserted account
     */
    private fun insertMetaAccount(metaAccountLocal: MetaAccountLocal, database: SupportSQLiteDatabase): Long {
        val contentValues = with(metaAccountLocal) {
            ContentValues().apply {
                put(MetaAccountLocal.Table.Column.ETHEREUM_ADDRESS, ethereumAddress)
                put(MetaAccountLocal.Table.Column.ETHEREUM_PUBKEY, ethereumPublicKey)
                put(MetaAccountLocal.Table.Column.NAME, name)
                put(MetaAccountLocal.Table.Column.SUBSTRATE_ACCOUNT_ID, substrateAccountId)
                put(MetaAccountLocal.Table.Column.SUBSTRATE_CRYPTO_TYPE, cryptoTypeConverters.from(substrateCryptoType))
                put(MetaAccountLocal.Table.Column.SUBSTRATE_PUBKEY, substratePublicKey)
                put(MetaAccountLocal.Table.Column.IS_SELECTED, isSelected)
                put(MetaAccountLocal.Table.Column.POSITION, position)
            }
        }

        return database.insert(MetaAccountLocal.TABLE_NAME, SQLiteDatabase.CONFLICT_REPLACE, contentValues)
    }

    private companion object {
        const val SEED_LENGTH_BYTES = 32
        const val MAX_V04_HEX_CHARS = 256
        const val MAX_DERIVATION_PATH_CHARS = 1_024
        const val MAX_LEGACY_ADDRESS_BYTES = 256
        const val MAX_LEGACY_PUBLIC_KEY_HEX_BYTES = 66
        const val MAX_LEGACY_NAME_BYTES = 1_024
        const val BOUNDED_LEGACY_POSITION = "boundedLegacyPosition"
        const val BOUNDED_LEGACY_CRYPTO_TYPE =
            "boundedLegacyCryptoType"
        val VALID_ENTROPY_LENGTHS = setOf(16, 20, 24, 28, 32)
    }
}

private object ProductionV2MigrationCryptography : V2MigrationCryptography {

    override fun decodeEthereumPath(
        path: String
    ): JunctionDecoder.DecodeResult {
        return BIP32JunctionDecoder.decode(path)
    }

    override fun deriveEthereumKeypair(
        entropy: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult
    ): FearlessKeypair {
        val mnemonic = MnemonicCreator.fromEntropy(entropy)
        val ethereumSeed = EthereumSeedFactory.deriveSeed32(
            mnemonic.words,
            password = decodedPath.password
        ).seed
        return EthereumKeypairFactory.generate(
            ethereumSeed,
            junctions = decodedPath.junctions
        )
    }
}
