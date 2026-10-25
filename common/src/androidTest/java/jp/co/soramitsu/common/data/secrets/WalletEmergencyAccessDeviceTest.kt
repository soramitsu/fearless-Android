package jp.co.soramitsu.common.data.secrets

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import java.security.KeyStore
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletMasterKeyAttestation
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.quarantineEncryptedStringSnapshotDurably
import jp.co.soramitsu.common.data.storage.pin.WalletEmergencyAuthentication
import jp.co.soramitsu.common.data.storage.pin.WalletEmergencyAuthenticator
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emergency key access (addendum U0.4) on a device: every secret released
 * builds stored (the 259 production-parity golden vectors), written through
 * the Android Keystore-backed encrypted preferences exactly as the app writes
 * them, comes back after the wallet PIN exactly as stored. A key set aside by
 * an internal build and a value this device cannot decrypt are listed too,
 * and nothing in storage changes.
 */
@RunWith(AndroidJUnit4::class)
class WalletEmergencyAccessDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        clearTestStorage()
    }

    @After
    fun tearDown() {
        clearTestStorage()
    }

    @Test
    fun everyReleasedSecretIsShownAfterThePinExactlyAsStored() {
        val walletPreferences = PreferencesImpl(walletPreferences())
        val encryptedPreferences = EncryptedPreferencesImpl(walletPreferences, EncryptionUtil(context))
        encryptedPreferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, PIN)
        val vectors = loadVectors().mapIndexed { index, vector ->
            val metaId = index + 1L
            val key = when (vector["store"].asString) {
                "SubstrateSecrets(V3)" -> "$metaId:SUBSTRATE_SECRETS"
                "EthereumSecrets(V3)" -> "$metaId:ETHEREUM_SECRETS"
                "TonSecrets(V3)" -> "$metaId:TON_SECRETS"
                else -> "$metaId:${vector["accountId"].asString.removePrefix("0x")}:ACCESS_SECRETS"
            }
            encryptedPreferences.putEncryptedString(key, vector["stored"].asString)
            key to vector
        }.toMap()
        // What internal build 230 did with a key it rejected.
        val setAside = vectors.keys.first { it.endsWith(":SUBSTRATE_SECRETS") }
        encryptedPreferences.quarantineEncryptedStringSnapshotDurably(
            sourceKey = setAside,
            quarantineKey = WalletSecretQuarantine.keyFor(setAside),
            expectedSnapshot = checkNotNull(encryptedPreferences.getDecryptedStringSnapshot(setAside))
        )
        // Ciphertext this device cannot decrypt (audit AND-V3).
        walletPreferences().edit()
            .putString(UNREADABLE_KEY, "v2:" + Base64.encodeToString(ByteArray(48) { 7 }, Base64.NO_WRAP))
            .commit()
        val authenticator = WalletEmergencyAuthenticator(walletPreferences, encryptedPreferences)

        assertEquals(WalletEmergencyAuthentication.WrongPin, authenticator.authenticate("000000"))
        assertEquals(WalletEmergencyAuthentication.Authenticated, authenticator.authenticate(PIN))
        val before = walletPreferences().all.toMap()
        val entries = WalletEmergencyRecoveryReader(encryptedPreferences).read()

        assertEquals(before, walletPreferences().all.toMap())
        assertEquals(vectors.size + 1, entries.size)
        entries.forEach { entry ->
            if (entry.storageKey == UNREADABLE_KEY) {
                assertFalse(entry.readable)
                return@forEach
            }
            val activeKey = entry.storageKey.removePrefix(WalletSecretQuarantine.KEY_PREFIX)
            val vector = vectors.getValue(activeKey)
            assertTrue(entry.storageKey, entry.readable)
            assertEquals(
                if (activeKey == setAside) EmergencyRecoveryState.SET_ASIDE else EmergencyRecoveryState.ACTIVE,
                entry.state
            )
            assertFalse(entry.fields.any { it.type == EmergencyRecoveryFieldType.STORED_DATA })
            if (vector["source"].asString.startsWith("ton-mnemonic")) {
                assertEquals(
                    vector["storedText"].asString,
                    entry.fields.single { it.type == EmergencyRecoveryFieldType.TON_PHRASE }.value
                )
            }
        }
    }

    private fun loadVectors(): List<JsonObject> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val root = assets.open("12-validator-acceptance.json").use { stream ->
            JsonParser.parseReader(InputStreamReader(stream, Charsets.UTF_8)).asJsonObject
        }
        return root.getAsJsonArray("vectors").map { it.asJsonObject }
    }

    private fun walletPreferences() =
        context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)

    private fun clearTestStorage() {
        context.getSharedPreferences(KEY_ALIAS, Context.MODE_PRIVATE).edit().clear().commit()
        walletPreferences().edit().clear().commit()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private companion object {
        const val PIN = "135790"
        const val KEY_ALIAS = "key_alias"
        const val UNREADABLE_KEY = "999:ETHEREUM_SECRETS"
    }
}
