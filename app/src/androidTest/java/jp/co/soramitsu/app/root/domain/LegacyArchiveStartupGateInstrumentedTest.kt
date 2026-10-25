package jp.co.soramitsu.app.root.domain

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.Lazy
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidation
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidation
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.TonMigration
import jp.co.soramitsu.coredb.migrations.WalletOrphanSecretInventory
import jp.co.soramitsu.coredb.migrations.WalletQuarantineRevalidation
import jp.co.soramitsu.tonconnect.impl.di.TonConnectModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actual constructor-wired startup gate, Room migrations, Android Keystore,
 * durable encrypted preferences and both journal reconcilers. No fake Ready,
 * alternate database builder, Activity, full application DI or process death.
 *
 * Execution requires an expressly owned disposable app UID. The runner must
 * bind the acknowledgement/serial to its actual adb target. Existing keystore
 * aliases are never deleted or recreated. Scoped synthetic files/preferences
 * remain for evidence capture; subsequent runs use fresh UUID namespaces.
 */
@RunWith(AndroidJUnit4::class)
class LegacyArchiveStartupGateInstrumentedTest {
    @Test
    fun unknownEndpointVersion30ArchiveStillReachesActualReadyWithReadableWallet() = runBlocking(Dispatchers.IO) {
        exercise(version = 30, unresolved = true)
    }

    @Test
    fun knownEndpointVersion30HealthyControlReachesActualReadyAndKeepsCurrentNode() = runBlocking(Dispatchers.IO) {
        exercise(version = 30, unresolved = false)
    }

    @Test
    fun unknownPublicKeyVersion71ArchiveStillReachesActualReadyWithReadableWallet() = runBlocking(Dispatchers.IO) {
        exercise(version = 71, unresolved = true)
    }

    @Test
    fun canonicalUsersVersion71HealthyControlReachesActualReadyAndKeepsRecoveryLedger() = runBlocking(Dispatchers.IO) {
        exercise(version = 71, unresolved = false)
    }

