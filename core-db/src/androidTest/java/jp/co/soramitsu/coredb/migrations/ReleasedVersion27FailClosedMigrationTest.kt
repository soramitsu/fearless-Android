package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString as toPlainHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Adversarial counterpart to [ReleasedSchemaUpgradeMatrixTest]. This includes
 * a real released-v27 legacy `users` row and V1 secret that V2 would otherwise
 * copy to a new preference key. A corrupted child relationship must stop
 * before that external write; neither rollback nor startup may erase or
 * partially migrate either store.
 */
class ReleasedVersion27FailClosedMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @get:Rule
    val schemaHelper = MigrationTestHelper(
        instrumentation,
        LEGACY_DATABASE_CANONICAL_NAME,
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun deleteIsolatedDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun orphanChainAccountRejectsFullUpgradeWithoutWipeOrSecretMutation() =
        runBlocking {
            context.deleteDatabase(DATABASE_NAME)
            val legacySeed = deterministicSeed(offset = 17)
            val legacyKeypair = SubstrateKeypairFactory.generate(
                encryptionType = EncryptionType.SR25519,
                seed = legacySeed,
                junctions = emptyList()
            )
            val legacyAddress = legacyKeypair.publicKey.toAddress(0)
            val orphanKeypair = SubstrateKeypairFactory.generate(
                encryptionType = EncryptionType.SR25519,
                seed = deterministicSeed(offset = 81),
                junctions = emptyList()
            )
            val orphanAccountId =
                orphanKeypair.publicKey.substrateAccountId()
            val preferences = HashMapEncryptedPreferences()
            val storeV1 = SecretStoreV1Impl(preferences)
            storeV1.saveSecuritySource(
                legacyAddress,
                SecuritySource.Unspecified(legacyKeypair)
            )
            preferences.putEncryptedString(SENTINEL_KEY, SENTINEL_VALUE)
            assertArrayEquals(
                legacyKeypair.publicKey,
                checkNotNull(storeV1.getSecuritySource(legacyAddress))
                    .keypair.publicKey
            )
            val expectedPreferenceState =
                preferences.exactPreferenceState()
            assertEquals(2, expectedPreferenceState.size)
            assertTrue(
                expectedPreferenceState.containsKey(
                    "security_source_$legacyAddress"
                )
            )

            schemaHelper.createDatabase(
                DATABASE_NAME,
                START_VERSION
            ).use { database ->
                database.execSQL(
                    """
                    INSERT INTO users(
                        address, username, publicKey, cryptoType,
                        position, networkType
                    ) VALUES(?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(
                        legacyAddress,
                        "Legacy V1 safety wallet",
                        legacyKeypair.publicKey.toPlainHexString(
                            withPrefix = false
                        ),
                        CryptoType.SR25519.ordinal,
                        0,
                        0
                    )
                )
                database.execSQL(
                    """
                    INSERT INTO meta_accounts(
                        id, substratePublicKey, substrateCryptoType,
                        substrateAccountId, ethereumPublicKey, ethereumAddress,
                        name, isSelected, position
                    ) VALUES(?, ?, ?, ?, NULL, NULL, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(
                        META_ID,
                        orphanKeypair.publicKey,
                        CryptoType.SR25519.name,
                        orphanAccountId,
                        "Orphan safety wallet",
                        1,
                        0
                    )
                )
                database.execSQL("PRAGMA foreign_keys = OFF")
                database.execSQL(
                    """
                    INSERT INTO chain_accounts(
                        metaId, chainId, publicKey, accountId, cryptoType
                    ) VALUES(?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(
                        META_ID,
                        MISSING_CHAIN_ID,
                        orphanKeypair.publicKey,
                        orphanAccountId,
                        CryptoType.SR25519.name
                    )
                )
                database.query("PRAGMA foreign_key_check").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                }
            }

            val roomDatabase = AppDatabase.create(
                context = context,
                databaseName = DATABASE_NAME,
                storeV1 = storeV1,
                storeV2 = SecretStoreV2(preferences),
                encryptedPreferences = preferences,
                substrateSecretStore = SubstrateSecretStore(preferences),
                ethereumSecretStore = EthereumSecretStore(preferences)
            )

            val failure = try {
                roomDatabase.openHelper.writableDatabase
                null
            } catch (caught: Throwable) {
                caught
            } finally {
                roomDatabase.close()
            }

            assertNotNull(
                "The corrupted v27 database unexpectedly upgraded",
                failure
            )
            val integrityFailure = checkNotNull(
                checkNotNull(failure)
                    .findCause<WalletPublicIdentityIntegrityException>()
            ) {
                "Expected the version-28 FK preflight to reject the orphan"
            }
            assertTrue(
                integrityFailure.message.orEmpty().contains("Version 28")
            )
            assertTrue(
                integrityFailure.message.orEmpty().contains("foreign-key")
            )
            assertEquals(
                expectedPreferenceState,
                preferences.exactPreferenceState()
            )
            assertEquals(
                SENTINEL_VALUE,
                preferences.getDecryptedString(SENTINEL_KEY)
            )
            assertFalse(
                preferences.hasKeyWithPrefix(
                    WalletSecretQuarantine.KEY_PREFIX
                )
            )
            assertFalse(
                preferences.hasKeyWithPrefix(
                    WalletPublicIdentityRecovery.KEY_PREFIX
                )
            )

            SQLiteDatabase.openDatabase(
                context.getDatabasePath(DATABASE_NAME).absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { database ->
                assertEquals(START_VERSION, database.version)
                assertEquals(
                    1L,
                    database.singleLong("SELECT COUNT(*) FROM users")
                )
                assertEquals(
                    legacyKeypair.publicKey.toPlainHexString(
                        withPrefix = false
                    ),
                    database.singleString("SELECT publicKey FROM users")
                )
                assertEquals(
                    1L,
                    database.singleLong("SELECT COUNT(*) FROM meta_accounts")
                )
                assertEquals(
                    1L,
                    database.singleLong(
                        "SELECT COUNT(*) FROM chain_accounts"
                    )
                )
                assertEquals(
                    MISSING_CHAIN_ID,
                    database.singleString(
                        "SELECT chainId FROM chain_accounts"
                    )
                )
                assertEquals(
                    0L,
                    database.singleLong("SELECT COUNT(*) FROM chains")
                )
                database.rawQuery(
                    "PRAGMA foreign_key_check",
                    null
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                }
            }
        }

    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var current: Throwable? = this
        repeat(MAX_CAUSE_DEPTH) {
            if (current is T) return current
            current = current?.cause ?: return null
        }
        return null
    }

    private fun HashMapEncryptedPreferences.exactPreferenceState():
        Map<String, String> {
        // The empty prefix deliberately enumerates the complete in-memory test
        // store; strict fixture-sized bounds turn any added key into a visible
        // map difference rather than checking only anticipated destinations.
        val keys = keysWithPrefixes(
            prefixes = setOf(""),
            maxResultCount = MAX_PREFERENCE_KEYS,
            maxKeyBytes = MAX_PREFERENCE_KEY_BYTES,
            maxTotalKeyBytes = MAX_TOTAL_PREFERENCE_KEY_BYTES,
            failOnOversizedMatch = true
        )
        return keys.sorted().associateWith { key ->
            checkNotNull(getDecryptedString(key))
        }
    }

    private fun SQLiteDatabase.singleLong(sql: String): Long {
        return rawQuery(sql, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val value = cursor.getLong(0)
            assertFalse(cursor.moveToNext())
            value
        }
    }

    private fun SQLiteDatabase.singleString(sql: String): String {
        return rawQuery(sql, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val value = cursor.getString(0)
            assertFalse(cursor.moveToNext())
            value
        }
    }

    private companion object {
        const val LEGACY_DATABASE_CANONICAL_NAME =
            "jp.co.soramitsu.core_db.AppDatabase"
        const val DATABASE_NAME = "released-schema-27-orphan-fails-closed"
        const val START_VERSION = 27
        const val META_ID = 1L
        const val MISSING_CHAIN_ID = "missing-chain"
        const val SENTINEL_KEY = "unrelated-preference"
        const val SENTINEL_VALUE = "must-survive"
        const val MAX_CAUSE_DEPTH = 16
        const val MAX_PREFERENCE_KEYS = 32
        const val MAX_PREFERENCE_KEY_BYTES = 1_024
        const val MAX_TOTAL_PREFERENCE_KEY_BYTES = 32_768

        fun deterministicSeed(offset: Int) =
            ByteArray(32) { index -> (index + offset).toByte() }
    }
}
