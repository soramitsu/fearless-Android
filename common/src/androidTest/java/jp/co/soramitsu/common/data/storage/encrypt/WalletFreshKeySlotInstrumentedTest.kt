package jp.co.soramitsu.common.data.storage.encrypt

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletFreshKeySlotInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keys get() = context.getSharedPreferences("key_alias", Context.MODE_PRIVATE)
    private val wallets get() = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
    private val store get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Before fun before() = clear()
    @After fun after() = clear()

    @Test fun freshSlotRequiresRetriesAcrossDeviceBootsAndPreservesExactOldValues() {
        val old = EncryptionUtil(context)
        old.getPrerenceAesKey()
        wallets.edit().putString("pin_code", old.encrypt("123456")).putString("42:ACCESS_SECRETS", old.encrypt("old payload")).commit()
        val oldWallets = wallets.all.toMap()
        val certificate = store.getCertificate("key_alias").encoded
        val malformed = "%%%stored-wrapped-value%%%"
        keys.edit().putString("secret_key", malformed).commit()
        val recovery = EncryptionUtil(context)
        assertEquals(WalletKeyLossRetry.RestartDevice, recovery.retryPreviousSecureStorage(8, 1000))
        assertEquals(WalletKeyLossRetry.RestartDevice, recovery.retryPreviousSecureStorage(8, 2000))
        assertFalse(keys.contains("secret_key_v2"))
        val consent = (recovery.retryPreviousSecureStorage(9, 3000) as WalletKeyLossRetry.CanStart).consent
        assertThrows(IllegalArgumentException::class.java) { recovery.beginFreshKeySlot(consent, false, 9, 3000) }
        recovery.beginFreshKeySlot(consent, true, 9, 3000)
        assertEquals(oldWallets, wallets.all)
        assertEquals(malformed, keys.all["secret_key"])
        assertEquals(malformed, keys.all["secret_key_lost_3000"])
        assertArrayEquals(certificate, store.getCertificate("key_alias").encoded)
        assertTrue(store.containsAlias("key_alias_v2"))
        assertEquals(2, keys.all["active_slot"])
        assertEquals(PinHealth.UNREADABLE, WalletPinHealth.health.value)
        val newCiphertext = recovery.encrypt("new payload")
        assertEquals("new payload", EncryptionUtil(context).decrypt(newCiphertext))
        assertThrows(IllegalStateException::class.java) { recovery.beginFreshKeySlot(consent, true, 9, 3000) }
    }

    @Test fun recoveredPreviousKeyReadsBothSlotsAndNewPinWithoutChangingEitherSentinel() {
        val initial = EncryptionUtil(context)
        val originalKey = initial.getPrerenceAesKey().encoded
        val wrapped = keys.getString("secret_key", null)!!
        val oldCipher = initial.encrypt("old payload")
        val oldSentinel = wallets.all[WalletMasterKeyAttestation.SENTINEL_KEY]
        keys.edit().putString("secret_key", "malformed").commit()
        val fresh = activate()
        assertEquals(PinHealth.UNREADABLE, EncryptedPreferencesImpl(
            jp.co.soramitsu.common.data.storage.PreferencesImpl(wallets), fresh
        ).getPinHealth()) // no PIN must not authorize onboarding
        val freshCipher = fresh.encrypt("fresh payload")
        wallets.edit().putString("pin_code", fresh.encrypt("987654")).commit()
        val newSentinel = keys.all["wallet_master_key_attestation_v2"]
        keys.edit().putString("secret_key", wrapped).commit() // OS/provider recovery simulated with the retained wrapping alias
        val reopened = EncryptionUtil(context)
        assertArrayEquals(originalKey, reopened.getPrerenceAesKey().encoded)
        assertEquals("old payload", reopened.decrypt(oldCipher))
        assertEquals("fresh payload", reopened.decrypt(freshCipher))
        assertEquals("987654", reopened.decrypt(wallets.getString("pin_code", "")!!))
        assertEquals(PinHealth.HEALTHY, reopened.assessPinHealth())
        assertEquals(WalletKeyLossRetry.Recovered, reopened.retryPreviousSecureStorage(2, 3000))
        assertEquals(oldSentinel, wallets.all[WalletMasterKeyAttestation.SENTINEL_KEY])
        assertEquals(newSentinel, keys.all["wallet_master_key_attestation_v2"])
    }

    @Test fun typedMalformedWrappedValuesRemainExactAndMissingValueRemainsAbsent() {
        listOf<Any?>(null, 47, 48L, 1.25f, true, setOf("wrapped", "unreadable")).forEach { value ->
            clear()
            EncryptionUtil(context).getPrerenceAesKey()
            keys.edit().remove("secret_key").commit()
            val editor = keys.edit()
            WalletWrappedKeySnapshot(value).retain(editor, "secret_key")
            editor.commit()
            activate()
            assertTrue(WalletWrappedKeySnapshot(value).matches(keys.all["secret_key"]))
            assertTrue(WalletWrappedKeySnapshot(value).matches(keys.all["secret_key_lost_2000"]))
            assertEquals(value != null, keys.all["secret_key_lost_2000_present"])
        }
    }

    @Test fun changedOrExpiredConsentAndRecoveredOldKeyNeverCreateFreshSlot() {
        EncryptionUtil(context).getPrerenceAesKey()
        val wrapped = keys.all["secret_key"] as String
        keys.edit().putString("secret_key", "malformed").commit()
        val recovery = EncryptionUtil(context)
        recovery.retryPreviousSecureStorage(1, 1000)
        val consent = (recovery.retryPreviousSecureStorage(2, 2000) as WalletKeyLossRetry.CanStart).consent
        assertThrows(IllegalArgumentException::class.java) { recovery.beginFreshKeySlot(consent, true, 2, 302001) }
        keys.edit().putString("secret_key", wrapped).commit()
        assertThrows(IllegalArgumentException::class.java) { recovery.beginFreshKeySlot(consent, true, 2, 2000) }
        assertFalse(keys.contains("active_slot"))
        assertFalse(store.containsAlias("key_alias_v2"))
    }

    @Test fun providerFailureNeverGrantsConsentOrChangesStorage() {
        EncryptionUtil(context).getPrerenceAesKey()
        val exact = keys.all.toMap()
        val unavailable = EncryptionUtil(context, object : WalletPayloadDecryptor {
            override fun decrypt(transformation: String, key: ByteArray, parameters: AlgorithmParameterSpec,
                ciphertext: ByteArray, secureRandom: SecureRandom): ByteArray = throw ProviderException("offline provider")
        })
        assertEquals(WalletKeyLossRetry.RetryLater, unavailable.retryPreviousSecureStorage(1, 1000))
        assertEquals(WalletKeyLossRetry.RetryLater, unavailable.retryPreviousSecureStorage(2, 2000))
        assertEquals(exact, keys.all)
    }

    @Test fun freshSlotCannotAuthenticateWithLegacySentinelOrSilentlyReplaceMissingAlias() {
        EncryptionUtil(context).getPrerenceAesKey()
        keys.edit().putString("secret_key", "malformed").commit()
        activate()
        val exactWallets = wallets.all.toMap()
        keys.edit().putString("wallet_master_key_attestation_v2", wallets.all[WalletMasterKeyAttestation.SENTINEL_KEY] as String).commit()
        val failure = assertThrows(WalletSecureStorageUnavailableException::class.java) { EncryptionUtil(context).getPrerenceAesKey() }
        assertEquals(WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS, failure.kind)
        assertEquals(exactWallets, wallets.all)
        store.deleteEntry("key_alias_v2")
        assertThrows(WalletSecureStorageUnavailableException::class.java) { EncryptionUtil(context).getPrerenceAesKey() }
        assertFalse(store.containsAlias("key_alias_v2"))
    }

    @Test fun previousHealthySlotRemainsAvailableIfFreshSlotLaterFails() {
        EncryptionUtil(context).getPrerenceAesKey()
        val wrapped = keys.getString("secret_key", null)!!
        keys.edit().putString("secret_key", "malformed").commit()
        activate()
        keys.edit().putString("secret_key", wrapped).putString("wallet_master_key_attestation_v2", "malformed").commit()
        assertEquals(32, EncryptionUtil(context).getPrerenceAesKey().encoded.size)
    }

    @Test fun oversizedPinIsUnreadableAndRetainedWithoutPassingItToTheCipher() {
        EncryptionUtil(context).getPrerenceAesKey()
        val oversized = "v2:" + "A".repeat(4097)
        wallets.edit().putString("pin_code", oversized).commit()
        var providerCalls = 0
        val guarded = EncryptionUtil(context, WalletPayloadDecryptor { transformation, key, parameters, ciphertext, random ->
            check(ciphertext.size < 4096) { "Oversized PIN reached the provider" }
            providerCalls++
            JcaWalletPayloadDecryptor.decrypt(transformation, key, parameters, ciphertext, random)
        })
        assertEquals(PinHealth.UNREADABLE, guarded.assessPinHealth())
        assertEquals(1, providerCalls) // Only the independent sentinel authenticates.
        assertEquals(oversized, wallets.all["pin_code"])
    }

    @Test fun ambiguousActivationCommitRequiresRestartAndThenReadsOneCompleteSlot() {
        EncryptionUtil(context).getPrerenceAesKey()
        keys.edit().putString("secret_key", "malformed").commit()
        val recovery = EncryptionUtil(context)
        recovery.retryPreviousSecureStorage(1, 1000)
        val consent = (recovery.retryPreviousSecureStorage(2, 2000) as WalletKeyLossRetry.CanStart).consent
        val previousWallets = wallets.all.toMap()
        val wrappedContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val real = context.getSharedPreferences(name, mode)
                if (name != "key_alias") return real
                return object : SharedPreferences by real {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = real.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { editor.putBoolean(key, value); return this }
                            override fun putString(key: String, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                            override fun putInt(key: String, value: Int): SharedPreferences.Editor { editor.putInt(key, value); return this }
                            override fun commit(): Boolean { editor.commit(); return false }
                        }
                    }
                }
            }
        }
        val failed = assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(wrappedContext).beginFreshKeySlot(consent, true, 2, 2000)
        }
        assertEquals(WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED, failed.kind)
        assertFalse(WalletSecureStorageHealth.isHealthy())
        assertEquals(previousWallets, wallets.all)
        WalletSecureStorageHealth.resetForTest() // Simulate a new process reading durable state.
        val reopened = EncryptionUtil(context)
        assertEquals(32, reopened.getPrerenceAesKey().encoded.size)
        assertEquals(PinHealth.UNREADABLE, reopened.assessPinHealth())
        assertEquals("malformed", keys.all["secret_key_lost_2000"])
    }

    private fun activate(): EncryptionUtil {
        val recovery = EncryptionUtil(context)
        recovery.retryPreviousSecureStorage(1, 1000)
        val consent = (recovery.retryPreviousSecureStorage(2, 2000) as WalletKeyLossRetry.CanStart).consent
        recovery.beginFreshKeySlot(consent, true, 2, 2000)
        return recovery
    }

    private fun clear() {
        WalletSecureStorageHealth.resetForTest()
        keys.edit().clear().commit()
        wallets.edit().clear().commit()
        context.deleteDatabase("app.db")
        listOf("key_alias", "key_alias_v2").forEach { if (store.containsAlias(it)) store.deleteEntry(it) }
    }
}