    private suspend fun exercise(version: Int, unresolved: Boolean) {
        requireOwnedDisposableUid()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val existingAliases = aliasCertificates()
        val scope = FixtureContext(app, "archive-gate-${UUID.randomUUID()}")
        val raw = scope.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
        assertTrue(raw.all.isEmpty())
        // Authenticate/create only this fresh fixture's wrapped key before a
        // wallet database or protected payload exists. Never reset an alias.
        val initialEncryption = EncryptionUtil(scope)
        initialEncryption.getPrerenceAesKey()
        val fixturePreferences = EncryptedPreferencesImpl(PreferencesImpl(raw), initialEncryption)
        val vectorBytes = instrumentation.context.assets.open(SECRET_ASSET).use { it.readBytes() }
        assertEquals(SECRET_SHA, sha256(vectorBytes))
        val encoded = vectorBytes.toString(Charsets.UTF_8).trim()
        val schema = TonMigration.MetaAccountSecretsV69
        val secret = schema.read(encoded)
        val substrate = secret[schema.SubstrateKeypair]
        val ethereum = checkNotNull(secret[schema.EthereumKeypair])
        val substratePublic = substrate[KeyPairSchema.PublicKey]
        val ethereumPublic = ethereum[KeyPairSchema.PublicKey]
        fixturePreferences.putEncryptedString("$META_ID:ACCESS_SECRETS", encoded)
        fixturePreferences.putEncryptedString(SENTINEL_KEY, SENTINEL_VALUE)
        assertTrue(raw.edit().putString(UNRELATED_STRING, "fixture selection 雪")
            .putInt(UNRELATED_INT, 73).putBoolean(UNRELATED_BOOL, true).commit())
        val exactSentinelCiphertext = raw.getString(SENTINEL_KEY, null)
        assertNotNull(exactSentinelCiphertext)
        assertFalse(exactSentinelCiphertext == SENTINEL_VALUE)
        val wrapped = scope.getSharedPreferences("key_alias", Context.MODE_PRIVATE)
        val archiveTable = if (version == 30) "nodes" else "users"
        val fixture = scope.getDatabasePath("app.db")
        val asset = if (version == 30) "keyloss/shipped-2.0.0-v30.db" else "keyloss/shipped-3.7.4-v71.db"
        val expectedHash = if (version == 30) DB30_SHA else DB71_SHA
        assertFalse(fixture.exists())
        instrumentation.context.assets.open(asset).use { input -> fixture.outputStream().use(input::copyTo) }
        assertEquals(expectedHash, sha256(fixture.readBytes()))
        val beforeArchive = SQLiteDatabase.openDatabase(fixture.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            assertEquals(version, db.version)
            assertEquals(0L, db.scalar("SELECT COUNT(*) FROM meta_accounts"))
            val row = ContentValues().apply {
                put("id", META_ID)
                put("substratePublicKey", substratePublic)
                put("substrateCryptoType", "SR25519")
                put("substrateAccountId", substratePublic.substrateAccountId())
                put("ethereumPublicKey", ethereumPublic)
                put("ethereumAddress", ethereumPublic.ethereumAddressFromPublicKey())
                put("name", WALLET_NAME)
                put("isSelected", 1)
                put("position", 0)
                if (version == 71) { put("isBackedUp", 1); put("initialized", 1) }
            }
            db.insertOrThrow("meta_accounts", null, row)
            if (version == 30) {
                assertEquals(0L, db.scalar("SELECT COUNT(*) FROM nodes"))
                db.execSQL("INSERT INTO nodes(id,name,link,networkType,isDefault,isActive) VALUES(41,?,?,?,0,1)",
                    arrayOf(NODE_LABEL, NODE_URL, if (unresolved) 15 else 0))
            } else {
                assertEquals(0L, db.scalar("SELECT COUNT(*) FROM users"))
                if (unresolved) {
                    // Exact same malformed ordinary public-identity family as
                    // the core-db availability baseline; no RENAME COLUMN on26.
                    db.execSQL("DROP TABLE users")
                    db.execSQL("CREATE TABLE users(address TEXT NOT NULL PRIMARY KEY, username TEXT, " +
                        "unknownPublicKey BLOB, cryptoType INTEGER, position INTEGER)")
                    db.execSQL("INSERT INTO users(rowid,address,username,unknownPublicKey,cryptoType,position) " +
                        "VALUES(17,?,?,?,0,3)", arrayOf("public-archive-address", "Raw identity 雪", byteArrayOf(0, 1, -1)))
                } else {
                    db.execSQL("INSERT INTO users(address,username,publicKey,cryptoType,position) VALUES(?,?,?,0,3)",
                        arrayOf("public-canonical-address", "Canonical identity 雪", substratePublic.hex()))
                }
            }
            assertEquals("ok", db.rawQuery("PRAGMA integrity_check", null).use { assertTrue(it.moveToFirst()); it.getString(0) })
            fingerprint(archiveTable) { sql -> db.rawQuery(sql, null) }
        }
        var firstPreferences: String? = null
        var firstWrappedPreferences: String? = null
        try {
            repeat(2) { opening ->
                // New readers and Room object each time, same Android process.
                val encryption = EncryptionUtil(scope)
                val preferences = EncryptedPreferencesImpl(PreferencesImpl(raw), encryption)
                val database = lazy { productionDatabase(scope, preferences) }
                val gate = WalletDatabaseStartupGate(
                    appDatabase = Lazy { database.value },
                    encryptionUtil = encryption,
                    tonConnectRepository = Lazy { TonConnectModule().provideTonConnectRepo(database.value.tonConnectDao(), preferences) },
                    walletSecretMutationCoordinator = Lazy {
                        WalletSecretMutationCoordinator(database.value, database.value.metaAccountDao(),
                            WalletSecretMutationJournalStore(preferences), preferences)
                    },
                    walletSecretAccessGuard = Lazy { WalletSecretAccessGuard(preferences) },
                    walletOrphanSecretInventory = Lazy { WalletOrphanSecretInventory(preferences) },
                    walletQuarantineRevalidation = Lazy { WalletQuarantineRevalidation(preferences) }
                )
                try {
                    assertEquals("Actual gate opening=$opening", WalletDatabaseStartupResult.Ready, gate.open())
                    assertTrue(database.isInitialized())
                    val room = database.value
                    val sql = room.openHelper.writableDatabase
                    assertEquals("Actual Room must open only the scoped fixture", fixture.canonicalPath, File(sql.path).canonicalPath)
                    assertEquals(EXPECTED_DATABASE_VERSION, sql.version)
                    val wallet = room.metaAccountDao().getMetaAccount(META_ID)
                    assertNotNull(wallet)
                    assertEquals(WALLET_NAME, wallet!!.name)
                    assertArrayEquals(substratePublic, wallet.substratePublicKey)
                    assertArrayEquals(ethereumPublic, wallet.ethereumPublicKey)
                    assertEquals(1, room.metaAccountDao().getMetaAccounts().size)
                    val substrateRead = checkNotNull(SubstrateSecretStore(preferences).get(
                        META_ID, substratePublic, CryptoType.SR25519, substratePublic.substrateAccountId()))
                    assertArrayEquals(substrate[KeyPairSchema.PrivateKey],
                        substrateRead[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PrivateKey])
                    val ethereumRead = checkNotNull(EthereumSecretStore(preferences).get(
                        META_ID, ethereumPublic, ethereumPublic.ethereumAddressFromPublicKey()))
                    assertArrayEquals(ethereum[KeyPairSchema.PrivateKey],
                        ethereumRead[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey])
                    assertFalse(preferences.hasKey("$META_ID:ACCESS_SECRETS"))
                    assertFalse(preferences.hasKeyWithPrefix(WalletSecretQuarantine.KEY_PREFIX))
                    assertFalse(preferences.hasKeyWithPrefix(WalletPublicIdentityRecovery.KEY_PREFIX))
                    if (version == 30 || unresolved) {
                        assertEquals(beforeArchive, fingerprint(archiveTable) { sql.query(it) })
                    }
                    if (version == 30) {
                        if (unresolved) {
                            assertEquals(0L, sql.scalar("SELECT COUNT(*) FROM chain_nodes WHERE url = '$NODE_URL'"))
                        } else {
                            val node = room.chainDao().getNode(KUSAMA, NODE_URL)
                            assertEquals(NODE_LABEL, node.name)
                            assertTrue(node.isActive)
                            assertFalse(node.isDefault)
                        }
                    } else if (unresolved) {
                        assertEquals(0L, sql.scalar("SELECT COUNT(*) FROM legacy_users_recovery"))
                    } else {
                        assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM legacy_users_recovery"))
                        sql.query("SELECT address, username, publicKey, recoveryState FROM legacy_users_recovery").use {
                            assertTrue(it.moveToFirst())
                            assertEquals("public-canonical-address", it.getString(0))
                            assertEquals("Canonical identity 雪", it.getString(1))
                            assertEquals(substratePublic.hex(), it.getString(2))
                            assertEquals("PRESERVED_PENDING_REVIEW", it.getString(3))
                            assertFalse(it.moveToNext())
                        }
                    }
                    assertEquals(SENTINEL_VALUE, preferences.getDecryptedString(SENTINEL_KEY))
                    assertEquals(exactSentinelCiphertext, raw.getString(SENTINEL_KEY, null))
                    assertEquals("fixture selection 雪", raw.getString(UNRELATED_STRING, null))
                    assertEquals(73, raw.getInt(UNRELATED_INT, -1))
                    assertTrue(raw.getBoolean(UNRELATED_BOOL, false))
                    val preferencesNow = preferenceFingerprint(raw)
                    val wrappedNow = preferenceFingerprint(wrapped)
                    firstPreferences?.let { assertEquals("Second gate must not rewrite encrypted preferences", it, preferencesNow) }
                    firstWrappedPreferences?.let { assertEquals("Second gate must not replace the wrapped master key", it, wrappedNow) }
                    firstPreferences = preferencesNow
                    firstWrappedPreferences = wrappedNow
                } finally {
                    if (database.isInitialized()) database.value.close()
                }
            }
        } finally {
            val afterAliases = aliasCertificates()
            existingAliases.forEach { (alias, fingerprint) -> assertEquals("Existing alias changed: $alias", fingerprint, afterAliases[alias]) }
            // Deliberately retain this test's scoped SQLite/preferences for
            // external evidence capture; never clear global app state/aliases.
        }
    }

