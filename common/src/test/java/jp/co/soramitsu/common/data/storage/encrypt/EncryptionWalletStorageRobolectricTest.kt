package jp.co.soramitsu.common.data.storage.encrypt

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.security.KeyPairGenerator
import javax.crypto.Cipher
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import org.bouncycastle.util.encoders.Base64
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EncryptionWalletStorageRobolectricTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val wrapping = context.getSharedPreferences("key_alias", Context.MODE_PRIVATE)
    private val wallet = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
    private val key = ByteArray(32) { (it + 1).toByte() }
    private val pair by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }

    @Before
    fun clearStorage() {
        wrapping.edit().clear().commit()
        wallet.edit().clear().commit()
        context.deleteDatabase("app.db")
        WalletSecureStorageHealth.resetForTest()
        WalletPinHealth.resetForTest()
    }

    @After
    fun resetHealth() {
        WalletSecureStorageHealth.resetForTest()
        WalletPinHealth.resetForTest()
        context.deleteDatabase("app.db")
    }

    @Test
    fun shouldUnwrapAnAttestedKeyAndReportHealthyPin() {
        val util = utility()
        wallet.edit().putString(WalletMasterKeyAttestation.SENTINEL_KEY, sentinel(util))
            .putString("pin_code", util.encrypt(key, "123456")).commit()
        assertArrayEquals(key, util.getPrerenceAesKey().encoded)
        assertEquals(PinHealth.HEALTHY, util.assessPinHealth())
        assertEquals("payload", util.decrypt(util.encrypt("payload")))
        assertTrue(WalletSecureStorageHealth.isHealthy())
    }

    @Test
    fun shouldKeepAnAuthenticatedKeyWhenThePinIsAbsentMalformedOrTooLarge() {
        val util = utility()
        wallet.edit().putString(WalletMasterKeyAttestation.SENTINEL_KEY, sentinel(util)).commit()
        assertEquals(PinHealth.ABSENT, util.assessPinHealth())
        for (value in listOf("broken", "v2:" + "A".repeat(5000), util.encrypt(key, "12ab56"))) {
            wallet.edit().putString("pin_code", value).commit()
            assertEquals(PinHealth.UNREADABLE, util.assessPinHealth())
            assertArrayEquals(key, util.getPrerenceAesKey().encoded)
        }
        wallet.edit().putInt("pin_code", 123456).commit()
        assertEquals(PinHealth.UNREADABLE, util.assessPinHealth())
    }

    @Test
    fun shouldBackfillSentinelOnlyAfterAnExistingPayloadAuthenticates() {
        val util = utility()
        val payload = util.encrypt(key, "archived payload")
        wallet.edit().putString("TON_CONNECT_session", payload).putString("pin_code", "broken").commit()
        assertArrayEquals(key, util.getPrerenceAesKey().encoded)
        val written = wallet.getString(WalletMasterKeyAttestation.SENTINEL_KEY, null)!!
        assertEquals(WalletMasterKeyAttestation.SENTINEL_PLAINTEXT, util.decrypt(key, written))
        assertEquals(payload, wallet.getString("TON_CONNECT_session", null))
        assertEquals(PinHealth.UNREADABLE, WalletPinHealth.health.value)
    }

    @Test
    fun shouldRejectCorruptCandidatesWithoutReplacingTheSentinel() {
        val util = utility()
        wallet.edit().putString(WalletMasterKeyAttestation.SENTINEL_KEY, "broken")
            .putInt("1:TON_SECRETS", 42).putString("TON_CONNECT_session", "invalid")
            .putString("pin_code", util.encrypt(key, "bad-pin")).commit()
        val before = wallet.all
        val failure = assertThrows(WalletSecureStorageUnavailableException::class.java) { util.getPrerenceAesKey() }
        assertEquals(WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS, failure.kind)
        assertEquals(before, wallet.all)
    }

    @Test
    fun shouldNotTreatAnArchivedMalformedPinAsProofOfTheMasterKey() {
        val util = utility()
        wallet.edit().putString(WalletSecretArchive.keyFor("pin_code"), util.encrypt(key, "bad-pin")).commit()
        assertThrows(WalletSecureStorageUnavailableException::class.java) { util.getPrerenceAesKey() }
        assertFalse(wallet.contains(WalletMasterKeyAttestation.SENTINEL_KEY))
        wallet.edit().putString(WalletSecretArchive.keyFor("pin_code"), util.encrypt(key, "123456")).commit()
        assertArrayEquals(key, util.getPrerenceAesKey().encoded)
        assertTrue(wallet.contains(WalletMasterKeyAttestation.SENTINEL_KEY))
    }

    @Test
    fun shouldCreateAndDurablyAttestAKeyOnlyForAnEmptyInstallation() {
        val util = utility(storeWrapped = false)
        val generated = util.getPrerenceAesKey().encoded
        assertEquals(32, generated.size)
        assertFalse(generated.contentEquals(key))
        assertTrue(wrapping.contains("secret_key"))
        assertEquals(WalletMasterKeyAttestation.SENTINEL_PLAINTEXT,
            util.decrypt(generated, wallet.getString(WalletMasterKeyAttestation.SENTINEL_KEY, "")!!))
        assertArrayEquals(generated, util.getPrerenceAesKey().encoded)
        assertEquals(PinHealth.ABSENT, WalletPinHealth.health.value)
    }

    @Test
    fun shouldRefuseANewKeyWhenEncryptedPreferencesOrWalletRowsExist() {
        wallet.edit().putString("pin_code", "existing").commit()
        val util = utility(storeWrapped = false)
        assertThrows(WalletSecureStorageUnavailableException::class.java) { util.getPrerenceAesKey() }
        wallet.edit().clear().commit()
        val file = context.getDatabasePath("app.db").apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.execSQL("CREATE TABLE meta_accounts (id INTEGER PRIMARY KEY)")
            it.execSQL("INSERT INTO meta_accounts VALUES (1)")
        }
        val failure = assertThrows(WalletSecureStorageUnavailableException::class.java) { util.getPrerenceAesKey() }
        assertEquals(WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS, failure.kind)
        assertFalse(wrapping.contains("secret_key"))
    }

    @Test
    fun shouldFailClosedForAnUnreadableExistingDatabase() {
        val file = context.getDatabasePath("app.db").apply { parentFile!!.mkdirs() }
        file.writeText("not a database")
        val util = utility(storeWrapped = false)
        assertThrows(WalletSecureStorageUnavailableException::class.java) { util.getPrerenceAesKey() }
        assertFalse(wrapping.contains("secret_key"))
    }

    @Test
    fun shouldRejectMalformedWrappedKeysAndUnknownActiveSlots() {
        val util = utility()
        for (value in listOf("A".repeat(4097), "invalid base64")) {
            wrapping.edit().putString("secret_key", value).commit()
            assertThrows(WalletSecureStorageUnavailableException::class.java) { util.getPrerenceAesKey() }
            assertEquals(value, wrapping.getString("secret_key", null))
        }
        wrapping.edit().putInt("active_slot", 99).commit()
        val failure = assertThrows(WalletSecureStorageUnavailableException::class.java) { util.getPrerenceAesKey() }
        assertEquals(WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS, failure.kind)
        assertTrue(failure.message!!.contains("Unknown active"))
    }

    @Test
    fun shouldUseTheFreshSlotWhenThePreviousKeyIsLostAndKeepPinRequired() {
        val util = utility(alias = "key_alias_v2", storeWrapped = false)
        wrapping.edit().putInt("active_slot", 2).putString("secret_key_v2", wrapped())
            .putString("wallet_master_key_attestation_v2", sentinel(util)).commit()
        assertArrayEquals(key, util.getPrerenceAesKey().encoded)
        assertEquals(PinHealth.UNREADABLE, WalletPinHealth.health.value)
        assertEquals("fresh payload", util.decrypt(util.encrypt(key, "fresh payload")))
    }

    @Test
    fun shouldRetainThePreviousSlotWhenTheFreshSlotIsMissing() {
        val util = utility()
        wrapping.edit().putInt("active_slot", 2).commit()
        wallet.edit().putString(WalletMasterKeyAttestation.SENTINEL_KEY, sentinel(util)).commit()
        assertArrayEquals(key, util.getPrerenceAesKey().encoded)
        assertEquals(PinHealth.UNREADABLE, WalletPinHealth.health.value)
        assertFalse(wrapping.contains("secret_key_v2"))
    }

    @Test
    fun shouldRejectFreshSlotsWhoseSentinelDoesNotAuthenticate() {
        val util = utility(alias = "key_alias_v2", storeWrapped = false)
        wrapping.edit().putInt("active_slot", 2).putString("secret_key_v2", wrapped())
            .putString("wallet_master_key_attestation_v2", util.encrypt(key, "wrong sentinel")).commit()
        val failure = assertThrows(WalletSecureStorageUnavailableException::class.java) { util.getPrerenceAesKey() }
        assertEquals(WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS, failure.kind)
        assertTrue(failure.message!!.contains("Neither"))
    }

    @Test
    fun shouldRequireTheSameLostKeyAcrossTwoBootsBeforeOfferingRecovery() {
        val util = EncryptionUtil(context)
        assertEquals(WalletKeyLossRetry.RestartDevice, util.retryPreviousSecureStorage(5, 1000))
        assertEquals(WalletKeyLossRetry.RestartDevice, util.retryPreviousSecureStorage(5, 2000))
        val consent = (util.retryPreviousSecureStorage(6, 3000) as WalletKeyLossRetry.CanStart).consent
        assertEquals(6, consent.bootCount)
        assertEquals(3000L, consent.issuedAtMillis)
        val before = wrapping.all
        assertThrows(IllegalArgumentException::class.java) { util.beginFreshKeySlot(consent, false, 6, 3001) }
        assertThrows(IllegalArgumentException::class.java) { util.beginFreshKeySlot(consent, true, 6, 400000) }
        assertEquals(before, wrapping.all)
        wrapping.edit().putInt("active_slot", 2).commit()
        assertEquals(WalletKeyLossRetry.RetryLater, util.retryPreviousSecureStorage(7, 4000))
        assertThrows(IllegalStateException::class.java) { util.beginFreshKeySlot(consent, true, 6, 3001) }
    }

    @Test
    fun shouldStopAllReadsAfterAnAmbiguousDurabilityFailure() {
        val util = utility()
        WalletSecureStorageHealth.latchProcessRestartRequired()
        val failure = assertThrows(WalletSecureStorageUnavailableException::class.java) { util.getPrerenceAesKey() }
        assertEquals(WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED, failure.kind)
        assertThrows(WalletSecureStorageUnavailableException::class.java) { util.retryPreviousSecureStorage(1, 1) }
        assertFalse(WalletSecureStorageHealth.isHealthy())
    }

    private fun utility(alias: String = "key_alias", storeWrapped: Boolean = true): EncryptionUtil {
        val util = EncryptionUtil(context)
        // Supply JVM-generated wrapping material; all preference, RSA, AES, and attestation operations stay real.
        for ((name, value) in mapOf("privateKey" to pair.private, "publicKey" to pair.public, "loadedAlias" to alias)) {
            EncryptionUtil::class.java.getDeclaredField(name).apply { isAccessible = true }.set(util, value)
        }
        if (storeWrapped) wrapping.edit().putString("secret_key", wrapped()).commit()
        return util
    }

    private fun wrapped(): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pair.public)
        return Base64.toBase64String(cipher.doFinal(key))
    }

    private fun sentinel(util: EncryptionUtil): String = util.encrypt(key, WalletMasterKeyAttestation.SENTINEL_PLAINTEXT)
}
