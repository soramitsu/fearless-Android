package jp.co.soramitsu.coredb.migrations

import android.annotation.SuppressLint
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets.EthereumDerivationPath
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.shared_utils.scale.Schema
import jp.co.soramitsu.shared_utils.scale.byteArray
import jp.co.soramitsu.shared_utils.scale.schema
import jp.co.soramitsu.shared_utils.scale.string
import kotlinx.coroutines.runBlocking

class TonMigration(
    private val storeV2: SecretStoreV2,
    private val substrateSecretStore: SubstrateSecretStore,
    private val ethereumSecretStore: EthereumSecretStore,
    private val encryptedPreferences: EncryptedPreferences
) : Migration(71, 72) {

    override fun migrate(db: SupportSQLiteDatabase) {
        runBlocking {
            db.beginTransaction()
            // 1. New fields in chain (ecosystem, androidMinAppVersion) and remove old ethereumType from chain_asset
            // 2. New field in meta_accounts (tonPublicKey)
            // 3. Change cascade delete of assets to no_action to avoid clearing user settings (enable/disable assets)
            // 4. Change TokenPriceLocal configuration
            db.execSQL("ALTER TABLE meta_accounts ADD COLUMN `tonPublicKey` BLOB DEFAULT NULL")

            recreateChainsAndAssets(db)
            recreateTokenPrice(db)
            val metaIds = getAccounts(db)
            migrateToSeparatedSecretsStorage(metaIds)

            db.setTransactionSuccessful()
            db.endTransaction()
        }
    }

    private fun recreateChainsAndAssets(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `chains`")
        db.execSQL("DROP TABLE IF EXISTS `chain_assets`")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chains` (
            `id` TEXT NOT NULL,
            `paraId` TEXT NULL,
            `parentId` TEXT,
            `rank` INTEGER NULL,
            `name` TEXT NOT NULL,
            `minSupportedVersion` TEXT,
            `icon` TEXT NOT NULL,
            `prefix` INTEGER NOT NULL,
            `isEthereumBased` INTEGER NOT NULL,
            `isTestNet` INTEGER NOT NULL,
            `hasCrowdloans` INTEGER NOT NULL,
            `supportStakingPool` INTEGER NOT NULL,
            `isEthereumChain` INTEGER NOT NULL DEFAULT 0,
            `isChainlinkProvider` INTEGER NOT NULL DEFAULT 0,
            `supportNft` INTEGER NOT NULL DEFAULT 0,
            `isUsesAppId` INTEGER NOT NULL DEFAULT 0,
            `identityChain` TEXT NULL DEFAULT NULL,
            `ecosystem` TEXT NOT NULL,
            `androidMinAppVersion` TEXT NULL DEFAULT NULL,
            `staking_url` TEXT,
            `staking_type` TEXT,
            `history_url` TEXT,
            `history_type` TEXT,
            `crowdloans_url` TEXT,
            `crowdloans_type` TEXT,
            `remoteAssetsSource` TEXT NULL DEFAULT NULL,
            
            PRIMARY KEY(`id`))
            """.trimIndent()
        )

        db.execSQL("DROP TABLE IF EXISTS chain_assets")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_assets` (
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`)")

        // balances

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
        db.execSQL("DROP TABLE IF EXISTS `chains`")
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

    private suspend fun migrateToSeparatedSecretsStorage(metaIds: List<Long>) {
        metaIds.forEach { metaId ->
            val oldSecrets = encryptedPreferences.getDecryptedString("$metaId:ACCESS_SECRETS")
                ?.let(MetaAccountSecretsV69::read) ?: return@forEach

            val entropy = oldSecrets[MetaAccountSecretsV69.Entropy] ?: return@forEach

            val substrateKeypair =
                oldSecrets[MetaAccountSecretsV69.SubstrateKeypair].let { struct ->
                    Keypair(
                        publicKey = struct[KeyPairSchema.PublicKey],
                        privateKey = struct[KeyPairSchema.PrivateKey],
                        nonce = struct[KeyPairSchema.Nonce]
                    )
                }
            val seed = oldSecrets[MetaAccountSecretsV69.Seed]
            val substrateDerivationPath = oldSecrets[MetaAccountSecretsV69.SubstrateDerivationPath]
            val ethereumKeypair = oldSecrets[MetaAccountSecretsV69.EthereumKeypair]?.let { struct ->
                Keypair(
                    publicKey = struct[KeyPairSchema.PublicKey],
                    privateKey = struct[KeyPairSchema.PrivateKey],
                    nonce = struct[KeyPairSchema.Nonce]
                )
            }

            val newSubstrateSecrets = SubstrateSecrets(
                substrateKeyPair = substrateKeypair,
                entropy = entropy,
                seed = seed,
                substrateDerivationPath = substrateDerivationPath
            )
            substrateSecretStore.put(metaId, newSubstrateSecrets)
            if (ethereumKeypair != null) {
                val newEthereumSecrets = EthereumSecrets(
                    entropy = entropy,
                    seed = ethereumKeypair.privateKey,
                    ethereumKeypair = ethereumKeypair,
                    ethereumDerivationPath = oldSecrets[EthereumDerivationPath]
                )
                ethereumSecretStore.put(metaId, newEthereumSecrets)
            }
            storeV2.clearSecrets(metaId, listOf())
        }
    }

    @SuppressLint("Range")
    private fun getAccounts(database: SupportSQLiteDatabase): List<Long> {
        return database.query("SELECT id FROM meta_accounts").let {
            it.map {
                getLong(getColumnIndex(MetaAccountLocal.Table.Column.ID))
            }
        }
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