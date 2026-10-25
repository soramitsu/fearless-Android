package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedSchemaFixtures
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV200DatabaseFixture
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Availability baseline, distinct from the existing fail-closed safety controls.
 * Exact released schemas plus synthetic offline endpoints must open a healthy
 * wallet even when no safe operational network binding can be inferred.
 * This exercises Room and secret readers, not the app startup gate or UI/U9.
 */
@RunWith(Parameterized::class)
class LegacyEndpointAvailabilityTest(
    private val release: String,
    private val issue: Issue
) {
    enum class Issue { HEALTHY, UNDEFINED, UNSEEDABLE, CONFLICTING_LABELS, OVERSIZED, AMBIGUOUS_ACALA }

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "legacy-endpoint-availability-$release-${issue.name}"
    private val schema by lazy { ReleasedSchemaFixtures.load(context, release) }
    private val preferences = HashMapEncryptedPreferences()
    private val tracking = TrackingPreferences(preferences)
    private var originalV1Secret: String? = null
    private val legacyKeypair by lazy {
        SubstrateKeypairFactory.generate(EncryptionType.SR25519, ByteArray(32) { (it + 37).toByte() }, emptyList())
    }
    private val address by lazy { legacyKeypair.publicKey.toAddress(2) }
    private val v2Secret by lazy { ReleasedV200DatabaseFixture.legacySecretHex(context) }
    private val v2Schema = TonMigration.MetaAccountSecretsV69
    private val v2Wallet by lazy { v2Schema.read(v2Secret) }
    private val v2Substrate by lazy { v2Wallet[v2Schema.SubstrateKeypair] }
    private val v2Ethereum by lazy { checkNotNull(v2Wallet[v2Schema.EthereumKeypair]) }

    @After
    fun removeOwnedDatabase() { context.deleteDatabase(databaseName) }

    @Test
    fun unresolvedEndpointDoesNotBlockHealthyWalletAndOriginalRowsRemainRecoverable() = runBlocking {
        install()
        val originalSql = withSource { sqlFingerprint() }
        val originalNodes = withSource { tableFingerprint("nodes") }
        val originalPreferences = preferences.snapshot()
        tracking.reset()
        var firstOpenedPreferences: Map<String, String>? = null

        // Same process only. No process-death or physical-wallet-screen claim.
        repeat(2) { opening ->
            val expectedVersion = if (opening == 0) schema.databaseVersion else APP_DATABASE_VERSION
            val beforeAttemptSql = if (opening == 0) originalSql else withSource(expectedVersion) { sqlFingerprint() }
            val beforeAttemptPreferences = if (opening == 0) originalPreferences else preferences.snapshot()
            tracking.reset()
            val database = AppDatabase.create(
                context = context, databaseName = databaseName,
                storeV1 = SecretStoreV1Impl(tracking), storeV2 = SecretStoreV2(tracking),
                encryptedPreferences = tracking,
                substrateSecretStore = SubstrateSecretStore(tracking),
                ethereumSecretStore = EthereumSecretStore(tracking)
            )
            val sql = try {
                database.openHelper.writableDatabase
            } catch (failure: Throwable) {
                database.close()
                // Preserve the availability failure, while proving whether the
                // failed production open left SQL and external stores intact.
                val unchangedSql = withSource(expectedVersion) { sqlFingerprint() } == beforeAttemptSql
                val unchangedPreferences = preferences.snapshot() == beforeAttemptPreferences
                val diagnostic = "release=$release issue=$issue opening=$opening; " +
                    "SQL unchanged=$unchangedSql; preferences unchanged=$unchangedPreferences; " +
                    "preference reads=${tracking.reads}; writes=${tracking.writes}; CAS=${tracking.casAttempts}"
                try {
                    assertTrue("Rejected open mutated SQL: $diagnostic", unchangedSql)
                    assertTrue("Rejected open mutated preferences: $diagnostic", unchangedPreferences)
                    assertEquals("Rejected open attempted external writes: $diagnostic", 0, tracking.writes)
                    assertEquals("Rejected open attempted CAS: $diagnostic", 0, tracking.casAttempts)
                } catch (unsafe: AssertionError) {
                    unsafe.addSuppressed(failure)
                    throw unsafe
                }
                throw AssertionError("Healthy wallet production open must succeed: $diagnostic", failure)
            }
            try {
                assertEquals(APP_DATABASE_VERSION, sql.version)
                assertEquals("ok", sql.text("PRAGMA integrity_check"))
                sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                assertWalletAndSecrets(sql)
                if (issue == Issue.HEALTHY) {
                    // The healthy control is independent of archive policy.
                    val node = database.chainDao().getNode(KUSAMA, URL)
                    assertEquals(KUSAMA, node.chainId)
                    assertEquals(URL, node.url)
                    assertEquals(LABEL, node.name)
                    assertTrue(node.isActive)
                    assertFalse(node.isDefault)
                } else {
                    assertEquals("Unresolved raw source must remain reachable", 1L, sql.scalar(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'nodes'"
                    ))
                    assertEquals("Every original rowid, storage class and byte must remain exact", originalNodes, sql.tableFingerprint("nodes"))
                }
                val after = preferences.snapshot()
                firstOpenedPreferences?.let { assertEquals("Second open must not rewrite preferences", it, after) }
                firstOpenedPreferences = after
            } finally {
                database.close()
            }
        }
    }

    private fun install() = runBlocking {
        assertEquals(if (release == "2.0.0") 30 else 27, schema.databaseVersion)
        ReleasedSchemaFixtures.createDatabase(context, databaseName, schema)
        if (schema.databaseVersion == 27) {
            SecretStoreV1Impl(preferences).saveSecuritySource(address, SecuritySource.Unspecified(legacyKeypair))
            originalV1Secret = checkNotNull(preferences.getDecryptedString("security_source_$address"))
        } else {
            preferences.putEncryptedString("$META_ID:ACCESS_SECRETS", v2Secret)
        }
        preferences.putEncryptedString(SENTINEL_KEY, SENTINEL_VALUE)
        withSource {
            assertEquals(schema.databaseVersion, version)
            assertEquals(schema.roomIdentityHash, text("SELECT identity_hash FROM room_master_table WHERE id = 42"))
            assertEquals(setOf("id", "name", "link", "networkType", "isDefault", "isActive"), columns("nodes").toSet())
            assertEquals(0L, scalar("SELECT COUNT(*) FROM nodes"))
            if (schema.databaseVersion == 27) {
                assertEquals(0L, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('chains', 'chain_nodes', 'meta_accounts')"))
                execSQL(
                    "INSERT INTO users(address, username, publicKey, cryptoType, position, networkType) VALUES (?, ?, ?, ?, 0, 0)",
                    arrayOf(address, WALLET_NAME, legacyKeypair.publicKey.toHexString(withPrefix = false), CryptoType.SR25519.ordinal)
                )
            } else {
                assertEquals(0L, scalar("SELECT COUNT(*) FROM meta_accounts"))
                val substrate = v2Substrate[KeyPairSchema.PublicKey]
                val ethereum = v2Ethereum[KeyPairSchema.PublicKey]
                execSQL(
                    "INSERT INTO meta_accounts(id, substratePublicKey, substrateCryptoType, substrateAccountId, " +
                        "ethereumPublicKey, ethereumAddress, name, isSelected, position) VALUES (?, ?, 'SR25519', ?, ?, ?, ?, 1, 0)",
                    arrayOf(META_ID, substrate, substrate.substrateAccountId(), ethereum, ethereum.ethereumAddressFromPublicKey(), WALLET_NAME)
                )
                seedParent(KUSAMA, "Synthetic Kusama parent", 2)
                if (issue == Issue.AMBIGUOUS_ACALA) {
                    // Synthetic registry contents in released DB30 columns,
                    // keyed by the two source-pinned identities. No invented
                    // historical Acala metadata is claimed.
                    seedParent(OLD_ACALA, "Synthetic old Acala parent", 10)
                    seedParent(CORRECTED_ACALA, "Synthetic corrected Acala parent", 10)
                }
            }
            val network = when (issue) {
                Issue.UNDEFINED -> 15 // Actual released Undefined enum member.
                Issue.UNSEEDABLE -> 3 // Exact released Rococo literal; no parent metadata is seeded.
                Issue.AMBIGUOUS_ACALA -> 6
                else -> 0
            }
            val label = if (issue == Issue.OVERSIZED) "x".repeat((TON_UPGRADE_MAX_CELL_PAYLOAD_BYTES + 1).toInt()) else LABEL
            execSQL(
                "INSERT INTO nodes(id, name, link, networkType, isDefault, isActive) VALUES (41, ?, ?, ?, 0, 1)",
                arrayOf(label, URL, network)
            )
            if (issue == Issue.CONFLICTING_LABELS) {
                execSQL(
                    "INSERT INTO nodes(id, name, link, networkType, isDefault, isActive) VALUES (42, ?, ?, 0, 0, 0)",
                    arrayOf("Second exact label 雪", URL)
                )
            }
            assertEquals(if (issue == Issue.CONFLICTING_LABELS) 2L else 1L, scalar("SELECT COUNT(*) FROM nodes"))
            assertEquals(1L, scalar("SELECT COUNT(*) FROM nodes WHERE isActive = 1"))
            assertEquals("ok", text("PRAGMA integrity_check"))
            query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    private fun SupportSQLiteDatabase.seedParent(id: String, name: String, prefix: Int) {
        execSQL(
            "INSERT INTO chains(id, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans) VALUES (?, ?, ?, ?, 0, 0, 0)",
            arrayOf(id, name, "https://registry-fixture.invalid/icon", prefix)
        )
    }

    private fun assertWalletAndSecrets(sql: SupportSQLiteDatabase) {
        assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM meta_accounts"))
        val metaId = sql.scalar("SELECT id FROM meta_accounts")
        assertEquals(WALLET_NAME, sql.text("SELECT name FROM meta_accounts"))
        val expectedPublic = if (schema.databaseVersion == 27) legacyKeypair.publicKey else v2Substrate[KeyPairSchema.PublicKey]
        val expectedPrivate = if (schema.databaseVersion == 27) legacyKeypair.privateKey else v2Substrate[KeyPairSchema.PrivateKey]
        sql.query("SELECT substratePublicKey, substrateAccountId FROM meta_accounts").use {
            assertTrue(it.moveToFirst())
            assertArrayEquals(expectedPublic, it.getBlob(0))
            assertArrayEquals(expectedPublic.substrateAccountId(), it.getBlob(1))
            assertFalse(it.moveToNext())
        }
        val substrate = checkNotNull(SubstrateSecretStore(preferences).get(metaId, expectedPublic, CryptoType.SR25519, expectedPublic.substrateAccountId()))
        assertArrayEquals(expectedPublic, substrate[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PublicKey])
        assertArrayEquals(expectedPrivate, substrate[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PrivateKey])
        if (schema.databaseVersion == 27) {
            assertEquals(originalV1Secret, preferences.getDecryptedString("security_source_$address"))
            assertEquals(address, sql.text("SELECT address FROM legacy_users_recovery"))
            assertEquals(WALLET_NAME, sql.text("SELECT username FROM legacy_users_recovery"))
        } else {
            assertEquals(META_ID, metaId)
            val publicKey = v2Ethereum[KeyPairSchema.PublicKey]
            val ethereum = checkNotNull(EthereumSecretStore(preferences).get(metaId, publicKey, publicKey.ethereumAddressFromPublicKey()))
            assertArrayEquals(v2Ethereum[KeyPairSchema.PrivateKey], ethereum[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey])
            assertFalse(preferences.hasKey("$META_ID:ACCESS_SECRETS"))
        }
        assertEquals(SENTINEL_VALUE, preferences.getDecryptedString(SENTINEL_KEY))
        assertFalse(preferences.hasKeyWithPrefix(WalletSecretQuarantine.KEY_PREFIX))
        assertFalse(preferences.hasKeyWithPrefix(WalletPublicIdentityRecovery.KEY_PREFIX))
    }

    private fun <T> withSource(expectedVersion: Int = schema.databaseVersion, action: SupportSQLiteDatabase.() -> T): T {
        val callback = object : SupportSQLiteOpenHelper.Callback(expectedVersion) {
            override fun onCreate(db: SupportSQLiteDatabase) = error("Released fixture missing")
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected fixture setup upgrade")
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build()
        )
        return try { helper.writableDatabase.action() } finally { helper.close() }
    }

    private data class CellFingerprint(val type: String, val bytes: Long, val sha256: String)
    private data class RowFingerprint(val rowid: Long, val cells: List<CellFingerprint>)
    private data class TableFingerprint(val columns: List<String>, val rows: List<RowFingerprint>)
    private data class SqlFingerprint(val version: Int, val schema: TableFingerprint, val tables: Map<String, TableFingerprint>)

    private fun SupportSQLiteDatabase.sqlFingerprint(): SqlFingerprint {
        val names = query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return SqlFingerprint(version, tableFingerprint("sqlite_master"), names.associateWith { tableFingerprint(it) })
    }

    /** Read only bounded BLOB chunks, even for the >1MiB endpoint label. */
    private fun SupportSQLiteDatabase.tableFingerprint(table: String): TableFingerprint {
        val names = columns(table)
        val quotedTable = quote(table)
        val rows = query("SELECT rowid FROM $quotedTable ORDER BY rowid").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        return TableFingerprint(names, rows.map { rowid ->
            RowFingerprint(rowid, names.map { column ->
                val quotedColumn = quote(column)
                val (type, length) = query(
                    "SELECT typeof($quotedColumn), length(CAST($quotedColumn AS BLOB)) FROM $quotedTable WHERE rowid = ?", arrayOf(rowid)
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getString(0) to if (cursor.isNull(1)) -1L else cursor.getLong(1)
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var offset = 0L
                while (offset < length) {
                    val chunk = query(
                        "SELECT substr(CAST($quotedColumn AS BLOB), ?, ?) FROM $quotedTable WHERE rowid = ?",
                        arrayOf(offset + 1, CHUNK_BYTES, rowid)
                    ).use { cursor -> assertTrue(cursor.moveToFirst()); cursor.getBlob(0) }
                    assertTrue(chunk.isNotEmpty() && chunk.size <= CHUNK_BYTES)
                    digest.update(chunk)
                    offset += chunk.size
                }
                assertEquals(if (length < 0) 0L else length, offset)
                CellFingerprint(type, length, digest.digest().toHexString(withPrefix = false))
            })
        })
    }

    private fun SupportSQLiteDatabase.columns(table: String): List<String> = query("PRAGMA table_info(${quote(table)})").use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
    }
    private fun SupportSQLiteDatabase.scalar(sql: String): Long = query(sql).use {
        assertTrue(it.moveToFirst()); val value = it.getLong(0); assertFalse(it.moveToNext()); value
    }
    private fun SupportSQLiteDatabase.text(sql: String): String = query(sql).use {
        assertTrue(it.moveToFirst()); val value = it.getString(0); assertFalse(it.moveToNext()); value
    }
    private fun quote(identifier: String) = "`" + identifier.replace("`", "``") + "`"
    private fun HashMapEncryptedPreferences.snapshot(): Map<String, String> =
        keysWithPrefixes(setOf(""), 256, 1024, 65536, true).associateWith { checkNotNull(getDecryptedString(it)) }

    private class TrackingPreferences(private val delegate: HashMapEncryptedPreferences) : EncryptedPreferences by delegate {
        var reads = 0
        var writes = 0
        var casAttempts = 0
        fun reset() { reads = 0; writes = 0; casAttempts = 0 }
        override fun requireDurableStorageHealthy() { reads++; delegate.requireDurableStorageHealthy() }
        override fun hasKey(field: String): Boolean { reads++; return delegate.hasKey(field) }
        override fun hasKeyWithPrefix(prefix: String): Boolean { reads++; return delegate.hasKeyWithPrefix(prefix) }
        override fun getDecryptedString(field: String): String? { reads++; return delegate.getDecryptedString(field) }
        override fun getDecryptedStringSnapshot(field: String): EncryptedPreferenceSnapshot? { reads++; return delegate.getDecryptedStringSnapshot(field) }
        override fun keysWithPrefixes(prefixes: Set<String>, maxResultCount: Int, maxKeyBytes: Int, maxTotalKeyBytes: Int, failOnOversizedMatch: Boolean): Set<String> {
            reads++; return delegate.keysWithPrefixes(prefixes, maxResultCount, maxKeyBytes, maxTotalKeyBytes, failOnOversizedMatch)
        }
        override fun putEncryptedString(field: String, value: String) { writes++; delegate.putEncryptedString(field, value) }
        override fun removeKey(field: String) { writes++; delegate.removeKey(field) }
        override fun replaceEncryptedStringsDurably(valuesToPut: Map<String, String>, keysToRemove: Set<String>) {
            writes++; delegate.replaceEncryptedStringsDurably(valuesToPut, keysToRemove)
        }
        override fun replaceEncryptedStringsDurablyIfStatesMatch(expectedStates: Map<String, EncryptedPreferenceSnapshot?>, valuesToPut: Map<String, String>, keysToRemove: Set<String>, snapshotMoves: List<EncryptedPreferenceSnapshotMove>): Boolean {
            writes++; casAttempts++; return delegate.replaceEncryptedStringsDurablyIfStatesMatch(expectedStates, valuesToPut, keysToRemove, snapshotMoves)
        }
        override fun quarantineEncryptedStringDurably(sourceKey: String, quarantineKey: String, expectedSnapshot: EncryptedPreferenceSnapshot): Boolean {
            writes++; return delegate.quarantineEncryptedStringDurably(sourceKey, quarantineKey, expectedSnapshot)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}")
        fun cases(): List<Array<Any>> = listOf("1.9.13", "1.9.15", "2.0.0").flatMap { release ->
            Issue.entries.filter { it != Issue.AMBIGUOUS_ACALA || release == "2.0.0" }.map { arrayOf<Any>(release, it) }
        }
        private const val CHUNK_BYTES = 16_384
        private const val META_ID = 30L
        private const val KUSAMA = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
        private const val OLD_ACALA = "6e966844cfd406ac4d2e9ff66ae485d60677d8b514fe8cfefb54e19a7c12e6c3"
        private const val CORRECTED_ACALA = "fc41b9bd8ef8fe53d58c7ea67c794c7ec9a73daf05e6d54b14ff6342c99ba64c"
        private const val URL = "wss://availability-fixture.invalid/rpc?exact=Yes"
        private const val LABEL = "  Original endpoint 'label' 雪  "
        private const val WALLET_NAME = "Healthy endpoint-availability wallet"
        private const val SENTINEL_KEY = "endpoint-availability-unrelated"
        private const val SENTINEL_VALUE = "must remain exact"
    }
}
