package jp.co.soramitsu.app.root.domain

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Build
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
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.app.root.domain.keyloss.WalletPublicSnapshot
import jp.co.soramitsu.app.root.domain.keyloss.WalletPublicSnapshotReader
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
 * Shipped71 and current83 large-cache fixtures, plus reached malformed journals.
 * Actual startup gate, Android Keystore,
 * durable encrypted preferences, Room and both journal reconcilers. No fake Ready,
 * alternate database builder, Activity, full application DI or process death. Malformed controls
 * preserve the closed database and all preferences; they do not claim valid journal mutation replay.
 *
 * Execution requires an expressly owned disposable app UID. The runner must
 * bind the acknowledgement/serial to its actual adb target. Existing keystore
 * aliases are never deleted or recreated. Scoped public fixture files/preferences
 * remain for evidence capture; subsequent runs use fresh UUID namespaces.
 */
@RunWith(AndroidJUnit4::class)
class WalletLargeCacheStartupInstrumentedTest {
    @Test
    fun currentLargeCachePreservesReopenedShippedWalletAndReadOnlySnapshot() = runBlocking<Unit>(Dispatchers.IO) {
        exercise(largeCache = true)
    }

    @Test
    fun malformedWalletJournalPreservesReopenedShippedWalletAndStoredBytes() = runBlocking<Unit>(Dispatchers.IO) {
        exercise(largeCache = false, journal = ReachedJournal.WALLET)
    }

    @Test
    fun malformedTonJournalStopsBeforeWalletJournalAndPreservesStoredBytes() = runBlocking<Unit>(Dispatchers.IO) {
        exercise(largeCache = false, journal = ReachedJournal.TON)
    }

    @Test
    fun historicalLargeCachePreservesShippedWalletKeysAcrossTwoStartupOpens() = runBlocking<Unit>(Dispatchers.IO) {
        exercise(largeCache = false, historicalCache = true)
    }

    private enum class ReachedJournal { WALLET, TON }

