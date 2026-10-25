package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedSchemaFixture
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedSchemaFixtures
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV200DatabaseFixture
import jp.co.soramitsu.fearless_utils.extensions.toHexString as toPlainHexString
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic user endpoints in the exact released DB30/33/37/40 schemas.
 * The legacy writer persisted nodes.isDefault=false; the DB33 writer persisted
 * chain_nodes.isDefault=false. Their names, URLs and selection are user data.
 *
 * Full paths use the production builder and the already-pinned DB30 six-field
 * wallet vector. Two opens share a process and preferences instance: this is
 * not process-death U9, historical installed-writer or remote-registry coverage.
 */
class ReleasedCustomNodeMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val createdDatabases = mutableSetOf<String>()
    private val legacySecret by lazy { ReleasedV200DatabaseFixture.legacySecretHex(context) }
    private val legacySchema = TonMigration.MetaAccountSecretsV69
    private val legacyWallet by lazy { legacySchema.read(legacySecret) }
    private val substrateKeypair by lazy { legacyWallet[legacySchema.SubstrateKeypair] }
    private val ethereumKeypair by lazy { checkNotNull(legacyWallet[legacySchema.EthereumKeypair]) }
    private val substratePublicKey by lazy { substrateKeypair[KeyPairSchema.PublicKey] }
    private val ethereumPublicKey by lazy { ethereumKeypair[KeyPairSchema.PublicKey] }
    private val chainSecretKey by lazy {
        "$META_ID:${substratePublicKey.substrateAccountId().toPlainHexString(withPrefix = false)}:ACCESS_SECRETS"
    }
    private val chainSecret by lazy {
        ChainAccountSecrets { secret ->
            secret[ChainAccountSecrets.Entropy] = legacyWallet[legacySchema.Entropy]
            secret[ChainAccountSecrets.Seed] = legacyWallet[legacySchema.Seed]
            secret[ChainAccountSecrets.Keypair] = substrateKeypair
            secret[ChainAccountSecrets.DerivationPath] = legacyWallet[legacySchema.SubstrateDerivationPath]
        }.toHexString()
    }

    @After
    fun removeOwnedTestDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
    }

    @Test
    fun legacyVersion30EdgePreservesCustomEndpointNameUrlAndSelection() {
        val fixture = install("2.0.0", "legacy-edge", withChainAccount = true) {
            seedRegistryDefaults(hasFlags = false)
            seedLegacyNodes()
        }
        withSource(fixture) {
            assertLegacyNodes()
            MigrateTablesToV2_30_31.migrate(this)
            assertCustomRows()
            assertDefaultRows()
            assertSqliteHealthy()
        }
    }

    @Test
    fun legacyVersion30CustomNodesSurviveProductionOpen() = runBlocking {
        val fixture = install("2.0.0", "legacy-full", withChainAccount = true) {
            seedRegistryDefaults(hasFlags = false)
            seedLegacyNodes()
        }
        assertProductionOpensPreserve(fixture)
    }

    @Test
    fun version33CustomNodesSurviveRegistryRebuildAndProductionOpen() = runBlocking {
        val fixture = install("2.0.1", "chain-v33", withChainAccount = true) {
            seedRegistryDefaults(hasFlags = true)
            seedCustomRows()
        }
        assertProductionOpensPreserve(fixture)
    }

    @Test
    fun version37CustomNodeKeepsChainWithoutChainAccount() = runBlocking {
        val fixture = install("2.0.3", "chain-v37", withChainAccount = false) {
            seedRegistryDefaults(hasFlags = true)
            seedCustomRows()
            assertEquals(0L, singleLong("SELECT COUNT(*) FROM chain_accounts"))
        }
        assertProductionOpensPreserve(fixture)
    }

    @Test
    fun version40CustomNodeHealthyControl() = runBlocking {
        val fixture = install("2.0.4", "chain-v40", withChainAccount = true) {
            seedRegistryDefaults(hasFlags = true)
            seedCustomRows()
        }
        assertProductionOpensPreserve(fixture)
    }

    @Test
    fun version30RegistryDefaultsStayDefaults() {
        val fixture = install("2.0.0", "defaults-only", withChainAccount = true) {
            seedRegistryDefaults(hasFlags = false)
            assertEquals(0L, singleLong("SELECT COUNT(*) FROM nodes"))
        }
        withSource(fixture) {
            MigrateTablesToV2_30_31.migrate(this)
            assertDefaultRows()
            assertEquals(0L, singleLong("SELECT COUNT(*) FROM chain_nodes WHERE isDefault = 0"))
            assertSqliteHealthy()
        }
    }

    private data class Fixture(
        val name: String,
        val schema: ReleasedSchemaFixture,
        val preferences: HashMapEncryptedPreferences,
        val withChainAccount: Boolean
    )

    private fun install(
        release: String,
        suffix: String,
        withChainAccount: Boolean,
        seed: SupportSQLiteDatabase.() -> Unit
    ): Fixture {
        val schema = ReleasedSchemaFixtures.load(context, release)
        assertEquals(RELEASE_VERSIONS.getValue(release), schema.databaseVersion)
        val name = "released-custom-nodes-$suffix"
        check(createdDatabases.add(name))
        ReleasedSchemaFixtures.createDatabase(context, name, schema)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(LEGACY_SECRET_KEY, legacySecret)
            putEncryptedString(UNRELATED_KEY, UNRELATED_VALUE)
            if (withChainAccount) putEncryptedString(chainSecretKey, chainSecret)
        }
        return Fixture(name, schema, preferences, withChainAccount).also { fixture ->
            withSource(fixture) {
                assertFreshReleasedSchema(schema)
                seedWalletAndParents(withChainAccount)
                seed()
                assertSqliteHealthy()
            }
        }
    }

    private fun withSource(fixture: Fixture, action: SupportSQLiteDatabase.() -> Unit) {
        val callback = object : SupportSQLiteOpenHelper.Callback(fixture.schema.databaseVersion) {
            override fun onCreate(db: SupportSQLiteDatabase) = error("Released fixture was not installed")
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                error("Unexpected setup migration: $oldVersion to $newVersion")
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(fixture.name).callback(callback).build()
        )
        try {
            val database = helper.writableDatabase
            database.setForeignKeyConstraintsEnabled(true)
            database.beginTransaction()
            try {
                database.action()
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        } finally {
            helper.close()
        }
    }

    private fun SupportSQLiteDatabase.assertFreshReleasedSchema(schema: ReleasedSchemaFixture) {
        assertEquals(schema.databaseVersion, version)
        assertEquals(schema.roomIdentityHash, singleString("SELECT identity_hash FROM room_master_table WHERE id = 42"))
        query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            while (cursor.moveToNext()) {
                val table = cursor.getString(0)
                if (table !in setOf("android_metadata", "sqlite_sequence", "room_master_table")) {
                    assertEquals("Fresh $table must be empty", 0L, singleLong("SELECT COUNT(*) FROM `$table`"))
                }
            }
        }
        val hasFlags = "isDefault" in columns("chain_nodes")
        assertEquals(schema.databaseVersion >= 31, hasFlags)
        if (schema.databaseVersion == 30) {
            assertEquals(setOf("id", "name", "link", "networkType", "isDefault", "isActive"), columns("nodes"))
        }
    }

    private fun SupportSQLiteDatabase.seedWalletAndParents(withChainAccount: Boolean) {
        insert("meta_accounts", linkedMapOf(
            "id" to META_ID,
            "substratePublicKey" to substratePublicKey,
            "substrateCryptoType" to CryptoType.SR25519.name,
            "substrateAccountId" to substratePublicKey.substrateAccountId(),
            "ethereumPublicKey" to ethereumPublicKey,
            "ethereumAddress" to ethereumPublicKey.ethereumAddressFromPublicKey(),
            "name" to WALLET_NAME,
            "isSelected" to 1,
            "position" to 0
        ))
        listOf(KUSAMA to 2, POLKADOT to 0).forEach { (chainId, prefix) ->
            insert("chains", linkedMapOf(
                "id" to chainId, "name" to "Fixture $chainId", "icon" to "fixture-icon",
                "prefix" to prefix, "isEthereumBased" to 0, "isTestNet" to 0, "hasCrowdloans" to 0
            ))
        }
        if (withChainAccount) {
            val values = linkedMapOf<String, Any?>(
                "metaId" to META_ID, "chainId" to KUSAMA, "publicKey" to substratePublicKey,
                "accountId" to substratePublicKey.substrateAccountId(), "cryptoType" to CryptoType.SR25519.name
            )
            if ("name" in columns("chain_accounts")) values["name"] = CHAIN_ACCOUNT_NAME
            insert("chain_accounts", values)
        }
        assertEquals(if (withChainAccount) 1L else 0L, singleLong("SELECT COUNT(*) FROM chain_accounts"))
    }

    private fun SupportSQLiteDatabase.seedRegistryDefaults(hasFlags: Boolean) {
        DEFAULTS.forEach { node ->
            val values = linkedMapOf<String, Any?>("chainId" to node.chainId, "url" to node.url, "name" to node.name)
            if (hasFlags) {
                values["isActive"] = 0
                values["isDefault"] = 1
            }
            insert("chain_nodes", values)
        }
        assertEquals(2L, singleLong("SELECT COUNT(*) FROM chain_nodes"))
    }

    private fun SupportSQLiteDatabase.seedLegacyNodes() {
        CUSTOMS.forEachIndexed { ordinal, node ->
            insert("nodes", linkedMapOf(
                "name" to node.name, "link" to node.url, "networkType" to ordinal,
                "isDefault" to 0, "isActive" to if (node.active) 1 else 0
            ))
        }
        insert("nodes", linkedMapOf(
            "name" to DEFAULTS.first().name, "link" to DEFAULTS.first().url,
            "networkType" to 0, "isDefault" to 1, "isActive" to 0
        ))
        assertLegacyNodes()
    }

    private fun SupportSQLiteDatabase.assertLegacyNodes() {
        assertEquals(3L, singleLong("SELECT COUNT(*) FROM nodes"))
        assertEquals(1L, singleLong("SELECT COUNT(*) FROM nodes WHERE isActive = 1"))
        CUSTOMS.forEachIndexed { ordinal, node ->
            query("SELECT name, networkType, isDefault, isActive FROM nodes WHERE link = ?", arrayOf(node.url)).use { cursor ->
                assertTrue("Legacy custom endpoint exists: ${node.url}", cursor.moveToFirst())
                assertEquals(node.name, cursor.getString(0))
                assertEquals(ordinal, cursor.getInt(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(if (node.active) 1 else 0, cursor.getInt(3))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    private fun SupportSQLiteDatabase.seedCustomRows() {
        CUSTOMS.forEach { node ->
            insert("chain_nodes", linkedMapOf(
                "chainId" to node.chainId, "url" to node.url, "name" to node.name,
                "isActive" to if (node.active) 1 else 0, "isDefault" to 0
            ))
        }
        assertCustomRows()
        assertDefaultRows()
    }

    private suspend fun assertProductionOpensPreserve(fixture: Fixture) {
        var firstOpenPreferences: Map<String, String>? = null
        // Same process, same preferences. No process-death or remote-sync claim.
        repeat(2) {
            val database = AppDatabase.create(
                context = context, databaseName = fixture.name,
                storeV1 = SecretStoreV1Impl(fixture.preferences), storeV2 = SecretStoreV2(fixture.preferences),
                encryptedPreferences = fixture.preferences,
                substrateSecretStore = SubstrateSecretStore(fixture.preferences),
                ethereumSecretStore = EthereumSecretStore(fixture.preferences)
            )
            try {
                val sql = database.openHelper.writableDatabase
                assertEquals(APP_DATABASE_VERSION, sql.version)
                sql.assertSqliteHealthy()
                assertWalletAndSecrets(sql, fixture)
                sql.assertCustomRows()
                CUSTOMS.forEach { expected ->
                    val node = database.chainDao().getNode(expected.chainId, expected.url)
                    // ChainNodeLocal.equals deliberately omits isActive: compare each field.
                    assertEquals(expected.chainId, node.chainId)
                    assertEquals(expected.url, node.url)
                    assertEquals(expected.name, node.name)
                    assertEquals(expected.active, node.isActive)
                    assertFalse(node.isDefault)
                    assertEquals("Fixture ${expected.chainId}", sql.singleString("SELECT name FROM chains WHERE id = ?", expected.chainId))
                }
                val after = fixture.preferences.snapshot()
                firstOpenPreferences?.let { before -> assertEquals(before, after) }
                firstOpenPreferences = after
            } finally {
                database.close()
            }
        }
    }

    private fun assertWalletAndSecrets(sql: SupportSQLiteDatabase, fixture: Fixture) {
        assertEquals(1L, sql.singleLong("SELECT COUNT(*) FROM meta_accounts"))
        assertEquals(WALLET_NAME, sql.singleString("SELECT name FROM meta_accounts WHERE id = ?", META_ID))
        assertEquals(1L, sql.singleLong("SELECT isSelected FROM meta_accounts WHERE id = ?", META_ID))
        assertEquals(0L, sql.singleLong("SELECT position FROM meta_accounts WHERE id = ?", META_ID))
        assertEquals(
            CryptoType.SR25519.name,
            sql.singleString("SELECT substrateCryptoType FROM meta_accounts WHERE id = ?", META_ID)
        )
        assertArrayEquals(substratePublicKey, sql.singleBlob("SELECT substratePublicKey FROM meta_accounts WHERE id = ?", META_ID))
        assertArrayEquals(substratePublicKey.substrateAccountId(), sql.singleBlob("SELECT substrateAccountId FROM meta_accounts WHERE id = ?", META_ID))
        assertArrayEquals(ethereumPublicKey, sql.singleBlob("SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?", META_ID))
        assertArrayEquals(ethereumPublicKey.ethereumAddressFromPublicKey(), sql.singleBlob("SELECT ethereumAddress FROM meta_accounts WHERE id = ?", META_ID))
        val preferences = fixture.preferences
        val substrate = checkNotNull(SubstrateSecretStore(preferences).get(
            META_ID, substratePublicKey, CryptoType.SR25519, substratePublicKey.substrateAccountId()
        ))
        assertEquals(substrateKeypair.toHexString(), substrate[SubstrateSecrets.SubstrateKeypair].toHexString())
        assertArrayEquals(legacyWallet[legacySchema.Entropy], substrate[SubstrateSecrets.Entropy])
        assertArrayEquals(legacyWallet[legacySchema.Seed], substrate[SubstrateSecrets.Seed])
        assertEquals(legacyWallet[legacySchema.SubstrateDerivationPath], substrate[SubstrateSecrets.SubstrateDerivationPath])
        val ethereum = checkNotNull(EthereumSecretStore(preferences).get(
            META_ID, ethereumPublicKey, ethereumPublicKey.ethereumAddressFromPublicKey()
        ))
        assertEquals(ethereumKeypair.toHexString(), ethereum[EthereumSecrets.EthereumKeypair].toHexString())
        assertArrayEquals(legacyWallet[legacySchema.Entropy], ethereum[EthereumSecrets.Entropy])
        assertArrayEquals(ethereumKeypair[KeyPairSchema.PrivateKey], ethereum[EthereumSecrets.Seed])
        assertEquals(legacyWallet[legacySchema.EthereumDerivationPath], ethereum[EthereumSecrets.EthereumDerivationPath])
        assertFalse(preferences.hasKey(LEGACY_SECRET_KEY))
        assertFalse(preferences.hasKeyWithPrefix(WalletSecretQuarantine.KEY_PREFIX))
        assertFalse(preferences.hasKeyWithPrefix(WalletPublicIdentityRecovery.KEY_PREFIX))
        assertEquals(UNRELATED_VALUE, preferences.getDecryptedString(UNRELATED_KEY))
        assertEquals(if (fixture.withChainAccount) 1L else 0L, sql.singleLong("SELECT COUNT(*) FROM chain_accounts"))
        if (fixture.withChainAccount) {
            assertArrayEquals(substratePublicKey, sql.singleBlob("SELECT publicKey FROM chain_accounts WHERE metaId = ? AND chainId = ?", META_ID, KUSAMA))
            assertArrayEquals(
                substratePublicKey.substrateAccountId(),
                sql.singleBlob("SELECT accountId FROM chain_accounts WHERE metaId = ? AND chainId = ?", META_ID, KUSAMA)
            )
            assertEquals(
                CryptoType.SR25519.name,
                sql.singleString("SELECT cryptoType FROM chain_accounts WHERE metaId = ? AND chainId = ?", META_ID, KUSAMA)
            )
            assertEquals(chainSecret, preferences.getDecryptedString(chainSecretKey))
        }
    }

    private fun SupportSQLiteDatabase.assertCustomRows() {
        CUSTOMS.forEach { assertNode(it, default = false) }
        assertEquals(2L, singleLong("SELECT COUNT(*) FROM chain_nodes WHERE isDefault = 0"))
    }

    private fun SupportSQLiteDatabase.assertDefaultRows() {
        DEFAULTS.forEach { assertNode(it, default = true) }
    }

    private fun SupportSQLiteDatabase.assertNode(node: ExpectedNode, default: Boolean) {
        query(
            "SELECT name, isActive, isDefault FROM chain_nodes WHERE chainId = ? AND url = ?",
            arrayOf(node.chainId, node.url)
        ).use { cursor ->
            assertTrue("Endpoint must survive: ${node.chainId} ${node.url}", cursor.moveToFirst())
            assertEquals(node.name, cursor.getString(0))
            assertEquals(if (node.active) 1 else 0, cursor.getInt(1))
            assertEquals(if (default) 1 else 0, cursor.getInt(2))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun SupportSQLiteDatabase.assertSqliteHealthy() {
        assertEquals("ok", singleString("PRAGMA integrity_check"))
        assertEquals(0L, query("PRAGMA foreign_key_check").use { it.count.toLong() })
    }

    private fun SupportSQLiteDatabase.columns(table: String): Set<String> =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val column = cursor.getColumnIndexOrThrow("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(column)) }
        }

    private fun SupportSQLiteDatabase.insert(table: String, values: Map<String, Any?>) {
        assertTrue("Released $table has every seeded column", columns(table).containsAll(values.keys))
        val names = values.keys.joinToString { "`$it`" }
        val placeholders = values.keys.joinToString { "?" }
        execSQL("INSERT INTO `$table` ($names) VALUES ($placeholders)", values.values.toTypedArray())
    }

    private fun SupportSQLiteDatabase.singleString(sql: String, vararg args: Any?): String =
        query(sql, args).use { cursor -> check(cursor.moveToFirst()); cursor.getString(0) }

    private fun SupportSQLiteDatabase.singleBlob(sql: String, vararg args: Any?): ByteArray =
        query(sql, args).use { cursor -> check(cursor.moveToFirst()); cursor.getBlob(0) }

    private fun SupportSQLiteDatabase.singleLong(sql: String, vararg args: Any?): Long =
        query(sql, args).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }

    private fun HashMapEncryptedPreferences.snapshot(): Map<String, String> = keysWithPrefixes(
        prefixes = setOf(""), maxResultCount = 256, maxKeyBytes = 1_024,
        maxTotalKeyBytes = 65_536, failOnOversizedMatch = true
    ).associateWith { checkNotNull(getDecryptedString(it)) }

    private data class ExpectedNode(val chainId: String, val url: String, val name: String, val active: Boolean)

    private companion object {
        const val META_ID = 30L
        const val WALLET_NAME = "Released custom endpoint wallet"
        const val CHAIN_ACCOUNT_NAME = "Owned Kusama account"
        const val LEGACY_SECRET_KEY = "$META_ID:ACCESS_SECRETS"
        const val UNRELATED_KEY = "custom-node-fixture-unrelated"
        const val UNRELATED_VALUE = "unchanged"
        // Released v2.0.0 source e43832fab8f59bbf57c8ca97d7384c0ad77fd90e:
        // Node.NetworkType ordinals 0/1 expose these literal genesis hashes as chainId.
        const val KUSAMA = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
        const val POLKADOT = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        val RELEASE_VERSIONS = mapOf("2.0.0" to 30, "2.0.1" to 33, "2.0.3" to 37, "2.0.4" to 40)
        val CUSTOMS = listOf(
            ExpectedNode(KUSAMA, "wss://kusama.custom.invalid/rpc?q=one", "  My Kusama 'node' 🧪  ", true),
            ExpectedNode(POLKADOT, "wss://polkadot.custom.invalid/rpc?q=two", "Polkadot \"备用\"", false)
        )
        val DEFAULTS = listOf(
            ExpectedNode(KUSAMA, "wss://kusama.catalog.invalid/rpc", "Registry Kusama", false),
            ExpectedNode(POLKADOT, "wss://polkadot.catalog.invalid/rpc", "Registry Polkadot", false)
        )
    }
}
