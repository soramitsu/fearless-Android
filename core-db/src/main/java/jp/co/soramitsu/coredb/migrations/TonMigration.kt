package jp.co.soramitsu.coredb.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidation
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.data.storage.encrypt.quarantineEncryptedStringSnapshotDurably
import jp.co.soramitsu.common.data.storage.encrypt.replaceEncryptedStringsForStatesDurably
import jp.co.soramitsu.common.data.storage.encrypt.requireSnapshotMovesReady
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string
import kotlinx.coroutines.runBlocking

class TonMigration(
    private val encryptedPreferences: EncryptedPreferences,
    private val walletRootSecretValidation: WalletRootSecretValidation =
        WalletRootSecretValidator
) : Migration(71, 72) {

    override fun migrate(db: SupportSQLiteDatabase) = runBlocking {
        encryptedPreferences.requireDurableStorageHealthy()
        TonUpgradeSqlPreflight.requireSafeToMutate(db)
        WalletSecretMigrationPreflight.requireSafeToMutate(
            database = db,
            encryptedPreferences = encryptedPreferences,
            includeTonPublicKey = false
        )

        // Room owns the migration transaction. External secret writes are
        // committed atomically and synchronously before Room can commit version
        // 72. A failed commit is latched for this process so Room's automatic
        // retry cannot accidentally finish against non-durable preference state.
        migrateMetaAccounts(db)
        recreateChainsAndAssets(db)
        recreateTokenPrice(db)
        // The first pass prepares every wallet without touching preferences.
        // This prevents a late deterministic conflict from following durable
        // mutations made for earlier wallets outside Room's transaction.
        migrateToSeparatedSecretsStorage(db, executeActions = false)
        migrateToSeparatedSecretsStorage(db, executeActions = true)
    }

    private fun migrateMetaAccounts(db: SupportSQLiteDatabase) {
        val dependentTables = listOf(
            "chain_accounts",
            "favorite_chains",
            "nomis_wallet_score"
        )

        dependentTables.forEach { tableName ->
            db.execSQL("DROP TABLE IF EXISTS `_ton_migration_$tableName`")
            db.execSQL(
                "CREATE TEMP TABLE `_ton_migration_$tableName` AS SELECT * FROM `$tableName`"
            )
        }

        db.execSQL("DROP TABLE IF EXISTS _meta_accounts")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `_meta_accounts` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `substratePublicKey` BLOB,
            `substrateCryptoType` TEXT,
            `substrateAccountId` BLOB,
            `ethereumPublicKey` BLOB,
            `ethereumAddress` BLOB,
            `tonPublicKey` BLOB,
            `name` TEXT NOT NULL,
            `isSelected` INTEGER NOT NULL,
            `position` INTEGER NOT NULL,
            `isBackedUp` INTEGER NOT NULL,
            `googleBackupAddress` TEXT,
            `initialized` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO _meta_accounts SELECT 
            m.id,
            m.substratePublicKey,
            m.substrateCryptoType,
            m.substrateAccountId,
            m.ethereumPublicKey,
            m.ethereumAddress,
            NULL as `tonPublicKey`,
            m.name,
            m.isSelected,
            m.position,
            m.isBackedUp,
            m.googleBackupAddress,
            m.initialized
            FROM meta_accounts m
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS `meta_accounts`")
        db.execSQL("ALTER TABLE _meta_accounts RENAME TO meta_accounts")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_accounts_substrateAccountId` ON `meta_accounts` (`substrateAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_accounts_ethereumAddress` ON `meta_accounts` (`ethereumAddress`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_accounts_tonPublicKey` ON `meta_accounts` (`tonPublicKey`)")

        dependentTables.forEach { tableName ->
            db.execSQL(
                "INSERT OR REPLACE INTO `$tableName` SELECT * FROM `_ton_migration_$tableName`"
            )
            db.execSQL("DROP TABLE `_ton_migration_$tableName`")
        }
    }

    private fun recreateChainsAndAssets(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE chains ADD COLUMN `ecosystem` TEXT NOT NULL DEFAULT 'Substrate'"
        )
        db.execSQL(
            """
            UPDATE chains
            SET ecosystem = CASE
                WHEN isEthereumChain = 1 THEN 'Ethereum'
                WHEN isEthereumBased = 1 THEN 'EthereumBased'
                ELSE 'Substrate'
            END
            """.trimIndent()
        )
        db.execSQL("ALTER TABLE chains ADD COLUMN `androidMinAppVersion` TEXT NULL DEFAULT NULL")

        db.execSQL("DROP TABLE IF EXISTS `_chain_assets`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `_chain_assets` (
            `id` TEXT NOT NULL, 
            `name` TEXT, 
            `symbol` TEXT NOT NULL, 
            `chainId` TEXT NOT NULL, 
            `icon` TEXT NOT NULL, 
            `priceId` TEXT, 
            `staking` TEXT NOT NULL, 
            `precision` INTEGER NOT NULL, 
            `purchaseProviders` TEXT, 
            `isUtility` INTEGER, 
            `type` TEXT, 
            `currencyId` TEXT, 
            `existentialDeposit` TEXT, 
            `color` TEXT, 
            `isNative` INTEGER, 
            `priceProvider` TEXT, 
            PRIMARY KEY(`chainId`, `id`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `_chain_assets` SELECT
            a.id,
            a.name,
            a.symbol,
            a.chainId,
            a.icon,
            a.priceId,
            a.staking,
            a.precision,
            a.purchaseProviders,
            a.isUtility,
            a.type,
            a.currencyId,
            a.existentialDeposit,
            a.color,
            a.isNative,
            a.priceProvider
            FROM chain_assets a
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `chain_assets`")
        db.execSQL("ALTER TABLE `_chain_assets` RENAME TO `chain_assets`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`)")

        db.execSQL("DROP TABLE IF EXISTS _assets")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `_assets` (
            `id` TEXT NOT NULL, 
            `chainId` TEXT NOT NULL, 
            `accountId` BLOB NOT NULL, 
            `metaId` INTEGER NOT NULL, 
            `tokenPriceId` TEXT, 
            `freeInPlanks` TEXT, 
            `reservedInPlanks` TEXT, 
            `miscFrozenInPlanks` TEXT, 
            `feeFrozenInPlanks` TEXT, 
            `bondedInPlanks` TEXT, 
            `redeemableInPlanks` TEXT, 
            `unbondingInPlanks` TEXT, 
            `sortIndex` INTEGER NOT NULL DEFAULT 0, 
            `enabled` INTEGER DEFAULT NULL, 
            `markedNotNeed` INTEGER NOT NULL DEFAULT 0, 
            `chainAccountName` TEXT, 
            `status` TEXT NULL,
            PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO _assets SELECT 
            a.id,
            a.chainId,
            a.accountId,
            a.metaId,
            a.tokenPriceId,
            a.freeInPlanks,
            a.reservedInPlanks,
            a.miscFrozenInPlanks,
            a.feeFrozenInPlanks,
            a.bondedInPlanks,
            a.redeemableInPlanks,
            a.unbondingInPlanks,
            a.sortIndex,
            a.enabled,
            a.markedNotNeed,
            a.chainAccountName,
            a.status
            FROM assets a
            """.trimIndent()
        )

        db.execSQL("DROP TABLE IF EXISTS assets")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `assets` (
            `id` TEXT NOT NULL, 
            `chainId` TEXT NOT NULL, 
            `accountId` BLOB NOT NULL, 
            `metaId` INTEGER NOT NULL, 
            `tokenPriceId` TEXT, 
            `freeInPlanks` TEXT, 
            `reservedInPlanks` TEXT, 
            `miscFrozenInPlanks` TEXT, 
            `feeFrozenInPlanks` TEXT, 
            `bondedInPlanks` TEXT, 
            `redeemableInPlanks` TEXT, 
            `unbondingInPlanks` TEXT, 
            `sortIndex` INTEGER NOT NULL DEFAULT 0, 
            `enabled` INTEGER DEFAULT NULL, 
            `markedNotNeed` INTEGER NOT NULL DEFAULT 0, 
            `chainAccountName` TEXT,
            `status` TEXT NULL,
            PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION 
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO assets SELECT 
            a.id,
            a.chainId,
            a.accountId,
            a.metaId,
            a.tokenPriceId,
            a.freeInPlanks,
            a.reservedInPlanks,
            a.miscFrozenInPlanks,
            a.feeFrozenInPlanks,
            a.bondedInPlanks,
            a.redeemableInPlanks,
            a.unbondingInPlanks,
            a.sortIndex,
            a.enabled,
            a.markedNotNeed,
            a.chainAccountName,
            a.status
            FROM _assets a
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS _assets")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_chainId` ON `assets` (`chainId`)")
    }

    private fun recreateTokenPrice(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `token_price`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `token_price` (
            `priceId` TEXT NOT NULL, 
            `fiatSymbol` TEXT NOT NULL, 
            `fiatRate` TEXT, 
            `recentRateChange` TEXT, 
            PRIMARY KEY(`priceId`)
            )
            """.trimIndent()
        )
    }

    private fun migrateToSeparatedSecretsStorage(
        database: SupportSQLiteDatabase,
        executeActions: Boolean
    ) {
        forEachBoundedWalletPublicIdentity(
            database = database,
            includeTonPublicKey = true
        ) wallet@{ account ->
            val legacySecretKey = "${account.metaId}:ACCESS_SECRETS"
            if (!encryptedPreferences.hasKey(legacySecretKey)) return@wallet
            if (
                WalletSecretMigrationPreflight.hasValidPublicIdentityRecovery(
                    encryptedPreferences = encryptedPreferences,
                    metaId = account.metaId,
                    activeSecretKey = legacySecretKey
                )
            ) {
                return@wallet
            }

            val preparedReplacement = prepareSeparatedSecretsOrQuarantine(
                account = account,
                legacySecretKey = legacySecretKey,
                executeActions = executeActions
            ) ?: return@wallet

            // A CAS rejection leaves the active secret untouched. If a
            // completed durable commit outlives Room's SQL rollback, its exact
            // split destinations remain the idempotent retry state.
            if (executeActions) {
                encryptedPreferences.replaceEncryptedStringsForStatesDurably(
                    expectedStates = buildMap {
                        put(
                            legacySecretKey,
                            preparedReplacement.legacySnapshot
                        )
                        putAll(preparedReplacement.destinationStates)
                    },
                    valuesToPut =
                        preparedReplacement.absentDestinationValues,
                    keysToRemove = setOf(legacySecretKey)
                )
            }
        }
    }

    private fun prepareSeparatedSecretsOrQuarantine(
        account: WalletPublicIdentity,
        legacySecretKey: String,
        executeActions: Boolean
    ): PreparedSeparatedSecretReplacement? {
        if (
            account.hasPartialSubstrateIdentity ||
            account.hasPartialEthereumIdentity
        ) {
            if (executeActions) {
                recordPublicIdentityRecovery(
                    metaId = account.metaId,
                    activeSecretKey = legacySecretKey
                )
            }
            return null
        }
        val snapshot = checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(legacySecretKey)
        ) {
            "Legacy wallet secret disappeared during migration"
        }
        val encodedOldSecrets = snapshot.plaintext
        val replacementSecrets: Map<String, String>? = try {
            if (encodedOldSecrets.isEmpty()) {
                throw WalletSecretIntegrityCorruptionException(
                    "Unable to decode an empty legacy wallet secret during migration"
                )
            }
            val replacement =
                WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                    encoded = encodedOldSecrets,
                    identity = account,
                    walletRootSecretValidation = walletRootSecretValidation
                )
            buildMap<String, String> {
                put(
                    "${account.metaId}:SUBSTRATE_SECRETS",
                    replacement.substratePlaintext
                )
                replacement.ethereumPlaintext?.let {
                    put(
                        "${account.metaId}:ETHEREUM_SECRETS",
                        it
                    )
                }
            }
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: WalletPublicIdentityIntegrityException) {
            if (executeActions) {
                recordPublicIdentityRecovery(
                    metaId = account.metaId,
                    activeSecretKey = legacySecretKey
                )
            }
            return null
        } catch (failure: WalletSecretIntegrityCorruptionException) {
            null
        }

        if (replacementSecrets == null) {
            val snapshotMoves = listOf(
                EncryptedPreferenceSnapshotMove(
                    sourceKey = legacySecretKey,
                    destinationKey =
                        WalletSecretQuarantine.keyFor(legacySecretKey),
                    expectedSnapshot = snapshot
                )
            )
            if (executeActions) {
                encryptedPreferences.quarantineEncryptedStringSnapshotDurably(
                    sourceKey = legacySecretKey,
                    quarantineKey =
                        WalletSecretQuarantine.keyFor(legacySecretKey),
                    expectedSnapshot = snapshot
                )
            } else {
                encryptedPreferences.requireSnapshotMovesReady(snapshotMoves)
            }
        }

        return replacementSecrets?.let {
            prepareDestinationStates(
                legacySnapshot = snapshot,
                replacementSecrets = it
            )
        }
    }

    private fun prepareDestinationStates(
        legacySnapshot: EncryptedPreferenceSnapshot,
        replacementSecrets: Map<String, String>
    ): PreparedSeparatedSecretReplacement {
        val destinationStates =
            linkedMapOf<String, EncryptedPreferenceSnapshot?>()
        val absentDestinationValues = linkedMapOf<String, String>()
        replacementSecrets.forEach { (destinationKey, replacementValue) ->
            val destinationSnapshot = if (
                encryptedPreferences.hasKey(destinationKey)
            ) {
                checkNotNull(
                    encryptedPreferences.getDecryptedStringSnapshot(
                        destinationKey
                    )
                ) {
                    "A TON migration target disappeared during preparation"
                }
            } else {
                null
            }
            if (
                destinationSnapshot != null &&
                destinationSnapshot.plaintext != replacementValue
            ) {
                throw WalletSecretConcurrentMutationException(
                    "A TON migration target contains conflicting wallet material"
                )
            }
            destinationStates[destinationKey] = destinationSnapshot
            if (destinationSnapshot == null) {
                absentDestinationValues[destinationKey] = replacementValue
            }
        }
        return PreparedSeparatedSecretReplacement(
            legacySnapshot = legacySnapshot,
            destinationStates = destinationStates,
            absentDestinationValues = absentDestinationValues
        )
    }

    private fun recordPublicIdentityRecovery(
        metaId: Long,
        activeSecretKey: String
    ) {
        val markerKey = WalletPublicIdentityRecovery.keyFor(
            metaId = metaId,
            activeSecretKey = activeSecretKey
        )
        if (
            WalletSecretMigrationPreflight.hasValidPublicIdentityRecovery(
                encryptedPreferences = encryptedPreferences,
                metaId = metaId,
                activeSecretKey = activeSecretKey
            )
        ) {
            return
        }

        val expectedMarkerState:
            Map<String, EncryptedPreferenceSnapshot?> =
            mapOf(markerKey to null)
        encryptedPreferences.replaceEncryptedStringsForStatesDurably(
            expectedStates = expectedMarkerState,
            valuesToPut = mapOf(
                markerKey to WalletPublicIdentityRecovery.MARKER_VALUE
            ),
            keysToRemove = emptySet()
        )
    }

    private data class PreparedSeparatedSecretReplacement(
        val legacySnapshot: EncryptedPreferenceSnapshot,
        val destinationStates:
        Map<String, EncryptedPreferenceSnapshot?>,
        val absentDestinationValues: Map<String, String>
    )

    object MetaAccountSecretsV69 : Schema<MetaAccountSecretsV69>() {
        val Entropy by byteArray().optional()
        val Seed by byteArray().optional()

        val SubstrateKeypair by schema(KeyPairSchema)
        val SubstrateDerivationPath by string().optional()

        val EthereumKeypair by schema(KeyPairSchema).optional()
        val EthereumDerivationPath by string().optional()
    }
}
