package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV374DatabaseFixture
import jp.co.soramitsu.coredb.migrations.fixtures.ShippedVersion4MigrationSql
import jp.co.soramitsu.coredb.migrations.fixtures.ShippedVersion4MigrationSql.RELEASE_4_0_1
import jp.co.soramitsu.coredb.migrations.fixtures.ShippedVersion4MigrationSql.RELEASE_4_1_1
import jp.co.soramitsu.coredb.migrations.fixtures.ShippedVersion4MigrationSql.RELEASE_4_2_0
import jp.co.soramitsu.coredb.migrations.shipped.ShippedSecretWriters
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Blocking upgrade test for every user who installed 3.x and updated in place
 * to a 4.x release (addendum K10, audit schema-allowlist-1/-2 and
 * foreign-key-preflight-1).
 *
 * The starting database is the frozen 3.7.4 schema, filled the way 3.7.4 ran
 * (foreign keys on), then upgraded by the SQL the 4.x releases actually ran
 * (frozen copy, not the release candidate's migrations) with the secrets
 * those releases moved (verbatim writer replicas and golden vectors). The
 * chain registry then re-syncs the way 4.x did: Parallel and InvArch Tinker
 * are no longer in it, so their nodes, explorers, asset settings, favourite
 * and per-chain account are left pointing at no chain.
 *
 * Before K10 the release candidate stopped every one of these users at
 * "Wallet recovery required" on every launch: the 76 -> 77 preflight rejected
 * the chains layout, and then the dangling rows. The production builder must
 * reach the current version on the first launch and keep every wallet row,
 * per-chain account, user setting and secret.
 */
@RunWith(Parameterized::class)
class ShippedVersion4UpgradeReplayTest(private val cohort: Cohort) {

    private class AssetSetting(
        val metaId: Long,
        val chainId: String,
        val free: String?,
        val sortIndex: Int,
        val enabled: Int?
    )

    data class Cohort(val label: String, val releasesByEdge: List<String>) {
        val shippedVersion: Int get() = 71 + releasesByEdge.size

        override fun toString(): String = label
    }

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "shipped-4x-replay-${cohort.releasesByEdge.joinToString("-")}"

    @After
    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun shippedUpgradeWithDanglingRegistryRowsOpensWithEveryWalletAndSecret() {
        val preferences = HashMapEncryptedPreferences()
        val wallets = ReleasedWallets(context)
        ReleasedV374DatabaseFixture.install(context, databaseName)

        withDatabase(version = 71) { database ->
            database.insertVersion374Rows(wallets)
        }
        wallets.storeVersion374Secrets(preferences)

        withDatabase(version = 71) { database ->
            ShippedVersion4MigrationSql.replay(
                context = context,
                database = database,
                releasesByEdge = cohort.releasesByEdge
            ) { from, _ ->
                if (from == 71) {
                    ShippedSecretWriters.separateSecretsTon420(
                        preferences,
                        listOf(OLD_MNEMONIC_META_ID, OLD_RAW_SEED_META_ID)
                    )
                }
            }
        }
        val shippedSecrets = wallets.secretKeys.associateWith(preferences::getDecryptedString)
        assertNull(shippedSecrets.getValue("$OLD_MNEMONIC_META_ID:ACCESS_SECRETS"))

        withDatabase(version = cohort.shippedVersion) { database ->
            database.syncRegistryAsVersion4(cohort.shippedVersion)
            database.insertVersion4TonWallet(wallets)
            assertEquals(SHIPPED_CHAIN_COLUMNS, database.columnNames("chains"))
            assertEquals(DANGLING_BEFORE_UPGRADE, database.foreignKeyViolations())
        }
        wallets.storeVersion4Secrets(preferences)
        val rowsBefore = withDatabase(version = cohort.shippedVersion) { it.userRows() }

        repeat(2) { launch ->
            assertEquals(
                "launch ${launch + 1} of ${cohort.label}",
                APP_DATABASE_VERSION,
                openProductionDatabase(preferences)
            )
        }

        withDatabase(version = APP_DATABASE_VERSION) { database ->
            assertEquals(rowsBefore, database.userRows())
            assertEquals(SHIPPED_CHAIN_COLUMNS + "xcm", database.columnNames("chains"))
            assertEquals(listOf(POLKADOT), database.strings("SELECT id FROM chains"))
            assertEquals(
                listOf(POLKADOT),
                database.strings("SELECT DISTINCT chainId FROM chain_nodes")
            )
            assertEquals(
                listOf(POLKADOT),
                database.strings("SELECT DISTINCT chainId FROM chain_explorers")
            )
            assertEquals(TOLERATED_AFTER_UPGRADE, database.foreignKeyViolations())
        }

        assertFalse(preferences.hasKeyWithPrefix(WalletSecretQuarantine.KEY_PREFIX))
        assertFalse(preferences.hasKeyWithPrefix(WalletPublicIdentityRecovery.KEY_PREFIX))
        wallets.assertSecretsKept(preferences, shippedSecrets)
    }

    private fun openProductionDatabase(preferences: EncryptedPreferences): Int {
        var database: AppDatabase? = null
        try {
            database = AppDatabase.create(
                context = context,
                databaseName = databaseName,
                storeV1 = SecretStoreV1Impl(preferences),
                storeV2 = SecretStoreV2(preferences),
                encryptedPreferences = preferences,
                substrateSecretStore = SubstrateSecretStore(preferences),
                ethereumSecretStore = EthereumSecretStore(preferences)
            )
            return database.openHelper.writableDatabase.version
        } finally {
            database?.close()
        }
    }

    private fun <T> withDatabase(version: Int, action: (SupportSQLiteDatabase) -> T): T {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("The replayed database was not installed")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("Unexpected upgrade from $oldVersion to $newVersion")
            }

            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("Unexpected downgrade from $oldVersion to $newVersion")
            }
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
        return try {
            val database = helper.writableDatabase
            database.setForeignKeyConstraintsEnabled(true)
            action(database)
        } finally {
            helper.close()
        }
    }

    /** What 3.7.4 kept for two wallets on Polkadot and two networks 4.x dropped. */
    private fun SupportSQLiteDatabase.insertVersion374Rows(wallets: ReleasedWallets) {
        listOf(POLKADOT to "Polkadot", PARALLEL to "Parallel", TINKER to "InvArch Tinker Network")
            .forEachIndexed { rank, (chainId, name) ->
                execSQL(
                    """
                    INSERT INTO chains(
                        id, rank, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans,
                        supportStakingPool, isEthereumChain, isChainlinkProvider, supportNft,
                        isUsesAppId, history_url, history_type
                    ) VALUES(?, ?, ?, ?, 0, 0, 0, 1, 0, 0, 0, 0, 0, ?, 'subquery')
                    """.trimIndent(),
                    arrayOf<Any>(chainId, rank, name, "https://icons.invalid/$name.svg", "https://history.invalid/$name")
                )
                execSQL(
                    """
                    INSERT INTO chain_assets(id, name, symbol, chainId, icon, priceId, staking, precision, isUtility)
                    VALUES('native', ?, ?, ?, 'icon', NULL, 'RELAYCHAIN', 10, 1)
                    """.trimIndent(),
                    arrayOf<Any>(name, name.take(4).uppercase(), chainId)
                )
            }
        listOf(
            arrayOf<Any>(POLKADOT, "wss://rpc.polkadot.invalid", "Polkadot node", 1, 1),
            arrayOf<Any>(PARALLEL, "wss://rpc.parallel.invalid", "Parallel node", 1, 1),
            arrayOf<Any>(PARALLEL, "wss://parallel-custom.invalid", "My Parallel node", 0, 0),
            arrayOf<Any>(TINKER, "wss://rpc.tinker.invalid", "Tinker node", 1, 1)
        ).forEach { node ->
            execSQL("INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault) VALUES(?, ?, ?, ?, ?)", node)
        }
        listOf(POLKADOT, PARALLEL).forEach { chainId ->
            execSQL(
                "INSERT INTO chain_explorers(chainId, type, types, url) VALUES(?, 'subscan', 'extrinsic,account', ?)",
                arrayOf<Any>(chainId, "https://$chainId.subscan.invalid")
            )
        }

        wallets.version374.forEach { wallet ->
            execSQL(
                """
                INSERT INTO meta_accounts(
                    id, substratePublicKey, substrateCryptoType, substrateAccountId, ethereumPublicKey,
                    ethereumAddress, name, isSelected, position, isBackedUp, googleBackupAddress, initialized
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, 1, NULL, 1)
                """.trimIndent(),
                arrayOf<Any?>(
                    wallet.metaId,
                    wallet.substratePublicKey,
                    wallet.substrateCryptoType.name,
                    wallet.substrateAccountId,
                    wallet.ethereumPublicKey,
                    wallet.ethereumAddress,
                    "Wallet ${wallet.metaId} ' \" 🔐",
                    if (wallet.metaId == OLD_MNEMONIC_META_ID) 1 else 0,
                    wallet.metaId - 1
                )
            )
        }
        listOf(
            AssetSetting(OLD_MNEMONIC_META_ID, POLKADOT, free = "100", sortIndex = 0, enabled = 1),
            // A deliberate hide of a funded asset on a network 4.x dropped.
            AssetSetting(OLD_MNEMONIC_META_ID, PARALLEL, free = "5", sortIndex = 3, enabled = 0),
            AssetSetting(OLD_MNEMONIC_META_ID, TINKER, free = "7", sortIndex = 4, enabled = 1),
            AssetSetting(OLD_RAW_SEED_META_ID, POLKADOT, free = null, sortIndex = 1, enabled = null)
        ).forEach { setting ->
            execSQL(
                """
                INSERT INTO assets(
                    id, chainId, accountId, metaId, freeInPlanks, sortIndex, enabled, markedNotNeed
                ) VALUES('native', ?, ?, ?, ?, ?, ?, 0)
                """.trimIndent(),
                arrayOf(
                    setting.chainId,
                    wallets.version374.single { it.metaId == setting.metaId }.substrateAccountId,
                    setting.metaId,
                    setting.free,
                    setting.sortIndex,
                    setting.enabled
                )
            )
        }
        execSQL("INSERT INTO favorite_chains(metaId, chainId, isFavorite) VALUES(?, ?, 1)", arrayOf<Any>(OLD_MNEMONIC_META_ID, PARALLEL))
        execSQL("INSERT INTO favorite_chains(metaId, chainId, isFavorite) VALUES(?, ?, 1)", arrayOf<Any>(OLD_MNEMONIC_META_ID, POLKADOT))
        wallets.chainAccounts.forEach { account ->
            execSQL(
                """
                INSERT INTO chain_accounts(metaId, chainId, publicKey, accountId, cryptoType, name, initialized)
                VALUES(?, ?, ?, ?, ?, ?, 1)
                """.trimIndent(),
                arrayOf<Any>(
                    account.metaId,
                    account.chainId,
                    account.publicKey,
                    account.accountId,
                    account.cryptoType,
                    "Account on ${account.chainId.take(8)}"
                )
            )
        }
        assertEquals(emptyList<List<String>>(), foreignKeyViolations())
    }

    /** 4.x chain sync: Polkadot comes back, the two dropped networks do not. */
    private fun SupportSQLiteDatabase.syncRegistryAsVersion4(version: Int) {
        execSQL(
            """
            INSERT INTO chains(
                id, rank, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans,
                supportStakingPool, ecosystem, history_url, history_type
            ) VALUES(?, 0, 'Polkadot', 'https://icons.invalid/Polkadot.svg', 0, 0, 0, 1, 0, 'Substrate', ?, 'subquery')
            """.trimIndent(),
            arrayOf<Any>(POLKADOT, "https://history.invalid/Polkadot")
        )
        val coinbase = if (version >= 74) (", coinbaseUrl" to ", NULL") else ("" to "")
        execSQL(
            """
            INSERT INTO chain_assets(id, name, symbol, chainId, icon, staking, precision, isUtility${coinbase.first})
            VALUES('native', 'Polkadot', 'DOT', ?, 'icon', 'RELAYCHAIN', 10, 1${coinbase.second})
            """.trimIndent(),
            arrayOf<Any>(POLKADOT)
        )
    }

    private fun SupportSQLiteDatabase.insertVersion4TonWallet(wallets: ReleasedWallets) {
        execSQL(
            """
            INSERT INTO meta_accounts(
                id, tonPublicKey, name, isSelected, position, isBackedUp, initialized
            ) VALUES(?, ?, 'TON wallet created on 4.x', 0, 2, 1, 1)
            """.trimIndent(),
            arrayOf<Any>(VERSION_4_TON_META_ID, wallets.ton.publicKey)
        )
    }

    /** Every row a user owns, by column name, blobs as hex. */
    private fun SupportSQLiteDatabase.userRows(): Map<String, List<Map<String, String?>>> {
        return mapOf(
            "meta_accounts" to "SELECT id, substratePublicKey, substrateCryptoType, substrateAccountId, " +
                "ethereumPublicKey, ethereumAddress, tonPublicKey, name, isSelected, position, isBackedUp, " +
                "googleBackupAddress, initialized FROM meta_accounts ORDER BY id",
            "chain_accounts" to "SELECT metaId, chainId, publicKey, accountId, cryptoType, name, initialized " +
                "FROM chain_accounts ORDER BY metaId, chainId",
            "assets" to "SELECT id, chainId, accountId, metaId, freeInPlanks, sortIndex, enabled, markedNotNeed " +
                "FROM assets ORDER BY metaId, chainId, id",
            "favorite_chains" to "SELECT metaId, chainId, isFavorite FROM favorite_chains ORDER BY metaId, chainId"
        ).mapValues { (_, sql) ->
            query(sql).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            cursor.columnNames.indices.associate { index ->
                                cursor.getColumnName(index) to when {
                                    cursor.isNull(index) -> null
                                    cursor.getType(index) == android.database.Cursor.FIELD_TYPE_BLOB ->
                                        cursor.getBlob(index).toHexString()
                                    else -> cursor.getString(index)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun SupportSQLiteDatabase.columnNames(table: String): List<String> =
        strings("SELECT name FROM pragma_table_info('$table') ORDER BY cid")

    private fun SupportSQLiteDatabase.strings(sql: String): List<String> =
        query(sql).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun SupportSQLiteDatabase.foreignKeyViolations(): List<List<String>> =
        query("PRAGMA foreign_key_check").use { cursor ->
            val childColumn = cursor.getColumnIndexOrThrow("table")
            val parentColumn = cursor.getColumnIndexOrThrow("parent")
            buildList {
                while (cursor.moveToNext()) {
                    add(listOf(cursor.getString(childColumn), cursor.getString(parentColumn)))
                }
            }.sortedWith(compareBy({ it[0] }, { it[1] }))
        }

    /**
     * Two wallets written by 3.7.4 (a mnemonic SR25519 wallet with an
     * Ethereum key, the frozen v69 payload; a raw-seed ED25519 import with
     * the Ethereum step declined, 3.7.4's own writer), their per-chain
     * accounts and a TON wallet created on 4.x (production-parity golden
     * vectors as released builds stored them).
     */
    private class ReleasedWallets(context: Context) {

        class LegacyWallet(
            val metaId: Long,
            val accessSecretsHex: String,
            val substratePublicKey: ByteArray,
            val substrateCryptoType: CryptoType,
            val substrateAccountId: ByteArray,
            val ethereumPublicKey: ByteArray?,
            val ethereumAddress: ByteArray?,
            val substratePrivateKey: ByteArray,
            val ethereumPrivateKey: ByteArray?
        )

        class StoredAccount(
            val metaId: Long,
            val chainId: String,
            val publicKey: ByteArray,
            val accountId: ByteArray,
            val cryptoType: String,
            val stored: String
        ) {
            val secretKey = "$metaId:${accountId.toHexString()}:ACCESS_SECRETS"
        }

        val version374: List<LegacyWallet>
        val chainAccounts: List<StoredAccount>
        val ton: StoredAccount

        init {
            val mnemonicHex = ReleasedV374DatabaseFixture.legacySecretHex(context)
            val mnemonic = ShippedSecretWriters.MetaAccountSecrets.read(mnemonicHex)
            val substrate = mnemonic[ShippedSecretWriters.MetaAccountSecrets.SubstrateKeypair]
            val ethereum = checkNotNull(mnemonic[ShippedSecretWriters.MetaAccountSecrets.EthereumKeypair])
            val ethereumPublicKey = ethereum[ShippedSecretWriters.KeyPairSchema.PublicKey]
            val rawSeed = ShippedSecretWriters.importFromSeed374(
                seed = RAW_SEED_HEX,
                derivationPath = "",
                selectedEncryptionType = CryptoType.ED25519,
                ethSeed = null
            )
            version374 = listOf(
                LegacyWallet(
                    metaId = OLD_MNEMONIC_META_ID,
                    accessSecretsHex = mnemonicHex,
                    substratePublicKey = substrate[ShippedSecretWriters.KeyPairSchema.PublicKey],
                    substrateCryptoType = CryptoType.SR25519,
                    substrateAccountId = substrate[ShippedSecretWriters.KeyPairSchema.PublicKey].substrateAccountId(),
                    ethereumPublicKey = ethereumPublicKey,
                    ethereumAddress = ethereumPublicKey.ethereumAddressFromPublicKey(),
                    substratePrivateKey = substrate[ShippedSecretWriters.KeyPairSchema.PrivateKey],
                    ethereumPrivateKey = ethereum[ShippedSecretWriters.KeyPairSchema.PrivateKey]
                ),
                LegacyWallet(
                    metaId = OLD_RAW_SEED_META_ID,
                    accessSecretsHex = rawSeed.accessSecretsHex,
                    substratePublicKey = rawSeed.substratePublicKey,
                    substrateCryptoType = rawSeed.substrateCryptoType,
                    substrateAccountId = rawSeed.substrateAccountId,
                    ethereumPublicKey = null,
                    ethereumAddress = null,
                    substratePrivateKey = rawSeed.substrateKeypair.privateKey,
                    ethereumPrivateKey = null
                )
            )

            val vectors = InstrumentationRegistry.getInstrumentation().context.assets
                .open("12-validator-acceptance.json")
                .use { stream -> InputStreamReader(stream, Charsets.UTF_8).use(JsonParser::parseReader) }
                .asJsonObject
                .getAsJsonArray("vectors")
                .map { it.asJsonObject }
                .associateBy { it["case"].asString }

            fun account(case: String, metaId: Long, chainId: String): StoredAccount {
                val vector: JsonObject = vectors.getValue(case)
                check(vector.getAsJsonObject("rc")["verdict"].asString == "accept")
                return StoredAccount(
                    metaId = metaId,
                    chainId = chainId,
                    publicKey = vector["publicKey"].asString.fromHex(),
                    accountId = (vector["accountId"]?.takeUnless { it.isJsonNull } ?: vector["publicKey"])
                        .asString
                        .fromHex(),
                    cryptoType = vector["cryptoType"]?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                    stored = vector["stored"].asString
                )
            }
            chainAccounts = listOf(
                account("chain-mnemonic:SR25519://polkadot#0", OLD_MNEMONIC_META_ID, PARALLEL),
                account("chain-seed:ED25519:#0", OLD_RAW_SEED_META_ID, POLKADOT)
            )
            ton = account("ton:ton-native:canonical#0", VERSION_4_TON_META_ID, "")
        }

        val secretKeys: List<String> = listOf(
            "$OLD_MNEMONIC_META_ID:ACCESS_SECRETS",
            "$OLD_MNEMONIC_META_ID:SUBSTRATE_SECRETS",
            "$OLD_MNEMONIC_META_ID:ETHEREUM_SECRETS",
            "$OLD_RAW_SEED_META_ID:ACCESS_SECRETS"
        ) + chainAccounts.map { it.secretKey }

        fun storeVersion374Secrets(preferences: EncryptedPreferences) {
            version374.forEach { preferences.putEncryptedString("${it.metaId}:ACCESS_SECRETS", it.accessSecretsHex) }
            chainAccounts.forEach { preferences.putEncryptedString(it.secretKey, it.stored) }
        }

        fun storeVersion4Secrets(preferences: EncryptedPreferences) {
            preferences.putEncryptedString("$VERSION_4_TON_META_ID:TON_SECRETS", ton.stored)
        }

        fun assertSecretsKept(preferences: EncryptedPreferences, shipped: Map<String, String?>) {
            val mnemonic = version374.single { it.metaId == OLD_MNEMONIC_META_ID }
            val substrate = SubstrateSecrets.read(
                keptOrArchived(preferences, "$OLD_MNEMONIC_META_ID:SUBSTRATE_SECRETS", shipped)
            )[SubstrateSecrets.SubstrateKeypair]
            assertArrayEquals(mnemonic.substratePrivateKey, substrate[KeyPairSchema.PrivateKey])
            assertArrayEquals(mnemonic.substratePublicKey, substrate[KeyPairSchema.PublicKey])
            val ethereum = EthereumSecrets.read(
                keptOrArchived(preferences, "$OLD_MNEMONIC_META_ID:ETHEREUM_SECRETS", shipped)
            )[EthereumSecrets.EthereumKeypair]
            assertArrayEquals(mnemonic.ethereumPrivateKey, ethereum[KeyPairSchema.PrivateKey])
            assertArrayEquals(mnemonic.ethereumPublicKey, ethereum[KeyPairSchema.PublicKey])

            // 4.x never converted a payload without entropy; the 76 -> 77 pass
            // does, and keeps the exact legacy payload in the archive (K2).
            val rawSeed = version374.single { it.metaId == OLD_RAW_SEED_META_ID }
            val legacyKey = "$OLD_RAW_SEED_META_ID:ACCESS_SECRETS"
            assertEquals(
                rawSeed.accessSecretsHex,
                preferences.getDecryptedString(legacyKey)
                    ?: preferences.getDecryptedString(WalletSecretArchive.keyFor(legacyKey))
            )
            val converted = preferences.getDecryptedString("$OLD_RAW_SEED_META_ID:SUBSTRATE_SECRETS")
            if (converted != null) {
                val keypair = SubstrateSecrets.read(converted)[SubstrateSecrets.SubstrateKeypair]
                assertArrayEquals(rawSeed.substratePrivateKey, keypair[KeyPairSchema.PrivateKey])
                assertArrayEquals(rawSeed.substratePublicKey, keypair[KeyPairSchema.PublicKey])
            } else {
                assertEquals(rawSeed.accessSecretsHex, preferences.getDecryptedString(legacyKey))
            }

            chainAccounts.forEach { account ->
                assertEquals(account.secretKey, account.stored, keptOrArchived(preferences, account.secretKey, shipped))
            }
            assertEquals(ton.stored, preferences.getDecryptedString("$VERSION_4_TON_META_ID:TON_SECRETS"))
        }

        /**
         * The value a shipped build stored under [key]: still active, or, if
         * the upgrade rewrote the active value, kept exactly in the archive.
         */
        private fun keptOrArchived(
            preferences: EncryptedPreferences,
            key: String,
            shipped: Map<String, String?>
        ): String {
            val stored = checkNotNull(shipped.getValue(key)) { "$key was not stored by the shipped build" }
            val active = checkNotNull(preferences.getDecryptedString(key)) { "$key is gone" }
            if (active != stored) {
                assertEquals(key, stored, preferences.getDecryptedString(WalletSecretArchive.keyFor(key)))
            }
            return stored
        }
    }

    companion object {
        const val POLKADOT = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val PARALLEL = "e61a41c53f5dcd0beb09df93b34402aada44cb05117b71059cce40a2723a4e97"
        const val TINKER = "d42e9606a995dfe433dc7955dc2a70f495f350f373daa200098ae84437816ad2"
        const val OLD_MNEMONIC_META_ID = 1L
        const val OLD_RAW_SEED_META_ID = 2L
        const val VERSION_4_TON_META_ID = 3L
        const val RAW_SEED_HEX = "0x9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"

        /** PRAGMA table_info(chains) after the shipped 71 -> 72 rebuild and 72 -> 73. */
        val SHIPPED_CHAIN_COLUMNS = listOf(
            "id", "paraId", "parentId", "rank", "name", "minSupportedVersion", "icon", "prefix",
            "isEthereumBased", "isTestNet", "hasCrowdloans", "supportStakingPool", "isEthereumChain",
            "isChainlinkProvider", "supportNft", "isUsesAppId", "identityChain", "ecosystem",
            "androidMinAppVersion", "staking_url", "staking_type", "history_url", "history_type",
            "crowdloans_url", "crowdloans_type", "remoteAssetsSource", "tonBridgeUrl"
        )

        val DANGLING_BEFORE_UPGRADE = listOf(
            listOf("assets", "chains"),
            listOf("assets", "chains"),
            listOf("chain_accounts", "chains"),
            listOf("chain_explorers", "chains"),
            listOf("chain_nodes", "chains"),
            listOf("chain_nodes", "chains"),
            listOf("chain_nodes", "chains"),
            listOf("favorite_chains", "chains")
        )

        /** The user's own rows stay; the registry's unreachable caches are gone. */
        val TOLERATED_AFTER_UPGRADE = listOf(
            listOf("assets", "chains"),
            listOf("assets", "chains"),
            listOf("chain_accounts", "chains"),
            listOf("favorite_chains", "chains")
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cohorts() = listOf(
            Cohort("3.7.4 to 4.0.1 (database 73)", listOf(RELEASE_4_0_1, RELEASE_4_0_1)),
            Cohort("3.7.4 to 4.1.1 (database 74)", listOf(RELEASE_4_1_1, RELEASE_4_1_1, RELEASE_4_1_1)),
            Cohort(
                "3.7.4 to 4.2.0 229 (database 75)",
                listOf(RELEASE_4_2_0, RELEASE_4_2_0, RELEASE_4_2_0, RELEASE_4_2_0)
            ),
            Cohort(
                "3.7.4 to 4.0.1, 4.1.1, then 4.2.0 229 (database 75)",
                listOf(RELEASE_4_0_1, RELEASE_4_0_1, RELEASE_4_1_1, RELEASE_4_2_0)
            )
        )
    }
}
