package jp.co.soramitsu.common.data.storage.encrypt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Combines the separately qualified ownership-reset and dual-slot storage implementations. */
@RunWith(AndroidJUnit4::class)
class FreshSlotPinRecoveryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keys get() = context.getSharedPreferences("key_alias", Context.MODE_PRIVATE)
    private val wallets get() = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)

    @Before fun setup() = clear()
    @After fun teardown() = clear()

    @Test fun absentOldPinRequiresOwnershipRecoveryAndCanBeReplacedWithoutInventingAnArchive() {
        EncryptionUtil(context).getPrerenceAesKey()
        keys.edit().putString("secret_key", "lost-wrapped-key").commit()
        val recovered = EncryptionUtil(context)
        assertEquals(WalletKeyLossRetry.RestartDevice, recovered.retryPreviousSecureStorage(1, 1000))
        val consent = (recovered.retryPreviousSecureStorage(2, 2000) as WalletKeyLossRetry.CanStart).consent
        recovered.beginFreshKeySlot(consent, true, 2, 2000)
        val preferences = EncryptedPreferencesImpl(PreferencesImpl(wallets), recovered)

        assertFalse(wallets.contains("pin_code"))
        assertEquals(PinHealth.UNREADABLE, preferences.getPinHealth())
        val authorizedSnapshot = preferences.capturePinResetSnapshot()
        preferences.saveRecoveredPinCodeDurably("123456", authorizedSnapshot)

        assertEquals(PinHealth.HEALTHY, preferences.getPinHealth())
        assertEquals("123456", preferences.getDecryptedString("pin_code"))
        assertFalse(wallets.all.keys.any { WalletSecretArchive.sourceKeyOf(it) == "pin_code" })
        assertEquals("lost-wrapped-key", keys.all["secret_key"])
        assertEquals("lost-wrapped-key", keys.all["secret_key_lost_2000"])
        val reopened = EncryptedPreferencesImpl(PreferencesImpl(wallets), EncryptionUtil(context))
        assertEquals(PinHealth.HEALTHY, reopened.getPinHealth())
        assertEquals("123456", reopened.getDecryptedString("pin_code"))
    }

    private fun clear() {
        WalletSecureStorageHealth.resetForTest()
        keys.edit().clear().commit()
        wallets.edit().clear().commit()
        context.deleteDatabase("app.db")
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        listOf("key_alias", "key_alias_v2").forEach { if (store.containsAlias(it)) store.deleteEntry(it) }
    }
}
