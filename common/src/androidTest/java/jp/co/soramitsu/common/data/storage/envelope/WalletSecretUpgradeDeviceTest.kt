package jp.co.soramitsu.common.data.storage.envelope

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.security.SecureRandom
import jp.co.soramitsu.common.data.secrets.WalletEmergencyRecoveryReader
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletPinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealth
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import kotlinx.coroutines.runBlocking
import org.bouncycastle.util.encoders.Hex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 4.3.1 storage on a real device: the Keystore-wrapped device key, the legacy `pin_code` and raw
 * wallet secrets. The first 4.3.2 unlock creates the keyring, whose PIN-wrap factor is kept under
 * the same device key, seals every secret and retires the PIN; a restarted process (new objects
 * over the same files and Keystore) unlocks with the PIN wrap, and emergency export still reveals
 * every secret. The factor stays out of the emergency listing, and no Keystore key is added.
 */
@RunWith(AndroidJUnit4::class)
class WalletSecretUpgradeDeviceTest {
    private lateinit var context: Context

    private val legacySecrets = linkedMapOf(
        "7:ETHEREUM_SECRETS" to "0x" + "00" + Hex.toHexString(ByteArray(32) { 3 }),
        "7:TON_SECRETS" to "0x" + Hex.toHexString(ByteArray(40) { 4 }),
        "wallet_secret_archive:v1:9:SUBSTRATE_SECRETS" to "0x" + Hex.toHexString(ByteArray(48) { 5 })
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName != "jp.co.soramitsu.fearless") { "Never run in the production wallet UID" }
        clear()
    }

    @After
    fun tearDown() = clear()

    private fun clear() {
        WalletSecretSession.clear()
        WalletPinHealth.resetForTest()
        WalletSecureStorageHealth.resetForTest()
        context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("key_alias", Context.MODE_PRIVATE).edit().clear().commit()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        listOf("key_alias", "key_alias_v2").forEach { if (store.containsAlias(it)) store.deleteEntry(it) }
    }

    private inner class Process {
        val preferences = PreferencesImpl(context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE))
        val encryption = EncryptionUtil(context)
        // The production factor: kept under the same device key as every wallet secret.
        val keyring = WalletSecretKeyring(preferences, MasterKeyBoundKeyringFactor(preferences, encryption))
        val sealer = WalletSecretSealer(preferences, keyring, SecureRandom())
        val storage = EncryptedPreferencesImpl(preferences, encryption, sealer)
        val authority = WalletPinAuthority(storage, keyring, sealer)
        val migration = WalletSecretSealingMigration(storage, keyring, sealer, authority, WalletSecretMutationJournalStore(storage))
    }

    @Test
    fun anUpgradedDeviceSealsEverySecretAndKeepsEveryOneReachable() = runBlocking {
        // 4.3.1: no envelope, the PIN under the device key.
        val legacy = EncryptedPreferencesImpl(
            PreferencesImpl(context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)),
            EncryptionUtil(context)
        )
        legacy.savePinCodeDurably(PIN)
        legacySecrets.forEach(legacy::putEncryptedString)

        val keystoreBefore = keystoreAliases()
        val first = Process()
        assertEquals(PinHealth.HEALTHY, first.authority.health())
        assertEquals(PinVerification.CORRECT, first.authority.verify(PIN))
        // The keyring added no Keystore key: its PIN-wrap factor lives under the device key.
        assertEquals(keystoreBefore, keystoreAliases())
        val factors = factorValues()
        assertEquals(1, factors.size)
        val report = first.migration.run()
        assertEquals(legacySecrets.size, report.sealed)
        assertTrue(report.legacyPinRetired)
        assertFalse(first.storage.hasKey("pin_code"))
        for ((key, original) in legacySecrets) {
            val stored = checkNotNull(first.storage.getDecryptedString(key))
            assertTrue(key, WalletSecretEnvelope.isSealed(stored))
            assertEquals(key, original, WalletSecretEnvelopes.open(stored))
        }

        // A new process: nothing in memory, the same files and Keystore.
        WalletSecretSession.clear()
        WalletPinHealth.resetForTest()
        val second = Process()
        second.encryption.getPrerenceAesKey()
        assertEquals(PinHealth.HEALTHY, second.authority.health())
        assertEquals(PinVerification.WRONG, second.authority.verify("000000"))
        assertEquals(PinVerification.CORRECT, second.authority.verify(PIN))
        assertEquals(WalletSecretSession.Origin.PIN_WRAP, WalletSecretSession.origin())
        for ((key, original) in legacySecrets) {
            assertEquals(key, original, WalletSecretEnvelopes.open(checkNotNull(second.storage.getDecryptedString(key))))
        }

        // Emergency export opens them with the PIN alone, without unlocking the session.
        WalletSecretSession.clear()
        val (verification, sk) = second.authority.verifyForEmergency(PIN)
        assertEquals(PinVerification.CORRECT, verification)
        val reader = WalletEmergencyRecoveryReader(second.storage)
        for (key in legacySecrets.keys) {
            assertTrue(key, reader.readKey(key, sk).readable)
        }
        checkNotNull(sk).close()
        assertFalse(WalletSecretSession.isOpen())

        // The factor is never sealed, moved or listed as a wallet key.
        assertEquals(factors, factorValues())
        val listed = reader.listPage().keys.map { it.storageKey }
        assertTrue(listed.containsAll(legacySecrets.keys))
        assertTrue(listed.none { it.startsWith(MasterKeyBoundKeyringFactor.ALIAS_PREFIX) })
    }

    private fun factorValues(): Map<String, Any?> = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE).all
        .filterKeys { it.startsWith(MasterKeyBoundKeyringFactor.ALIAS_PREFIX) }

    private fun keystoreAliases(): Set<String> =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.aliases().toList().toSet()

    private companion object {
        const val PIN = "203948"
    }
}