    private suspend fun exercise(largeCache: Boolean, journal: ReachedJournal? = null, historicalCache: Boolean = false) {
        requireOwnedDisposableUid()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val existingAliases = aliasCertificates()
        val scope = FixtureContext(app, "large-cache-gate-${UUID.randomUUID()}")
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
        val fixture = scope.getDatabasePath("app.db")
        val asset = "keyloss/shipped-3.7.4-v71.db"
        val expectedHash = DB71_SHA
        assertFalse(fixture.exists())
        instrumentation.context.assets.open(asset).use { input -> fixture.outputStream().use(input::copyTo) }
        assertEquals(expectedHash, sha256(fixture.readBytes()))
        SQLiteDatabase.openDatabase(fixture.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            assertEquals(71, db.version)
            assertEquals(0L, db.scalar("SELECT COUNT(*) FROM meta_accounts"))
            val row = ContentValues().apply {
                put("id", META_ID)
                put("substratePublicKey", substratePublic)
                put("substrateCryptoType", "ED25519")
                put("substrateAccountId", substratePublic.substrateAccountId())
                put("ethereumPublicKey", ethereumPublic)
                put("ethereumAddress", ethereumPublic.ethereumAddressFromPublicKey())
                put("name", WALLET_NAME)
                put("isSelected", 1)
                put("position", 0)
                put("isBackedUp", 1)
                put("initialized", 1)
            }
            db.insertOrThrow("meta_accounts", null, row)
            assertEquals(0L, db.scalar("SELECT COUNT(*) FROM users"))
            db.execSQL("INSERT INTO users(address,username,publicKey,cryptoType,position) VALUES(?,?,?,0,3)",
                arrayOf("public-canonical-address", "Canonical identity 雪", substratePublic.hex()))
            if (historicalCache) {
                val chain = "a".repeat(64)
                val providers = "{\"public\":\"" + "a".repeat(1_048_565) + "\"}"
                assertEquals(1_048_578, providers.toByteArray().size)
                db.execSQL("INSERT INTO chains(id,name,icon,prefix,isEthereumBased,isTestNet,hasCrowdloans,supportStakingPool,isEthereumChain,isChainlinkProvider,supportNft,isUsesAppId) VALUES(?, 'Public network', 'https://public.invalid/icon',42,0,0,0,0,0,0,0,0)", arrayOf(chain))
                db.execSQL("INSERT INTO chain_assets(id,name,symbol,chainId,icon,staking,precision,purchaseProviders) VALUES('0','Public token','PUB',?,'https://public.invalid/token','unsupported',12,?)", arrayOf(chain, providers))
                db.execSQL("WITH RECURSIVE n(i) AS (VALUES(1) UNION ALL SELECT i+1 FROM n WHERE i < 262144) INSERT INTO chain_assets(id,name,symbol,chainId,icon,staking,precision) SELECT CAST(i AS TEXT),'Public token','PUB',?,'https://public.invalid/token','unsupported',12 FROM n", arrayOf(chain))
                db.execSQL("INSERT INTO chain_nodes VALUES(?, 'wss://public.invalid/custom','Saved public endpoint 雪',1,0)", arrayOf(chain))
                db.execSQL("INSERT INTO favorite_chains VALUES(42,?,1)", arrayOf(chain))
                db.execSQL("INSERT INTO assets(id,chainId,accountId,metaId,sortIndex,enabled,markedNotNeed,freeInPlanks) VALUES('0',?,?,42,19,0,0,'1')", arrayOf(chain, substratePublic.substrateAccountId()))
                db.execSQL("INSERT INTO address_book(address,name,chainId,created,id) VALUES('public-address','Contact 雪',?,123,7)", arrayOf(chain))
                assertEquals(262_145L, db.scalar("SELECT COUNT(*) FROM chain_assets"))
                assertEquals(1_048_578L, db.scalar("SELECT length(CAST(purchaseProviders AS BLOB)) FROM chain_assets WHERE id='0'"))
            }
            assertEquals("ok", db.rawQuery("PRAGMA integrity_check", null).use { assertTrue(it.moveToFirst()); it.getString(0) })
        }
        var firstPreferences: Map<String, Any?>? = null
        var firstWrappedPreferences: Map<String, Any?>? = null
        var firstPublicSnapshot: WalletPublicSnapshot? = null
        var rawDatabaseBeforeSecondOpen: String? = null
        try {
            repeat(2) { opening ->
                // Existing cases grow the cache after the first upgrade. The historical case
                // already has its oversized cache before Room's first migration.
                if (opening == 1) {
                    if (largeCache) assertTrue("Closed database exceeds old global cap", fixture.length() > 536_870_912L)
                    if (journal != null) {
                        fixturePreferences.putEncryptedString(WalletSecretMutationJournalStore.JOURNAL_KEY, "public malformed wallet journal")
                        if (journal == ReachedJournal.TON) {
                            fixturePreferences.putEncryptedString(TonConnectStorageKeys.MUTATION_JOURNAL_KEY, "public malformed TON journal")
                        }
                        // Preserve the deliberately malformed durable image, including both journals in the TON control.
                        firstPreferences = preferenceImage(raw)
                    }
                    rawDatabaseBeforeSecondOpen = sha256File(fixture)
                    assertEquals(firstPublicSnapshot, WalletPublicSnapshotReader(scope).read())
                    assertEquals(rawDatabaseBeforeSecondOpen, sha256File(fixture))
                }
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
                    val expected = if (opening == 1 && journal != null) {
                        WalletDatabaseStartupResult.RecoveryRequired(
                            if (journal == ReachedJournal.TON) "TON_CONNECT_JOURNAL_RECOVERY" else "WALLET_MUTATION_RECOVERY"
                        )
                    } else WalletDatabaseStartupResult.Ready
                    assertEquals("Actual gate opening=$opening", expected, gate.open())
                    assertTrue(database.isInitialized())
                    val room = database.value
                    val sql = room.openHelper.writableDatabase
                    assertEquals("Actual Room must open only the scoped fixture", fixture.canonicalPath, File(checkNotNull(sql.path)).canonicalPath)
                    assertEquals(EXPECTED_DATABASE_VERSION, sql.version)
                    val wallet = room.metaAccountDao().getMetaAccount(META_ID)
                    assertNotNull(wallet)
                    assertEquals(WALLET_NAME, wallet!!.name)
                    assertArrayEquals(substratePublic, wallet.substratePublicKey)
                    assertArrayEquals(ethereumPublic, wallet.ethereumPublicKey)
                    assertEquals(1, room.metaAccountDao().getMetaAccounts().size)
                    val substrateRead = checkNotNull(SubstrateSecretStore(preferences).get(
                        META_ID, substratePublic, CryptoType.ED25519, substratePublic.substrateAccountId()))
                    assertArrayEquals(substrate[KeyPairSchema.PrivateKey],
                        substrateRead[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PrivateKey])
                    val ethereumRead = checkNotNull(EthereumSecretStore(preferences).get(
                        META_ID, ethereumPublic, ethereumPublic.ethereumAddressFromPublicKey()))
                    assertArrayEquals(ethereum[KeyPairSchema.PrivateKey],
                        ethereumRead[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey])
                    assertFalse(preferences.hasKey("$META_ID:ACCESS_SECRETS"))
                    assertFalse(preferences.hasKeyWithPrefix(WalletSecretQuarantine.KEY_PREFIX))
                    assertFalse(preferences.hasKeyWithPrefix(WalletPublicIdentityRecovery.KEY_PREFIX))
                    assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM legacy_users_recovery"))
                    sql.query("SELECT address, username, publicKey, recoveryState FROM legacy_users_recovery").use {
                        assertTrue(it.moveToFirst())
                        assertEquals("public-canonical-address", it.getString(0))
                        assertEquals("Canonical identity 雪", it.getString(1))
                        assertEquals(substratePublic.hex(), it.getString(2))
                        assertEquals("PRESERVED_PENDING_REVIEW", it.getString(3))
                        assertFalse(it.moveToNext())
                    }
                    assertEquals(SENTINEL_VALUE, preferences.getDecryptedString(SENTINEL_KEY))
                    assertEquals(exactSentinelCiphertext, raw.getString(SENTINEL_KEY, null))
                    assertEquals("fixture selection 雪", raw.getString(UNRELATED_STRING, null))
                    assertEquals(73, raw.getInt(UNRELATED_INT, -1))
                    assertTrue(raw.getBoolean(UNRELATED_BOOL, false))
                    val preferencesNow = preferenceImage(raw)
                    val wrappedNow = preferenceImage(wrapped)
                    firstPreferences?.let { assertEquals("Second gate must not rewrite encrypted preferences", it, preferencesNow) }
                    firstWrappedPreferences?.let { assertEquals("Second gate must not replace the wrapped master key", it, wrappedNow) }
                    firstPreferences = preferencesNow
                    firstWrappedPreferences = wrappedNow
                    val publicSnapshot = WalletPublicSnapshotReader(scope).read()
                    assertTrue(publicSnapshot.wallets.any { it.name == WALLET_NAME && it.identities.isNotEmpty() })
                    firstPublicSnapshot?.let { assertEquals(it, publicSnapshot) }
                    firstPublicSnapshot = publicSnapshot
                    if (opening == 0 && largeCache) {
                        sql.beginTransaction()
                        try {
                            repeat(513) { index ->
                                sql.execSQL("INSERT INTO storage(storageKey,content,chainId) VALUES(?, '0x' || hex(zeroblob(524288)), 'public-large-cache-fixture')",
                                    arrayOf("large-cache-$index"))
                            }
                            sql.setTransactionSuccessful()
                        } finally { sql.endTransaction() }
                    }
                    assertEquals(if (largeCache) 513L else 0L, sql.scalar("SELECT COUNT(*) FROM storage WHERE chainId='public-large-cache-fixture'"))
                    assertEquals(if (largeCache) 513L else 0L, sql.scalar("SELECT COUNT(*) FROM storage WHERE chainId='public-large-cache-fixture' AND length(content)=1048578"))
                    if (historicalCache) {
                        val chain = "a".repeat(64)
                        val accountHex = substratePublic.substrateAccountId().hex()
                        assertEquals(262_145L, sql.scalar("SELECT COUNT(*) FROM chain_assets"))
                        assertEquals(1_048_578L, sql.scalar("SELECT length(CAST(purchaseProviders AS BLOB)) FROM chain_assets WHERE id='0'"))
                        assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM chain_assets WHERE id='0' AND purchaseProviders = '{\"public\":\"' || replace(hex(zeroblob(524282)), '0', 'a') || 'a\"}'"))
                        assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM assets WHERE id='0' AND chainId='$chain' AND accountId=X'$accountHex' AND metaId=42 AND enabled=0 AND sortIndex=19 AND markedNotNeed=0 AND freeInPlanks='1'"))
                        assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM favorite_chains WHERE metaId=42 AND chainId='$chain' AND isFavorite=1"))
                        assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM address_book WHERE id=7 AND chainId='$chain' AND created=123 AND name='Contact 雪' AND address='public-address'"))
                        assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM upgrade_entry_endpoint_archive_v1 WHERE chainId='$chain' AND name='Saved public endpoint 雪' AND url='wss://public.invalid/custom'"))
                    }
                    val pageCount = sql.scalar("PRAGMA page_count")
                    val pageSize = sql.scalar("PRAGMA page_size")
                    if (largeCache) assertTrue(pageCount > 536_870_912L / pageSize)
                } finally {
                    if (database.isInitialized()) database.value.close()
                }
                if (opening == 1) assertEquals(rawDatabaseBeforeSecondOpen, sha256File(fixture))
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
        check(args.getString("fearlessLargeCacheGateOwnedUid") == "exclusively-owned-disposable-app-uid") {
            "This suite requires an explicitly owned disposable app UID"
        }
        check(Build.VERSION.SDK_INT in setOf(26, 34, 36) && Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(args.getString("fearlessLargeCacheGateOwnedSerial")?.matches(Regex("emulator-[0-9]+")) == true) {
            "The external runner must bind its actual serial/profile/boot/package to this acknowledgement"
        }
        check(Process.myUid() == InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.uid)
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
            openOrCreateDatabase(name, mode, factory, null)

        override fun openOrCreateDatabase(
            name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            // Match ContextImpl: WAL is an open flag, not a mode change after opening.
            // Otherwise API26 briefly switches an existing WAL database to DELETE and back,
            // advancing SQLite's paired change counters even when every row stays unchanged.
            var flags = SQLiteDatabase.CREATE_IF_NECESSARY
            if (mode and Context.MODE_ENABLE_WRITE_AHEAD_LOGGING != 0) {
                flags = flags or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
            }
            if (mode and Context.MODE_NO_LOCALIZED_COLLATORS != 0) {
                flags = flags or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            }
            return SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, flags, errorHandler)
        }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("${namespace}_$name", mode)
    }

    private fun SQLiteDatabase.scalar(sql: String): Long = rawQuery(sql, null).use { check(it.moveToFirst()); it.getLong(0) }
    private fun SupportSQLiteDatabase.scalar(sql: String): Long = query(sql).use { check(it.moveToFirst()); it.getLong(0) }
    private fun preferenceImage(preferences: SharedPreferences): Map<String, Any?> =
        preferences.all.mapValues { (_, value) ->
            when (value) {
                null, is String, is Boolean, is Int, is Long, is Float -> value
                is Set<*> -> value.map { checkNotNull(it as? String) }.toSet()
                else -> error("Unexpected SharedPreferences value type")
            }
        }

    private fun aliasCertificates(): Map<String, String> {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return listOf("key_alias", "key_alias_v2").filter(store::containsAlias).associateWith {
            sha256(checkNotNull(store.getCertificate(it)).encoded)
        }
    }
    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val bytes = ByteArray(65_536)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().hex()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }

    companion object {
        // Exact current schema pinned for this source-bound historical fixture.
        private const val EXPECTED_DATABASE_VERSION = 83
        private const val META_ID = 42L
        private const val WALLET_NAME = "Shipped large cache fixture 雪"
        private const val SENTINEL_KEY = "gate_fixture_unrelated_encrypted"
        private const val SENTINEL_VALUE = "public synthetic unrelated preference"
        private const val UNRELATED_STRING = "gate_fixture_unrelated_string"
        private const val UNRELATED_INT = "gate_fixture_unrelated_integer"
        private const val UNRELATED_BOOL = "gate_fixture_unrelated_boolean"
        private const val SECRET_ASSET = "keyloss/shipped-3.7.4-public-ed25519-evm.hex"
        private const val SECRET_SHA = "d6db2bb43a365a4eef35b700fc6bbda674a573cab88e528bb4c92e96a7162dfe"
        private const val DB71_SHA = "ca0c5efa69b56e46bba3cd218194e443133c83dd03ff044b972af65657d075a9"
    }
}
