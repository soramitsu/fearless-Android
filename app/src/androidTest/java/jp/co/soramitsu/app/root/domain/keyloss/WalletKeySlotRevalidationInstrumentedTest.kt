package jp.co.soramitsu.app.root.domain.keyloss

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.*
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.migrations.WalletQuarantineRevalidation
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletKeySlotRevalidationInstrumentedTest {
    @Test fun previousSlotReturnReleasesMatchingQuarantineAndPreservesNewSlotWalletAndPin() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(application) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                application.getSharedPreferences("keyloss-test-$name", mode)
            override fun getDatabasePath(name: String) = application.getDatabasePath("keyloss-test-$name")
        }
        val wrapped = context.getSharedPreferences("key_alias", Context.MODE_PRIVATE)
        val raw = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val keypair = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { (it + 1).toByte() })
        val accountId = keypair.publicKey.substrateAccountId()
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("keyloss-revalidation.db").callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE meta_accounts (id INTEGER PRIMARY KEY, substratePublicKey BLOB, substrateCryptoType TEXT, substrateAccountId BLOB, ethereumPublicKey BLOB, ethereumAddress BLOB, tonPublicKey BLOB)")
                    db.execSQL("CREATE TABLE ton_connection (metaId INTEGER, url TEXT, source TEXT)")
                    db.execSQL("CREATE TABLE chain_accounts (metaId INTEGER, chainId TEXT, accountId BLOB, publicKey BLOB, cryptoType TEXT)")
                    db.execSQL("INSERT INTO meta_accounts VALUES (42, ?, 'ECDSA', ?, NULL, NULL, NULL)", arrayOf(keypair.publicKey, accountId))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("No migration permitted")
            }).build())
        try {
            wrapped.edit().clear().commit()
            raw.edit().clear().commit()
            listOf("key_alias", "key_alias_v2").forEach { if (keyStore.containsAlias(it)) keyStore.deleteEntry(it) }
            val util = EncryptionUtil(context)
            val preferences = EncryptedPreferencesImpl(PreferencesImpl(raw), util)
            val plaintext = SubstrateSecrets(substrateKeyPair = keypair).toHexString()
            preferences.putEncryptedString("42:SUBSTRATE_SECRETS", plaintext)
            val exactOriginal = raw.all["42:SUBSTRATE_SECRETS"]
            val previousWrapped = wrapped.all["secret_key"] as String
            wrapped.edit().putString("secret_key", "malformed test fixture").commit()
            val recovery = EncryptionUtil(context)
            // Test-only boot simulation invokes the internal seam; production reads BOOT_COUNT itself.
            val retry = EncryptionUtil::class.java.declaredMethods.single {
                it.name.startsWith("retryPreviousSecureStorage") && it.parameterTypes.size == 2
            }.apply { isAccessible = true }
            retry.invoke(recovery, 1, 1000L)
            val consent = (retry.invoke(recovery, 2, 2000L) as WalletKeyLossRetry.CanStart).consent
            EncryptionUtil::class.java.declaredMethods.single {
                it.name.startsWith("beginFreshKeySlot") && it.parameterTypes.size == 4
            }.apply { isAccessible = true }.invoke(recovery, consent, true, 2, 2000L)
            val freshPreferences = EncryptedPreferencesImpl(PreferencesImpl(raw), recovery)
            assertThrows(WalletRecoveryRequiredException::class.java) {
                SubstrateSecretStore(freshPreferences).get(42, keypair.publicKey, CryptoType.ECDSA, accountId)
            }
            val quarantine = WalletSecretQuarantine.keyFor("42:SUBSTRATE_SECRETS")
            assertEquals(exactOriginal, raw.all[quarantine])
            assertFalse(raw.contains("42:SUBSTRATE_SECRETS"))
            freshPreferences.putEncryptedString("43:SUBSTRATE_SECRETS", plaintext)
            raw.edit().putString("pin_code", recovery.encrypt("654321")).commit()
            val exactNew = raw.all["43:SUBSTRATE_SECRETS"]
            val exactPin = raw.all["pin_code"]
            wrapped.edit().putString("secret_key", previousWrapped).commit()
            val reopened = EncryptionUtil(context)
            val reopenedPreferences = EncryptedPreferencesImpl(PreferencesImpl(raw), reopened)
            val report = WalletQuarantineRevalidation(reopenedPreferences).reconcile(helper.writableDatabase)
            assertEquals("One proven original wallet is released", 1, report.released)
            assertEquals(0, report.skipped)
            assertEquals(exactOriginal, raw.all["42:SUBSTRATE_SECRETS"])
            assertFalse(raw.contains(quarantine))
            assertNotNull(SubstrateSecretStore(reopenedPreferences).get(42, keypair.publicKey, CryptoType.ECDSA, accountId))
            assertEquals(plaintext, reopenedPreferences.getDecryptedString("43:SUBSTRATE_SECRETS"))
            assertEquals("654321", reopenedPreferences.getDecryptedString("pin_code"))
            assertEquals(exactNew, raw.all["43:SUBSTRATE_SECRETS"])
            assertEquals(exactPin, raw.all["pin_code"])
            assertEquals(PinHealth.HEALTHY, reopened.assessPinHealth())
        } finally {
            helper.close()
            context.getDatabasePath("keyloss-revalidation.db").delete()
            wrapped.edit().clear().commit()
            raw.edit().clear().commit()
            listOf("key_alias", "key_alias_v2").forEach { if (keyStore.containsAlias(it)) keyStore.deleteEntry(it) }
        }
    }
}
