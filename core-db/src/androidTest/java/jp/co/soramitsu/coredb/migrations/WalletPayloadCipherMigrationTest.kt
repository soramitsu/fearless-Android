package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtilTestFactory
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletPayloadCipherMigrationTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Before
    fun setUp() {
        clearTestStorage()
    }

    @After
    fun tearDown() {
        clearTestStorage()
    }

    @Test
    fun version76ProviderFailurePreservesExactCiphertextAndRetrySucceeds() {
        val fixture = ethereumFixture()
        createVersion76Database(PROVIDER_FAILURE_DATABASE, fixture)
        val normalPreferences = encryptedPreferences(EncryptionUtil(context))
        normalPreferences.putEncryptedString(ETHEREUM_SECRET_KEY, fixture.plaintext)
        val exactCiphertext = checkNotNull(
            walletPreferences().getString(ETHEREUM_SECRET_KEY, null)
        )
        val failingPreferences = encryptedPreferences(
            EncryptionUtilTestFactory.withPayloadDecryptFailure(
                context = context,
                transformation = AES_GCM_TRANSFORMATION,
                successfulMatchingDecryptions = 1,
                failure = ProviderException("transient payload provider failure")
            )
        )

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            openProductionDatabase(PROVIDER_FAILURE_DATABASE, failingPreferences)
        }

        assertEquals(76, rawDatabaseVersion(PROVIDER_FAILURE_DATABASE))
        assertEquals(
            exactCiphertext,
            walletPreferences().getString(ETHEREUM_SECRET_KEY, null)
        )
        assertFalse(walletPreferences().contains(ETHEREUM_QUARANTINE_KEY))

        val retryPreferences = encryptedPreferences(EncryptionUtil(context))
        assertEquals(
            77,
            openProductionDatabase(PROVIDER_FAILURE_DATABASE, retryPreferences)
        )
        assertEquals(
            fixture.plaintext,
            retryPreferences.getDecryptedString(ETHEREUM_SECRET_KEY)
        )
        assertFalse(walletPreferences().contains(ETHEREUM_QUARANTINE_KEY))
    }

    @Test
    fun version76LocalCiphertextFailuresQuarantineExactlyAndCommit() {
        val fixture = ethereumFixture()
        val encryptionUtil = EncryptionUtil(context)
        val key = encryptionUtil.getPrerenceAesKey().encoded
        val validModern = encryptionUtil.encrypt(key, "authenticated payload")
        val invalidModernTag = decodeModernPayload(validModern).also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }.toModernCiphertext()
        val invalidLegacyPadding = Base64.decode(
            encryptLegacy(key, "padding"),
            Base64.NO_WRAP
        ).also {
            it[LEGACY_IV_BYTES - 1] =
                (it[LEGACY_IV_BYTES - 1].toInt() xor 1).toByte()
        }.toBase64()
        val corruptPayloads = linkedMapOf(
            "malformed-base64" to "v2:%%%not-base64%%%",
            "oversized-base64" to
                ("v2:" + "A".repeat(MAX_ENCRYPTED_CIPHERTEXT_CHARS)),
            "truncated-gcm" to
                ByteArray(GCM_MINIMUM_PAYLOAD_BYTES - 1).toModernCiphertext(),
            "invalid-gcm-tag" to invalidModernTag,
            "invalid-cbc-padding" to invalidLegacyPadding,
            "illegal-cbc-block-size" to
                ByteArray(LEGACY_IV_BYTES + 17).toBase64()
        )

        corruptPayloads.forEach { (caseName, exactCiphertext) ->
            val databaseName = "wallet-payload-corruption-$caseName"
            createdDatabases += databaseName
            createVersion76Database(databaseName, fixture)
            walletPreferences().edit()
                .remove(ETHEREUM_QUARANTINE_KEY)
                .putString(ETHEREUM_SECRET_KEY, exactCiphertext)
                .commit()
            val preferences = encryptedPreferences(EncryptionUtil(context))

            assertEquals(77, openProductionDatabase(databaseName, preferences))
            assertEquals(77, rawDatabaseVersion(databaseName))
            assertFalse(walletPreferences().contains(ETHEREUM_SECRET_KEY))
            assertEquals(
                exactCiphertext,
                walletPreferences().getString(ETHEREUM_QUARANTINE_KEY, null)
            )
            assertThrows(WalletRecoveryRequiredException::class.java) {
                EthereumSecretStore(preferences).get(
                    META_ID,
                    fixture.publicKey,
                    fixture.address
                )
            }
        }
    }

    private fun createVersion76Database(
        databaseName: String,
        fixture: EthereumFixture
    ) {
        createdDatabases += databaseName
        helper.createDatabase(databaseName, 76).apply {
            execSQL(
                """
                INSERT INTO meta_accounts(
                    id,
                    substratePublicKey,
                    substrateCryptoType,
                    substrateAccountId,
                    ethereumPublicKey,
                    ethereumAddress,
                    tonPublicKey,
                    name,
                    isSelected,
                    position,
                    isBackedUp,
                    googleBackupAddress,
                    initialized
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    META_ID,
                    null,
                    null,
                    null,
                    fixture.publicKey,
                    fixture.address,
                    null,
                    "Payload failure fixture",
                    1,
                    0,
                    1,
                    null,
                    1
                )
            )
            close()
        }
    }

    private fun openProductionDatabase(
        databaseName: String,
        preferences: EncryptedPreferences
    ): Int {
        var database: AppDatabase? = null
        return try {
            database = AppDatabase.create(
                context = context,
                databaseName = databaseName,
                storeV1 = SecretStoreV1Impl(preferences),
                storeV2 = SecretStoreV2(preferences),
                encryptedPreferences = preferences,
                substrateSecretStore = SubstrateSecretStore(preferences),
                ethereumSecretStore = EthereumSecretStore(preferences)
            )
            database.openHelper.writableDatabase.version
        } finally {
            database?.close()
        }
    }

    private fun rawDatabaseVersion(databaseName: String): Int {
        return SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use(SQLiteDatabase::getVersion)
    }

    private fun encryptedPreferences(
        encryptionUtil: EncryptionUtil
    ): EncryptedPreferences {
        return EncryptedPreferencesImpl(
            preferences = PreferencesImpl(walletPreferences()),
            encryptionUtil = encryptionUtil
        )
    }

    private fun ethereumFixture(): EthereumFixture {
        val privateKey = ByteArray(32).apply { this[lastIndex] = 7 }
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey)
        return EthereumFixture(
            publicKey = keypair.publicKey,
            address = keypair.publicKey.ethereumAddressFromPublicKey(),
            plaintext = EthereumSecrets(
                seed = keypair.privateKey,
                ethereumKeypair = keypair
            ).toHexString()
        )
    }

    private fun decodeModernPayload(ciphertext: String): ByteArray {
        check(ciphertext.startsWith(MODERN_CIPHER_PREFIX))
        return Base64.decode(
            ciphertext.removePrefix(MODERN_CIPHER_PREFIX),
            Base64.NO_WRAP
        )
    }

    private fun ByteArray.toModernCiphertext(): String {
        return MODERN_CIPHER_PREFIX + toBase64()
    }

    private fun ByteArray.toBase64(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private fun encryptLegacy(key: ByteArray, plaintext: String): String {
        val iv = ByteArray(LEGACY_IV_BYTES) { index -> (index + 1).toByte() }
        val cipher = Cipher.getInstance(AES_CBC_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, AES_ALGORITHM),
            IvParameterSpec(iv)
        )
        return (iv + cipher.doFinal(plaintext.toByteArray())).toBase64()
    }

    private fun walletPreferences() = context.getSharedPreferences(
        SHARED_PREFERENCES_FILE,
        Context.MODE_PRIVATE
    )

    private fun keyPreferences() = context.getSharedPreferences(
        KEY_ALIAS,
        Context.MODE_PRIVATE
    )

    private fun clearTestStorage() {
        createdDatabases.forEach(context::deleteDatabase)
        createdDatabases.clear()
        walletPreferences().edit().clear().commit()
        keyPreferences().edit().clear().commit()
        val keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER).apply {
            load(null)
        }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private data class EthereumFixture(
        val publicKey: ByteArray,
        val address: ByteArray,
        val plaintext: String
    )

    private companion object {
        const val PROVIDER_FAILURE_DATABASE =
            "wallet-payload-provider-failure"
        const val META_ID = 76L
        const val ETHEREUM_SECRET_KEY = "$META_ID:ETHEREUM_SECRETS"
        val ETHEREUM_QUARANTINE_KEY =
            WalletSecretQuarantine.keyFor(ETHEREUM_SECRET_KEY)
        const val KEY_STORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "key_alias"
        const val AES_ALGORITHM = "AES"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_CBC_TRANSFORMATION = "AES/CBC/PKCS5Padding"
        const val MODERN_CIPHER_PREFIX = "v2:"
        const val MAX_ENCRYPTED_CIPHERTEXT_CHARS = 2_097_152
        const val GCM_MINIMUM_PAYLOAD_BYTES = 28
        const val LEGACY_IV_BYTES = 16
        val createdDatabases = mutableSetOf<String>()
    }
}
