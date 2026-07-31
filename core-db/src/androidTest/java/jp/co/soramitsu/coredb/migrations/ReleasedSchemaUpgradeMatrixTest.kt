package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString as toPlainHexString
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Opens populated copies of every checked-in released Room schema through the
 * same [AppDatabase] builder and migration graph used by the wallet process.
 *
 * Do not replace these fixtures with hand-written historical schemas. Room's
 * exported JSON captures identity hashes, indexes, foreign keys, defaults, and
 * the physical column order that a release actually put on user devices.
 */
@RunWith(Parameterized::class)
class ReleasedSchemaUpgradeMatrixTest(
    private val startVersion: Int
) {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val databaseName = "released-schema-$startVersion-to-$APP_DATABASE_VERSION"

    @get:Rule
    val legacySchemaHelper = MigrationTestHelper(
        instrumentation,
        LEGACY_DATABASE_CANONICAL_NAME,
        FrameworkSQLiteOpenHelperFactory()
    )

    @get:Rule
    val currentSchemaHelper = MigrationTestHelper(
        instrumentation,
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun deleteIsolatedDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun exactReleasedSchemaUpgradesTo77WithoutLosingWalletState() = runBlocking {
        val encryptedPreferences = HashMapEncryptedPreferences()
        val storeV1 = SecretStoreV1Impl(encryptedPreferences)
        val storeV2 = SecretStoreV2(encryptedPreferences)
        val substrateSecretStore = SubstrateSecretStore(encryptedPreferences)
        val ethereumSecretStore = EthereumSecretStore(encryptedPreferences)

        if (startVersion in LEGACY_RELEASED_VERSIONS) {
            storeV1.saveSecuritySource(
                LEGACY_WALLET_KEYPAIR.publicKey.toAddress(0),
                SecuritySource.Unspecified(LEGACY_WALLET_KEYPAIR)
            )
            legacySchemaHelper.createDatabase(databaseName, startVersion).use { database ->
                assertEquals(startVersion, database.version)
                database.insertLegacyReleasedFixture(startVersion)
                database.assertSqliteHealthy()
            }
        } else {
            currentSchemaHelper.createDatabase(databaseName, startVersion).use { database ->
                assertEquals(startVersion, database.version)
                database.insertModernReleasedFixture(startVersion)
                database.assertSqliteHealthy()
            }
            encryptedPreferences.insertModernSecrets(startVersion)
        }

        val roomDatabase = AppDatabase.create(
            context = context,
            databaseName = databaseName,
            storeV1 = storeV1,
            storeV2 = storeV2,
            encryptedPreferences = encryptedPreferences,
            substrateSecretStore = substrateSecretStore,
            ethereumSecretStore = ethereumSecretStore
        )
        try {
            val database = roomDatabase.openHelper.writableDatabase
            assertEquals(APP_DATABASE_VERSION, database.version)
            database.assertSqliteHealthy()
            if (startVersion in LEGACY_RELEASED_VERSIONS) {
                database.assertLegacyFixturePreserved(startVersion)
                assertLegacySecretPreserved(
                    encryptedPreferences = encryptedPreferences,
                    substrateSecretStore = substrateSecretStore
                )
            } else {
                database.assertModernFixturePreserved(startVersion)
                assertModernSecretsPreserved(
                    encryptedPreferences = encryptedPreferences,
                    substrateSecretStore = substrateSecretStore,
                    startVersion = startVersion
                )
            }
        } finally {
            roomDatabase.close()
        }
    }

    private fun SupportSQLiteDatabase.insertLegacyReleasedFixture(version: Int) {
        execSQL(
            """
            INSERT INTO users(
                address, username, publicKey, cryptoType, position, networkType
            ) VALUES(?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                LEGACY_WALLET_KEYPAIR.publicKey.toAddress(0),
                LEGACY_WALLET_NAME,
                LEGACY_WALLET_KEYPAIR.publicKey.toPlainHexString(withPrefix = false),
                CryptoType.SR25519.ordinal,
                0,
                0
            )
        )
        execSQL(
            """
            INSERT INTO chains(
                id, name, icon, prefix, isEthereumBased, isTestNet,
                hasCrowdloans
            ) VALUES(?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                CHAIN_ID,
                "Released chain $version",
                "released-chain-icon",
                42,
                0,
                0,
                0
            )
        )
        execSQL(
            """
            INSERT INTO chain_nodes(chainId, url, name)
            VALUES(?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(CHAIN_ID, CHAIN_NODE_URL, "Released endpoint")
        )
        execSQL(
            """
            INSERT INTO storage(storageKey, content, chainId)
            VALUES(?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(STORAGE_KEY, "legacy-storage-$version", CHAIN_ID)
        )
        execSQL(
            """
            INSERT INTO meta_accounts(
                id, substratePublicKey, substrateCryptoType,
                substrateAccountId, ethereumPublicKey, ethereumAddress,
                name, isSelected, position
            ) VALUES(?, ?, ?, ?, NULL, NULL, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                LEGACY_EXISTING_META_ID,
                LEGACY_CHAIN_KEYPAIR.publicKey,
                CryptoType.SR25519.name,
                LEGACY_CHAIN_ACCOUNT_ID,
                LEGACY_EXISTING_WALLET_NAME,
                0,
                41
            )
        )
        execSQL(
            """
            INSERT INTO chain_accounts(
                metaId, chainId, publicKey, accountId, cryptoType
            ) VALUES(?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                LEGACY_EXISTING_META_ID,
                CHAIN_ID,
                LEGACY_CHAIN_KEYPAIR.publicKey,
                LEGACY_CHAIN_ACCOUNT_ID,
                CryptoType.SR25519.name
            )
        )
    }

    private fun SupportSQLiteDatabase.insertModernReleasedFixture(version: Int) {
        execSQL(
            """
            INSERT INTO meta_accounts(
                id, substratePublicKey, substrateCryptoType,
                substrateAccountId, name, isSelected, position,
                isBackedUp, initialized
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                MODERN_META_ID,
                MODERN_ROOT_KEYPAIR.publicKey,
                CryptoType.SR25519.name,
                MODERN_ROOT_ACCOUNT_ID,
                MODERN_WALLET_NAME,
                1,
                0,
                1,
                1
            )
        )
        execSQL(
            """
            INSERT INTO chains(
                id, name, icon, prefix, isEthereumBased, isTestNet,
                hasCrowdloans, supportStakingPool, isEthereumChain,
                isChainlinkProvider, supportNft, isUsesAppId, ecosystem
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                CHAIN_ID,
                "Released chain $version",
                "released-chain-icon",
                42,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                "Substrate"
            )
        )
        execSQL(
            """
            INSERT INTO chain_nodes(
                chainId, url, name, isActive, isDefault
            ) VALUES(?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(CHAIN_ID, CHAIN_NODE_URL, "Released endpoint", 1, 0)
        )
        execSQL(
            """
            INSERT INTO chain_assets(
                id, name, symbol, chainId, icon, staking, precision
            ) VALUES(?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                CHAIN_ASSET_ID,
                "Released asset",
                "REL",
                CHAIN_ID,
                "released-asset-icon",
                "Unsupported",
                12
            )
        )
        if (version >= 74) {
            execSQL(
                "UPDATE chain_assets SET coinbaseUrl = ? WHERE id = ? AND chainId = ?",
                arrayOf<Any>(COINBASE_URL, CHAIN_ASSET_ID, CHAIN_ID)
            )
        }
        if (version == 76) {
            execSQL(
                "UPDATE chains SET xcm = ? WHERE id = ?",
                arrayOf<Any>(XCM_PAYLOAD, CHAIN_ID)
            )
        }
        execSQL(
            """
            INSERT INTO chain_accounts(
                metaId, chainId, publicKey, accountId, cryptoType,
                name, initialized
            ) VALUES(?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                MODERN_META_ID,
                CHAIN_ID,
                MODERN_CHAIN_KEYPAIR.publicKey,
                MODERN_CHAIN_ACCOUNT_ID,
                CryptoType.SR25519.name,
                MODERN_CHAIN_ACCOUNT_NAME,
                1
            )
        )
        execSQL(
            """
            INSERT INTO ton_connection(
                metaId, clientId, name, icon, url, source
            ) VALUES(?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                MODERN_META_ID,
                "ab".repeat(16),
                TON_CONNECTION_NAME,
                "https://released.example/icon.png",
                TON_CONNECTION_URL,
                TON_CONNECTION_SOURCE
            )
        )
        execSQL(
            """
            INSERT INTO storage(storageKey, content, chainId)
            VALUES(?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(STORAGE_KEY, "modern-storage-$version", CHAIN_ID)
        )
        if (version == 74) {
            execSQL(
                """
                INSERT INTO sora_card(
                    id, accessToken, refreshToken,
                    accessTokenExpirationTime, kycStatus
                ) VALUES(?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    "released-sora-card",
                    "access-token",
                    "refresh-token",
                    123456789L,
                    "PENDING"
                )
            )
        }
    }

    private fun HashMapEncryptedPreferences.insertModernSecrets(version: Int) {
        putEncryptedString(
            MODERN_ROOT_ACCESS_KEY,
            MetaAccountSecrets(
                substrateKeyPair = MODERN_ROOT_KEYPAIR,
                seed = MODERN_ROOT_SEED,
                substrateDerivationPath = ""
            ).toHexString()
        )
        putEncryptedString(
            MODERN_CHAIN_ACCESS_KEY,
            MODERN_CHAIN_SECRET
        )
        putEncryptedString(
            MODERN_TON_CONNECT_KEY,
            "ton-connect-secret-$version"
        )
    }

    private fun SupportSQLiteDatabase.assertLegacyFixturePreserved(version: Int) {
        assertEquals(
            2L,
            singleLong("SELECT COUNT(*) FROM meta_accounts")
        )
        assertEquals(
            LEGACY_WALLET_NAME,
            singleString(
                "SELECT name FROM meta_accounts WHERE id = $LEGACY_MIGRATED_META_ID"
            )
        )
        assertArrayEquals(
            LEGACY_WALLET_KEYPAIR.publicKey,
            singleBlob(
                "SELECT substratePublicKey FROM meta_accounts " +
                    "WHERE id = $LEGACY_MIGRATED_META_ID"
            )
        )
        assertEquals(
            LEGACY_EXISTING_WALLET_NAME,
            singleString(
                "SELECT name FROM meta_accounts WHERE id = $LEGACY_EXISTING_META_ID"
            )
        )
        assertArrayEquals(
            LEGACY_CHAIN_ACCOUNT_ID,
            singleBlob(
                "SELECT accountId FROM chain_accounts " +
                    "WHERE metaId = $LEGACY_EXISTING_META_ID AND chainId = '$CHAIN_ID'"
            )
        )
        // Registry endpoints and runtime storage from this cohort are
        // refreshable caches. They are intentionally cleared at 39 -> 40 and
        // 29 -> 30 respectively. The chain parent and its signing identity
        // above are wallet-owned state and must remain.
        assertEquals(
            0L,
            singleLong("SELECT COUNT(*) FROM chain_nodes")
        )
        assertEquals(
            0L,
            singleLong("SELECT COUNT(*) FROM storage")
        )
        assertEquals(
            "Released chain $version",
            singleString("SELECT name FROM chains WHERE id = '$CHAIN_ID'")
        )
        assertEquals(
            1L,
            singleLong("SELECT COUNT(*) FROM legacy_users_recovery")
        )
        assertEquals(
            LEGACY_WALLET_KEYPAIR.publicKey.toPlainHexString(withPrefix = false),
            singleString("SELECT publicKey FROM legacy_users_recovery")
        )
        assertEquals(
            "PRESERVED_PENDING_REVIEW",
            singleString("SELECT recoveryState FROM legacy_users_recovery")
        )
        assertEquals(
            0L,
            singleLong("SELECT COUNT(*) FROM ton_connection")
        )
    }

    private fun assertLegacySecretPreserved(
        encryptedPreferences: HashMapEncryptedPreferences,
        substrateSecretStore: SubstrateSecretStore
    ) {
        assertFalse(encryptedPreferences.hasKey(LEGACY_MIGRATED_ACCESS_KEY))
        assertTrue(encryptedPreferences.hasKey(LEGACY_MIGRATED_SUBSTRATE_KEY))
        val secrets = checkNotNull(
            substrateSecretStore.get(
                metaId = LEGACY_MIGRATED_META_ID,
                expectedPublicKey = LEGACY_WALLET_KEYPAIR.publicKey,
                expectedCryptoType = CryptoType.SR25519,
                expectedAccountId = LEGACY_WALLET_ACCOUNT_ID
            )
        )
        val keypair = checkNotNull(secrets[SubstrateSecrets.SubstrateKeypair])
        assertArrayEquals(
            LEGACY_WALLET_KEYPAIR.publicKey,
            keypair[KeyPairSchema.PublicKey]
        )
        assertArrayEquals(
            LEGACY_WALLET_KEYPAIR.privateKey,
            keypair[KeyPairSchema.PrivateKey]
        )
        listOf(
            LEGACY_MIGRATED_ACCESS_KEY,
            LEGACY_MIGRATED_SUBSTRATE_KEY
        ).forEach { key ->
            assertFalse(
                encryptedPreferences.hasKey(WalletSecretQuarantine.keyFor(key))
            )
            assertFalse(
                encryptedPreferences.hasKey(
                    WalletPublicIdentityRecovery.keyFor(
                        LEGACY_MIGRATED_META_ID,
                        key
                    )
                )
            )
        }
    }

    private fun SupportSQLiteDatabase.assertModernFixturePreserved(version: Int) {
        assertEquals(
            MODERN_WALLET_NAME,
            singleString("SELECT name FROM meta_accounts WHERE id = $MODERN_META_ID")
        )
        assertArrayEquals(
            MODERN_ROOT_ACCOUNT_ID,
            singleBlob(
                "SELECT substrateAccountId FROM meta_accounts " +
                    "WHERE id = $MODERN_META_ID"
            )
        )
        assertEquals(
            MODERN_CHAIN_ACCOUNT_NAME,
            singleString(
                "SELECT name FROM chain_accounts " +
                    "WHERE metaId = $MODERN_META_ID AND chainId = '$CHAIN_ID'"
            )
        )
        assertArrayEquals(
            MODERN_CHAIN_ACCOUNT_ID,
            singleBlob(
                "SELECT accountId FROM chain_accounts " +
                    "WHERE metaId = $MODERN_META_ID AND chainId = '$CHAIN_ID'"
            )
        )
        assertEquals(
            CHAIN_NODE_URL,
            singleString("SELECT url FROM chain_nodes WHERE chainId = '$CHAIN_ID'")
        )
        assertEquals(
            "Released chain $version",
            singleString("SELECT name FROM chains WHERE id = '$CHAIN_ID'")
        )
        assertEquals(
            "modern-storage-$version",
            singleString(
                "SELECT content FROM storage " +
                    "WHERE storageKey = '$STORAGE_KEY' AND chainId = '$CHAIN_ID'"
            )
        )
        assertEquals(
            "REL",
            singleString(
                "SELECT symbol FROM chain_assets " +
                    "WHERE id = '$CHAIN_ASSET_ID' AND chainId = '$CHAIN_ID'"
            )
        )
        if (version >= 74) {
            assertEquals(
                COINBASE_URL,
                singleNullableString(
                    "SELECT coinbaseUrl FROM chain_assets " +
                        "WHERE id = '$CHAIN_ASSET_ID' AND chainId = '$CHAIN_ID'"
                )
            )
        } else {
            assertNull(
                singleNullableString(
                    "SELECT coinbaseUrl FROM chain_assets " +
                        "WHERE id = '$CHAIN_ASSET_ID' AND chainId = '$CHAIN_ID'"
                )
            )
        }
        if (version == 76) {
            assertEquals(
                XCM_PAYLOAD,
                singleNullableString("SELECT xcm FROM chains WHERE id = '$CHAIN_ID'")
            )
        } else {
            assertNull(
                singleNullableString("SELECT xcm FROM chains WHERE id = '$CHAIN_ID'")
            )
        }
        assertEquals(
            TON_CONNECTION_NAME,
            singleString(
                "SELECT name FROM ton_connection " +
                    "WHERE metaId = $MODERN_META_ID " +
                    "AND url = '$TON_CONNECTION_URL' " +
                    "AND source = '$TON_CONNECTION_SOURCE'"
            )
        )
        assertFalse(tableExists("sora_card"))
    }

    private fun assertModernSecretsPreserved(
        encryptedPreferences: HashMapEncryptedPreferences,
        substrateSecretStore: SubstrateSecretStore,
        startVersion: Int
    ) {
        assertFalse(encryptedPreferences.hasKey(MODERN_ROOT_ACCESS_KEY))
        assertTrue(encryptedPreferences.hasKey(MODERN_ROOT_SUBSTRATE_KEY))
        val rootSecrets = checkNotNull(
            substrateSecretStore.get(
                metaId = MODERN_META_ID,
                expectedPublicKey = MODERN_ROOT_KEYPAIR.publicKey,
                expectedCryptoType = CryptoType.SR25519,
                expectedAccountId = MODERN_ROOT_ACCOUNT_ID
            )
        )
        val rootKeypair = checkNotNull(
            rootSecrets[SubstrateSecrets.SubstrateKeypair]
        )
        assertArrayEquals(
            MODERN_ROOT_KEYPAIR.publicKey,
            rootKeypair[KeyPairSchema.PublicKey]
        )
        assertArrayEquals(
            MODERN_ROOT_KEYPAIR.privateKey,
            rootKeypair[KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(
            MODERN_ROOT_SEED,
            rootSecrets[SubstrateSecrets.Seed]
        )
        assertEquals(
            MODERN_CHAIN_SECRET,
            encryptedPreferences.getDecryptedString(MODERN_CHAIN_ACCESS_KEY)
        )
        assertEquals(
            "ton-connect-secret-$startVersion",
            encryptedPreferences.getDecryptedString(MODERN_TON_CONNECT_KEY)
        )
        listOf(
            MODERN_ROOT_ACCESS_KEY,
            MODERN_ROOT_SUBSTRATE_KEY,
            MODERN_CHAIN_ACCESS_KEY,
            MODERN_TON_CONNECT_KEY
        ).forEach { key ->
            assertFalse(
                encryptedPreferences.hasKey(WalletSecretQuarantine.keyFor(key))
            )
            assertFalse(
                encryptedPreferences.hasKey(
                    WalletPublicIdentityRecovery.keyFor(MODERN_META_ID, key)
                )
            )
        }
    }

    private fun SupportSQLiteDatabase.assertSqliteHealthy() {
        assertEquals("ok", singleString("PRAGMA integrity_check"))
        query("PRAGMA foreign_key_check").use { cursor ->
            assertFalse(cursor.moveToFirst())
        }
    }

    private fun SupportSQLiteDatabase.tableExists(tableName: String): Boolean {
        return singleLong(
            "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type = 'table' AND name = '$tableName'"
        ) == 1L
    }

    private fun SupportSQLiteDatabase.singleLong(sql: String): Long {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val value = cursor.getLong(0)
            assertFalse(cursor.moveToNext())
            value
        }
    }

    private fun SupportSQLiteDatabase.singleString(sql: String): String {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val value = cursor.getString(0)
            assertFalse(cursor.moveToNext())
            value
        }
    }

    private fun SupportSQLiteDatabase.singleNullableString(sql: String): String? {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val value = if (cursor.isNull(0)) null else cursor.getString(0)
            assertFalse(cursor.moveToNext())
            value
        }
    }

    private fun SupportSQLiteDatabase.singleBlob(sql: String): ByteArray {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val value = cursor.getBlob(0)
            assertFalse(cursor.moveToNext())
            value
        }
    }

    companion object {
        private const val LEGACY_DATABASE_CANONICAL_NAME =
            "jp.co.soramitsu.core_db.AppDatabase"
        private const val LEGACY_EXISTING_META_ID = 41L
        private const val LEGACY_MIGRATED_META_ID = 42L
        private const val MODERN_META_ID = 71L
        private const val LEGACY_WALLET_NAME = "Released legacy wallet"
        private const val LEGACY_EXISTING_WALLET_NAME =
            "Released existing wallet"
        private const val MODERN_WALLET_NAME = "Released modern wallet"
        private const val MODERN_CHAIN_ACCOUNT_NAME =
            "Released signing account"
        private const val CHAIN_ID = "released-matrix-chain"
        private const val CHAIN_NODE_URL = "wss://released-matrix.invalid"
        private const val CHAIN_ASSET_ID = "released-matrix-asset"
        private const val STORAGE_KEY = "released-matrix-storage"
        private const val COINBASE_URL =
            "https://released.example/coinbase"
        private const val XCM_PAYLOAD = "{\"version\":76}"
        private const val TON_CONNECTION_NAME = "Released TON connection"
        private const val TON_CONNECTION_URL =
            "https://released.example/tonconnect-manifest.json"
        private const val TON_CONNECTION_SOURCE = "WEB"

        private val LEGACY_RELEASED_VERSIONS = setOf(26, 27, 28)

        private val LEGACY_WALLET_KEYPAIR = substrateKeypair(seedOffset = 1)
        private val LEGACY_WALLET_ACCOUNT_ID =
            LEGACY_WALLET_KEYPAIR.publicKey.substrateAccountId()
        private val LEGACY_CHAIN_KEYPAIR = substrateKeypair(seedOffset = 41)
        private val LEGACY_CHAIN_ACCOUNT_ID =
            LEGACY_CHAIN_KEYPAIR.publicKey.substrateAccountId()

        private val MODERN_ROOT_SEED = deterministicSeed(offset = 81)
        private val MODERN_ROOT_KEYPAIR = substrateKeypair(MODERN_ROOT_SEED)
        private val MODERN_ROOT_ACCOUNT_ID =
            MODERN_ROOT_KEYPAIR.publicKey.substrateAccountId()
        private val MODERN_CHAIN_SEED = deterministicSeed(offset = 121)
        private val MODERN_CHAIN_KEYPAIR = substrateKeypair(MODERN_CHAIN_SEED)
        private val MODERN_CHAIN_ACCOUNT_ID =
            MODERN_CHAIN_KEYPAIR.publicKey.substrateAccountId()

        private const val LEGACY_MIGRATED_ACCESS_KEY =
            "$LEGACY_MIGRATED_META_ID:ACCESS_SECRETS"
        private const val LEGACY_MIGRATED_SUBSTRATE_KEY =
            "$LEGACY_MIGRATED_META_ID:SUBSTRATE_SECRETS"
        private const val MODERN_ROOT_ACCESS_KEY =
            "$MODERN_META_ID:ACCESS_SECRETS"
        private const val MODERN_ROOT_SUBSTRATE_KEY =
            "$MODERN_META_ID:SUBSTRATE_SECRETS"
        private val MODERN_CHAIN_ACCESS_KEY =
            "$MODERN_META_ID:" +
                MODERN_CHAIN_ACCOUNT_ID.toPlainHexString(withPrefix = false) +
                ":ACCESS_SECRETS"
        private val MODERN_TON_CONNECT_KEY = TonConnectStorageKeys.scoped(
            metaId = MODERN_META_ID,
            url = TON_CONNECTION_URL,
            source = TON_CONNECTION_SOURCE
        )
        private val MODERN_CHAIN_SECRET = ChainAccountSecrets(
            keyPair = MODERN_CHAIN_KEYPAIR,
            seed = MODERN_CHAIN_SEED,
            derivationPath = ""
        ).toHexString()

        @JvmStatic
        @Parameterized.Parameters(name = "released schema {0} -> 77")
        fun releasedVersions(): List<Array<Int>> =
            listOf(26, 27, 28, 73, 74, 75, 76).map { arrayOf(it) }

        private fun substrateKeypair(seedOffset: Int) =
            substrateKeypair(deterministicSeed(seedOffset))

        private fun substrateKeypair(seed: ByteArray) =
            SubstrateKeypairFactory.generate(
                encryptionType = EncryptionType.SR25519,
                seed = seed,
                junctions = emptyList()
            )

        private fun deterministicSeed(offset: Int) =
            ByteArray(32) { index -> (index + offset).toByte() }
    }
}
