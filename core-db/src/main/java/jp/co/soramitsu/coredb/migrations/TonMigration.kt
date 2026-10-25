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
        TonUpgradeSqlPreparation.prepare(db)
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
        TonSchemaMigration.migrate(db)
        CurrentEndpointUpgradePreflight.requireSafe(db, fromVersion = 72)
        // The first pass prepares every wallet without touching preferences.
        // This prevents a late deterministic conflict from following durable
        // mutations made for earlier wallets outside Room's transaction.
        migrateToSeparatedSecretsStorage(db, executeActions = false)
        migrateToSeparatedSecretsStorage(db, executeActions = true)
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

            val plan = prepareSeparatedSecretsOrQuarantine(
                account = account,
                legacySecretKey = legacySecretKey,
                executeActions = executeActions
            ) ?: return@wallet

            // A CAS rejection leaves the legacy secret untouched. If a
            // completed durable commit outlives Room's SQL rollback, the
            // legacy payload has left ACCESS_SECRETS and the retry skips it.
            if (executeActions) {
                plan.commit(encryptedPreferences)
            } else {
                plan.requireReady(encryptedPreferences)
            }
        }
    }

    private fun prepareSeparatedSecretsOrQuarantine(
        account: WalletPublicIdentity,
        legacySecretKey: String,
        executeActions: Boolean
    ): LegacySecretConversionPlan? {
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
        val replacement = try {
            if (encodedOldSecrets.isEmpty()) {
                throw WalletSecretIntegrityCorruptionException(
                    "Unable to decode an empty legacy wallet secret during migration"
                )
            }
            WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                encoded = encodedOldSecrets,
                identity = account,
                walletRootSecretValidation = walletRootSecretValidation
            )
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

        if (replacement == null) {
            // The payload itself cannot be decoded, so it serves no
            // ecosystem: keep the exact ciphertext in the wallet's quarantine.
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
            return null
        }

        return encryptedPreferences.planLegacySecretConversion(
            identity = account,
            legacySourceKey = legacySecretKey,
            legacySnapshot = snapshot,
            replacement = replacement,
            walletRootSecretValidation = walletRootSecretValidation
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

    object MetaAccountSecretsV69 : Schema<MetaAccountSecretsV69>() {
        val Entropy by byteArray().optional()
        val Seed by byteArray().optional()

        val SubstrateKeypair by schema(KeyPairSchema)
        val SubstrateDerivationPath by string().optional()

        val EthereumKeypair by schema(KeyPairSchema).optional()
        val EthereumDerivationPath by string().optional()
    }
}