    /** Cross-module internal factory only; exact real signature, default real validators. */
    private fun productionDatabase(context: Context, preferences: EncryptedPreferences): AppDatabase {
        val parameterTypes = listOf(Context::class.java, String::class.java, SecretStoreV1::class.java,
            SecretStoreV2::class.java, EncryptedPreferences::class.java, SubstrateSecretStore::class.java,
            EthereumSecretStore::class.java, ChainAccountSecretValidation::class.java, WalletRootSecretValidation::class.java)
        val companion = AppDatabase.Companion
        val factory = companion.javaClass.declaredMethods.single {
            it.name.substringBefore('$') == "create" && it.returnType == AppDatabase::class.java &&
                it.parameterTypes.toList() == parameterTypes
        }.apply { isAccessible = true }
        return try {
            factory.invoke(companion, context, "app.db", SecretStoreV1Impl(preferences), SecretStoreV2(preferences),
                preferences, SubstrateSecretStore(preferences), EthereumSecretStore(preferences),
                ChainAccountSecretValidator, WalletRootSecretValidator) as AppDatabase
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }
    }

    private fun requireOwnedDisposableUid() {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("fearlessArchiveGateOwnedUid") == "exclusively-owned-disposable-app-uid") {
            "This suite requires an explicitly owned disposable app UID; it must never run on a personal wallet install"
        }
        check(Build.VERSION.SDK_INT in setOf(26, 34, 36) && Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(args.getString("fearlessArchiveGateOwnedSerial")?.matches(Regex("emulator-[0-9]+")) == true) {
            "Runner must bind the owned serial argument to its actual adb target"
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val actualAvd = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("getprop ro.boot.qemu.avd_name")
        ).bufferedReader().use { it.readText().trim() }
        check(actualAvd.isNotEmpty() && args.getString("fearlessArchiveGateOwnedAvd") == actualAvd)
        check(args.getString("fearlessArchiveGateUid") == Process.myUid().toString())
        check(instrumentation.targetContext.packageName == "jp.co.soramitsu.fearless.debug")
        check(Process.myUid() == instrumentation.targetContext.applicationInfo.uid)
    }

