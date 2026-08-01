package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.WalletMetaAccountScaleLayout
import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV200DatabaseFixture
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Opens an immutable Room-v30 fixture synthesized from DDL extracted from the
 * official Fearless Wallet v2.0.0 (51) APK through the complete production
 * 30 -> 77 migration chain.
 *
 * The wallet material is deterministic synthetic data encoded by the exact
 * historical six-field schema. In particular, it has no trailing TON option.
 */
class ReleasedV200MigrationSafetyTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun deleteTestDatabases() {
        DATABASE_NAMES.forEach(context::deleteDatabase)
    }

    @Test
    fun frozenSixFieldSecretMatchesReleaseSchemaAndRoundTripsByteExactly() {
        ReleasedV200DatabaseFixture.verifyProvenance(context)
        val encoded = ReleasedV200DatabaseFixture.legacySecretHex(context)
        val schema = TonMigration.MetaAccountSecretsV69
        assertEquals(
            WalletMetaAccountScaleLayout.LEGACY_V69,
            WalletSecretScalePreflight.requireMetaAccountV2OrLegacyV69(encoded)
        )
        val decoded = schema.read(encoded)

        assertEquals(encoded, decoded.toHexString())
        assertArrayEquals(ENTROPY, decoded[schema.Entropy])
        assertArrayEquals(SEED, decoded[schema.Seed])
        assertEquals(
            SUBSTRATE_DERIVATION_PATH,
            decoded[schema.SubstrateDerivationPath]
        )
        assertEquals(
            ETHEREUM_DERIVATION_PATH,
            decoded[schema.EthereumDerivationPath]
        )

        val substrateKeypair = decoded[schema.SubstrateKeypair]
        assertArrayEquals(
            SUBSTRATE_KEYPAIR.privateKey,
            substrateKeypair[KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(
            SUBSTRATE_KEYPAIR.publicKey,
            substrateKeypair[KeyPairSchema.PublicKey]
        )
        assertArrayEquals(
            (SUBSTRATE_KEYPAIR as Sr25519Keypair).nonce,
            substrateKeypair[KeyPairSchema.Nonce]
        )

        val ethereumKeypair = requireNotNull(decoded[schema.EthereumKeypair])
        assertArrayEquals(
            ETHEREUM_KEYPAIR.privateKey,
            ethereumKeypair[KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(
            ETHEREUM_KEYPAIR.publicKey,
            ethereumKeypair[KeyPairSchema.PublicKey]
        )
        assertNull(ethereumKeypair[KeyPairSchema.Nonce])
    }

    @Test
    fun officialVersion30OpenPreservesSigningMaterialAcrossTwoLaunches() =
        runBlocking {
            val preferences = HashMapEncryptedPreferences().apply {
                putEncryptedString(
                    legacySecretKey(META_ID),
                    ReleasedV200DatabaseFixture.legacySecretHex(context)
                )
            }
            createVersion30Database(PRESERVATION_DATABASE)

            openProductionDatabase(PRESERVATION_DATABASE, preferences).useDatabase {
                assertMigratedDatabaseAndSecrets(it, preferences)
            }

            assertFalse(preferences.hasKey(legacySecretKey(META_ID)))
            assertFalse(
                preferences.hasKey(
                    WalletSecretQuarantine.keyFor(legacySecretKey(META_ID))
                )
            )

            // A fresh process launch must open the already-migrated database
            // without replaying, deleting, or changing any signing material.
            openProductionDatabase(PRESERVATION_DATABASE, preferences).useDatabase {
                assertMigratedDatabaseAndSecrets(it, preferences)
            }
        }

    @Test
    fun truncatedOfficialSixFieldSecretEntersRecoveryWithoutBlockingStartup() =
        runBlocking {
            val exactTruncatedSecret =
                ReleasedV200DatabaseFixture.legacySecretHex(context).dropLast(2)
            val preferences = HashMapEncryptedPreferences().apply {
                putEncryptedString(
                    legacySecretKey(META_ID),
                    exactTruncatedSecret
                )
            }
            createVersion30Database(TRUNCATED_DATABASE)

            openProductionDatabase(TRUNCATED_DATABASE, preferences).useDatabase {
                assertEquals(77, it.openHelper.writableDatabase.version)
            }

            assertFalse(preferences.hasKey(legacySecretKey(META_ID)))
            assertEquals(
                exactTruncatedSecret,
                preferences.getDecryptedString(
                    WalletSecretQuarantine.keyFor(legacySecretKey(META_ID))
                )
            )
            assertFalse(preferences.hasKey(substrateSecretKey(META_ID)))
            assertFalse(preferences.hasKey(ethereumSecretKey(META_ID)))

            // Recovery state is stable and must not become a migration loop.
            openProductionDatabase(TRUNCATED_DATABASE, preferences).useDatabase {
                assertEquals(77, it.openHelper.writableDatabase.version)
            }
        }

    private fun createVersion30Database(databaseName: String) {
        ReleasedV200DatabaseFixture.install(context, databaseName)
        val callback = object :
            SupportSQLiteOpenHelper.Callback(
                ReleasedV200DatabaseFixture.DATABASE_VERSION
            ) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("The frozen v2.0.0 fixture was not installed")
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                error(
                    "Unexpected fixture setup migration from " +
                        "$oldVersion to $newVersion"
                )
            }
        }
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )

        try {
            openHelper.writableDatabase.apply {
                setForeignKeyConstraintsEnabled(true)
                assertFrozenVersion30Identity()
                execSQL(
                    """
                    INSERT INTO meta_accounts(
                        id,
                        substratePublicKey,
                        substrateCryptoType,
                        substrateAccountId,
                        ethereumPublicKey,
                        ethereumAddress,
                        name,
                        isSelected,
                        position
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        META_ID,
                        SUBSTRATE_KEYPAIR.publicKey,
                        CryptoType.SR25519.name,
                        SUBSTRATE_KEYPAIR.publicKey.substrateAccountId(),
                        ETHEREUM_KEYPAIR.publicKey,
                        ETHEREUM_KEYPAIR.publicKey.ethereumAddressFromPublicKey(),
                        WALLET_NAME,
                        1,
                        0
                    )
                )
                assertEquals(
                    0,
                    singleInt("SELECT COUNT(*) FROM pragma_foreign_key_check")
                )
            }
        } finally {
            openHelper.close()
        }
    }

    private fun SupportSQLiteDatabase.assertFrozenVersion30Identity() {
        assertEquals(ReleasedV200DatabaseFixture.DATABASE_VERSION, version)
        assertEquals(
            ReleasedV200DatabaseFixture.ROOM_IDENTITY_HASH,
            singleString(
                "SELECT identity_hash FROM room_master_table WHERE id = 42"
            )
        )
        assertEquals(
            ReleasedV200DatabaseFixture.EXPECTED_ROOM_TABLE_COUNT,
            singleInt(
                """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'table'
                  AND name NOT IN ('android_metadata', 'sqlite_sequence')
                """.trimIndent()
            )
        )
        assertEquals(
            ReleasedV200DatabaseFixture.EXPECTED_EXPLICIT_INDEX_COUNT,
            singleInt(
                """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'index' AND sql IS NOT NULL
                """.trimIndent()
            )
        )
    }

    private fun openProductionDatabase(
        databaseName: String,
        preferences: HashMapEncryptedPreferences
    ): AppDatabase {
        return AppDatabase.create(
            context = context,
            databaseName = databaseName,
            storeV1 = SecretStoreV1Impl(preferences),
            storeV2 = SecretStoreV2(preferences),
            encryptedPreferences = preferences,
            substrateSecretStore = SubstrateSecretStore(preferences),
            ethereumSecretStore = EthereumSecretStore(preferences)
        ).also { database ->
            database.openHelper.writableDatabase
        }
    }

    private fun assertMigratedDatabaseAndSecrets(
        database: AppDatabase,
        preferences: HashMapEncryptedPreferences
    ) {
        val migrated = database.openHelper.writableDatabase
        assertEquals(77, migrated.version)
        assertEquals(
            WALLET_NAME,
            migrated.singleString(
                "SELECT name FROM meta_accounts WHERE id = ?",
                META_ID
            )
        )
        assertArrayEquals(
            SUBSTRATE_KEYPAIR.publicKey,
            migrated.singleBlob(
                "SELECT substratePublicKey FROM meta_accounts WHERE id = ?",
                META_ID
            )
        )
        assertArrayEquals(
            SUBSTRATE_KEYPAIR.publicKey.substrateAccountId(),
            migrated.singleBlob(
                "SELECT substrateAccountId FROM meta_accounts WHERE id = ?",
                META_ID
            )
        )
        assertArrayEquals(
            ETHEREUM_KEYPAIR.publicKey,
            migrated.singleBlob(
                "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                META_ID
            )
        )
        assertArrayEquals(
            ETHEREUM_KEYPAIR.publicKey.ethereumAddressFromPublicKey(),
            migrated.singleBlob(
                "SELECT ethereumAddress FROM meta_accounts WHERE id = ?",
                META_ID
            )
        )

        val substrate = SubstrateSecretStore(preferences).get(
            metaId = META_ID,
            expectedPublicKey = SUBSTRATE_KEYPAIR.publicKey,
            expectedCryptoType = CryptoType.SR25519,
            expectedAccountId = SUBSTRATE_KEYPAIR.publicKey.substrateAccountId()
        )
        assertNotNull(substrate)
        requireNotNull(substrate)
        assertArrayEquals(ENTROPY, substrate[SubstrateSecrets.Entropy])
        assertArrayEquals(SEED, substrate[SubstrateSecrets.Seed])
        assertEquals(
            SUBSTRATE_DERIVATION_PATH,
            substrate[SubstrateSecrets.SubstrateDerivationPath]
        )
        val substrateKeypair = substrate[SubstrateSecrets.SubstrateKeypair]
        assertArrayEquals(
            SUBSTRATE_KEYPAIR.privateKey,
            substrateKeypair[KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(
            SUBSTRATE_KEYPAIR.publicKey,
            substrateKeypair[KeyPairSchema.PublicKey]
        )
        assertArrayEquals(
            (SUBSTRATE_KEYPAIR as Sr25519Keypair).nonce,
            substrateKeypair[KeyPairSchema.Nonce]
        )

        val ethereum = EthereumSecretStore(preferences).get(
            metaId = META_ID,
            expectedPublicKey = ETHEREUM_KEYPAIR.publicKey,
            expectedAddress =
                ETHEREUM_KEYPAIR.publicKey.ethereumAddressFromPublicKey()
        )
        assertNotNull(ethereum)
        requireNotNull(ethereum)
        assertArrayEquals(ENTROPY, ethereum[EthereumSecrets.Entropy])
        assertArrayEquals(
            ETHEREUM_KEYPAIR.privateKey,
            ethereum[EthereumSecrets.Seed]
        )
        assertEquals(
            ETHEREUM_DERIVATION_PATH,
            ethereum[EthereumSecrets.EthereumDerivationPath]
        )
        val ethereumKeypair = ethereum[EthereumSecrets.EthereumKeypair]
        assertArrayEquals(
            ETHEREUM_KEYPAIR.privateKey,
            ethereumKeypair[KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(
            ETHEREUM_KEYPAIR.publicKey,
            ethereumKeypair[KeyPairSchema.PublicKey]
        )
        assertNull(ethereumKeypair[KeyPairSchema.Nonce])
        assertFalse(preferences.hasKey(legacySecretKey(META_ID)))
        assertFalse(
            preferences.hasKey(
                WalletSecretQuarantine.keyFor(legacySecretKey(META_ID))
            )
        )
    }

    private inline fun <T : AppDatabase> T.useDatabase(block: (T) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    private fun SupportSQLiteDatabase.singleString(
        sql: String,
        vararg bindArgs: Any?
    ): String = query(sql, bindArgs).use { cursor ->
        check(cursor.moveToFirst()) { "Query returned no rows: $sql" }
        cursor.getString(0)
    }

    private fun SupportSQLiteDatabase.singleBlob(
        sql: String,
        vararg bindArgs: Any?
    ): ByteArray = query(sql, bindArgs).use { cursor ->
        check(cursor.moveToFirst()) { "Query returned no rows: $sql" }
        cursor.getBlob(0)
    }

    private fun SupportSQLiteDatabase.singleInt(sql: String): Int =
        query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "Query returned no rows: $sql" }
            cursor.getInt(0)
        }

    private companion object {
        const val PRESERVATION_DATABASE = "released-v200-preservation"
        const val TRUNCATED_DATABASE = "released-v200-truncated"
        const val META_ID = 30L
        const val WALLET_NAME = "v2.0.0 synthetic wallet"
        const val SUBSTRATE_DERIVATION_PATH = "//hard///password"
        const val ETHEREUM_DERIVATION_PATH = "//44//60//0/0/0"

        val DATABASE_NAMES = listOf(
            PRESERVATION_DATABASE,
            TRUNCATED_DATABASE
        )
        val ENTROPY = ByteArray(16) { (it * 7 + 3).toByte() }
        val MNEMONIC = MnemonicCreator.fromEntropy(ENTROPY)
        val DECODED_SUBSTRATE_DERIVATION_PATH =
            SubstrateJunctionDecoder.decode(SUBSTRATE_DERIVATION_PATH)
        val SEED = SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = MNEMONIC.words,
            password = DECODED_SUBSTRATE_DERIVATION_PATH.password
        ).seed
        val SUBSTRATE_KEYPAIR = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.SR25519,
            seed = SEED,
            junctions = DECODED_SUBSTRATE_DERIVATION_PATH.junctions
        )
        val DECODED_ETHEREUM_DERIVATION_PATH =
            BIP32JunctionDecoder.decode(ETHEREUM_DERIVATION_PATH)
        val ETHEREUM_SEED = EthereumSeedFactory.deriveSeed32(
            mnemonicWords = MNEMONIC.words,
            password = DECODED_ETHEREUM_DERIVATION_PATH.password
        ).seed
        val ETHEREUM_KEYPAIR = EthereumKeypairFactory.generate(
            seed = ETHEREUM_SEED,
            junctions = DECODED_ETHEREUM_DERIVATION_PATH.junctions
        )

        fun legacySecretKey(metaId: Long) = "$metaId:ACCESS_SECRETS"
        fun substrateSecretKey(metaId: Long) = "$metaId:SUBSTRATE_SECRETS"
        fun ethereumSecretKey(metaId: Long) = "$metaId:ETHEREUM_SECRETS"
    }
}
