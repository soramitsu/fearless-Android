package jp.co.soramitsu.coredb.migrations

import android.content.SharedPreferences
import java.security.SecureRandom
import jp.co.soramitsu.common.data.secrets.WalletEmergencyRecoveryReader
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletPinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.envelope.MasterKeyBoundKeyringFactor
import jp.co.soramitsu.common.data.storage.envelope.PinVerification
import jp.co.soramitsu.common.data.storage.envelope.WalletPinAuthority
import jp.co.soramitsu.common.data.storage.envelope.WalletPinKdfParameters
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretEnvelope
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretKeyring
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealPolicy
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealer
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealingMigration
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSession
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The PIN wrap's device factor and SK's sealing mark sit in the same preference file as every
 * wallet secret. A full upgrade from 4.3.1 data (first unlock, sealing), the quarantine
 * revalidation and the orphan inventory leave them exactly as they were, and emergency export
 * never lists them.
 */
class WalletKeyringFactorUntouchedTest {

    private val wallet = ShippedWalletFixtures.rawSeedDeclinedEthereum()
    private val identity = ShippedWalletFixtures.identity(META_ID, wallet)
    private val shared = MapPreferences()
    private val preferences = PreferencesImpl(shared)
    private val crypto = mock<EncryptionUtil>()
    private val keyring = WalletSecretKeyring(
        preferences, MasterKeyBoundKeyringFactor(preferences, crypto), SecureRandom(), WalletPinKdfParameters(memoryKiB = 64, iterations = 1)
    )
    private val sealer = WalletSecretSealer(preferences, keyring, SecureRandom(), true)
    private val storage = EncryptedPreferencesImpl(preferences, crypto, sealer)
    private val authority = WalletPinAuthority(storage, keyring, sealer)
    private val migration = WalletSecretSealingMigration(storage, keyring, sealer, authority, WalletSecretMutationJournalStore(storage))

    @Before
    fun setUp() {
        reset()
        whenever(crypto.encrypt(any())).thenAnswer { "v2:test:" + it.getArgument<String>(0) }
        whenever(crypto.isModernCiphertext(anyOrNull())).thenAnswer { it.getArgument<String?>(0)?.startsWith("v2:test:") == true }
        whenever(crypto.decrypt(any())).thenAnswer {
            it.getArgument<String>(0).takeIf { value -> value.startsWith("v2:test:") }?.removePrefix("v2:test:").orEmpty()
        }
        whenever(crypto.assessPinHealth()).thenAnswer {
            val snapshot = preferences.pinPreferenceSnapshot()
            PinHealth.assess(snapshot.exists, snapshot.ciphertext?.let(crypto::decrypt))
        }
    }

    @After
    fun tearDown() = reset()

    private fun reset() {
        WalletSecretSession.clear()
        WalletSessionLock.lock()
        WalletPinHealth.resetForTest()
    }

    private fun putLegacy(key: String, plaintext: String) {
        shared.values[key] = "v2:test:$plaintext"
    }

    private fun factorValues(): Map<String, Any?> =
        shared.values.filterKeys { it.startsWith(MasterKeyBoundKeyringFactor.ALIAS_PREFIX) }

    @Test
    fun anUpgradeSealAndBothPassesLeaveTheFactorAndSealingMarkAloneAndUnlisted(): Unit = runBlocking {
        val converted = checkNotNull(
            WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(encoded = wallet.accessSecretsHex, identity = identity)
                .substratePlaintext
        )
        // 4.3.1: the legacy PIN, the wallet's active key, a quarantined legacy payload and a deleted wallet's key.
        putLegacy("pin_code", PIN)
        putLegacy(SUBSTRATE, converted)
        putLegacy(WalletSecretQuarantine.keyFor(ACCESS), wallet.accessSecretsHex)
        putLegacy(ORPHAN, converted)

        assertEquals(PinVerification.CORRECT, authority.verify(PIN))
        val factors = factorValues()
        assertEquals(1, factors.size)
        val alias = factors.keys.single()

        val report = migration.run()
        assertEquals(3, report.sealed)
        assertTrue(report.legacyPinRetired)
        val mark = WalletSecretKeyring.sealingKey(checkNotNull(keyring.currentKeyId()))
        val markValue = checkNotNull(shared.values[mark])
        WalletQuarantineRevalidation(storage).reconcile(ShippedWalletFixtures.database(identity))
        val orphans = WalletOrphanSecretInventory(storage).reconcile(ShippedWalletFixtures.database(identity))
        assertEquals(1, orphans.archivedCount)
        assertTrue(WalletSecretEnvelope.isSealed(shared.values[WalletSecretArchive.keyFor(ORPHAN)]?.toString()?.removePrefix("v2:test:")))

        // The factor is exactly as written, and nothing that walks wallet values treats it as one.
        assertEquals(factors, factorValues())
        assertFalse(WalletSecretSealPolicy.isSealable(alias))
        val reader = WalletEmergencyRecoveryReader(storage)
        val listed = reader.listPage().keys.map { it.storageKey }
        assertTrue(listed.contains(SUBSTRATE))
        assertTrue(listed.none { it.startsWith(MasterKeyBoundKeyringFactor.ALIAS_PREFIX) })
        // The sealing mark too: unchanged, never sealed, never listed.
        assertEquals(markValue, shared.values[mark])
        assertFalse(WalletSecretSealPolicy.isSealable(mark))
        assertTrue(listed.none { it.startsWith(WalletSecretKeyring.KEY_PREFIX) })
        assertEquals(PinVerification.CORRECT, authority.verify(PIN))
    }

    /** A plain in-memory SharedPreferences whose commits are atomic, like the platform's. */
    private class MapPreferences : SharedPreferences {
        val values = linkedMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = LinkedHashMap(values)
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = key in values
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val puts = linkedMapOf<String, Any?>()
            private val removes = linkedSetOf<String>()
            private var clear = false
            override fun putString(key: String, value: String?) = apply { puts[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { puts[key] = values?.toSet() }
            override fun putInt(key: String, value: Int) = apply { puts[key] = value }
            override fun putLong(key: String, value: Long) = apply { puts[key] = value }
            override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
            override fun remove(key: String) = apply { removes += key }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean {
                if (clear) values.clear()
                removes.forEach(values::remove)
                puts.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }

    private companion object {
        const val META_ID = 5L
        const val PIN = "731905"
        const val ACCESS = "5:ACCESS_SECRETS"
        const val SUBSTRATE = "5:SUBSTRATE_SECRETS"
        const val ORPHAN = "12:SUBSTRATE_SECRETS"
    }
}