    private class FixtureContext(base: Context, val namespace: String) : ContextWrapper(base) {
        private val directory = File(base.cacheDir, namespace).apply { check(mkdir()) }
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String): File {
            val target = File(directory, "app.db")
            check(name == "app.db" || name == target.absolutePath)
            return target
        }
        // SQLiteOpenHelper calls the Context database-opening methods; merely
        // overriding getDatabasePath would still delegate those calls to base.
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory).apply {
                if (mode and Context.MODE_ENABLE_WRITE_AHEAD_LOGGING != 0) enableWriteAheadLogging()
            }
        override fun openOrCreateDatabase(
            name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, errorHandler).apply {
            if (mode and Context.MODE_ENABLE_WRITE_AHEAD_LOGGING != 0) enableWriteAheadLogging()
        }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("${namespace}_$name", mode)
    }

    private fun fingerprint(table: String, query: (String) -> Cursor): List<String> {
        require(table in setOf("nodes", "users"))
        val columns = query("PRAGMA main.table_info(`$table`)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
        check(columns.isNotEmpty() && columns.all { it.all { c -> c.isLetterOrDigit() || c == '_' } })
        val shape = query("SELECT type,name,tbl_name,sql FROM main.sqlite_master WHERE tbl_name='$table' ORDER BY type,name").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0..3).joinToString("|") { if (cursor.isNull(it)) "NULL" else cursor.getString(it) }) }
        }
        val rowids = query("SELECT rowid FROM main.`$table` ORDER BY rowid LIMIT 129").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        check(rowids.size <= 128)
        return shape + rowids.map { rowid ->
            "$rowid:" + columns.joinToString("|") { column ->
                query("SELECT typeof(`$column`),length(CAST(`$column` AS BLOB))," +
                    "hex(substr(CAST(`$column` AS BLOB),1,16384)) FROM main.`$table` WHERE rowid=$rowid").use {
                    check(it.moveToFirst())
                    val length = if (it.isNull(1)) -1 else it.getInt(1)
                    check(length <= 16384)
                    "$column:${it.getString(0)}:$length:${it.getString(2)}"
                }
            }
        }
    }

    private fun SQLiteDatabase.scalar(sql: String): Long = rawQuery(sql, null).use { check(it.moveToFirst()); it.getLong(0) }
    private fun SupportSQLiteDatabase.scalar(sql: String): Long = query(sql).use { check(it.moveToFirst()); it.getLong(0) }
    private fun preferenceFingerprint(preferences: SharedPreferences): String = sha256(
        preferences.all.toSortedMap().entries.joinToString("\n") { (key, value) ->
            "$key:${value?.javaClass?.name}:${if (value is Set<*>) value.map { it.toString() }.sorted() else value}"
        }.toByteArray(Charsets.UTF_8)
    )
    private fun aliasCertificates(): Map<String, String> {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return listOf("key_alias", "key_alias_v2").filter(store::containsAlias).associateWith {
            sha256(checkNotNull(store.getCertificate(it)).encoded)
        }
    }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }

    companion object {
        // Exact current schema pinned for this source-bound historical fixture.
        private const val EXPECTED_DATABASE_VERSION = 84
        private const val META_ID = 42L
        private const val WALLET_NAME = "Startup archive fixture 雪"
        private const val NODE_LABEL = "Retained endpoint 雪"
        private const val NODE_URL = "wss://startup-archive.invalid/rpc?case=Exact"
        private const val KUSAMA = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
        private const val SENTINEL_KEY = "gate_fixture_unrelated_encrypted"
        private const val SENTINEL_VALUE = "public synthetic unrelated preference"
        private const val UNRELATED_STRING = "gate_fixture_unrelated_string"
        private const val UNRELATED_INT = "gate_fixture_unrelated_integer"
        private const val UNRELATED_BOOL = "gate_fixture_unrelated_boolean"
        private const val SECRET_ASSET = "keyloss/startup-gate-synthetic-six-field.hex"
        private const val SECRET_SHA = "fb30cf2e9c4918c22f0a269afc715802555c5b3ef97c794c82c375725773e51c"
        private const val DB30_SHA = "c9b071ca0037caec64236de73ecb2bed69321743379f8020ebf8f824319b5cf2"
        private const val DB71_SHA = "ca0c5efa69b56e46bba3cd218194e443133c83dd03ff044b972af65657d075a9"
    }
}
