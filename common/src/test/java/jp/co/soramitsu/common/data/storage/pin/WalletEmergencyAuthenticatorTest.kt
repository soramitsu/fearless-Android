package jp.co.soramitsu.common.data.storage.pin

import jp.co.soramitsu.common.data.storage.encrypt.WalletMasterKeyAttestation
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletEmergencyAuthenticatorTest {

    private val preferences = InMemoryPreferences()
    private val encryptedPreferences = HashMapEncryptedPreferences()
    private var now = 1_000_000L
    private val authenticator = WalletEmergencyAuthenticator(preferences, encryptedPreferences) { now }

    @Test
    fun `the wallet PIN unlocks without the wallet database`() {
        encryptedPreferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, PIN)

        assertEquals(WalletEmergencyAuthentication.WrongPin, authenticator.authenticate("000000"))
        assertEquals(WalletEmergencyAuthentication.Authenticated, authenticator.authenticate(PIN))
    }

    @Test
    fun `failed guesses share the PIN screen lockout`() {
        encryptedPreferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, PIN)
        // Four failures on the PIN screen, one here: the fifth locks both.
        val screenLimiter = PinAttemptLimiter(preferences) { now }
        repeat(4) { screenLimiter.registerFailure() }

        assertEquals(
            WalletEmergencyAuthentication.LockedUntil(now + 30_000),
            authenticator.authenticate("000000")
        )
        // Even the right PIN waits for the lock.
        assertEquals(
            WalletEmergencyAuthentication.LockedUntil(now + 30_000),
            authenticator.authenticate(PIN)
        )
        assertEquals(now + 30_000, screenLimiter.lockedUntil())

        now += 30_000
        assertEquals(WalletEmergencyAuthentication.Authenticated, authenticator.authenticate(PIN))
        assertEquals(null, screenLimiter.lockedUntil())
    }

    @Test
    fun `lock doubles and is capped at fifteen minutes`() {
        val limiter = PinAttemptLimiter(preferences) { now }
        repeat(5) { limiter.registerFailure() }
        assertEquals(now + 30_000, limiter.lockedUntil())
        limiter.registerFailure()
        assertEquals(now + 60_000, limiter.lockedUntil())
        repeat(20) { limiter.registerFailure() }
        assertEquals(now + 15 * 60_000, limiter.lockedUntil())
    }

    @Test
    fun `nothing is revealed when no PIN can be read`() {
        assertEquals(WalletEmergencyAuthentication.Unavailable, authenticator.authenticate(PIN))
        // An undecryptable PIN reads as empty on this device.
        encryptedPreferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, "")
        assertEquals(WalletEmergencyAuthentication.Unavailable, authenticator.authenticate(""))
    }

    @Test
    fun `matching malformed PIN text cannot unlock emergency export`() {
        listOf("12345", "1234567", "abcdef", "１２３４５６").forEach { malformed ->
            encryptedPreferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, malformed)
            assertEquals(WalletEmergencyAuthentication.Unavailable, authenticator.authenticate(malformed))
        }
    }

    @Test
    fun `wrong preference type is unavailable while provider errors still propagate`() {
        val wrongType = object : jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences by encryptedPreferences {
            override fun getDecryptedString(field: String): String? = throw ClassCastException("test wrong type")
        }
        assertEquals(WalletEmergencyAuthentication.Unavailable,
            WalletEmergencyAuthenticator(preferences, wrongType) { now }.authenticate(PIN))
        val providerFailure = object : jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences by encryptedPreferences {
            override fun getDecryptedString(field: String): String? = throw jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException("test provider failure")
        }
        org.junit.Assert.assertThrows(jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException::class.java) {
            WalletEmergencyAuthenticator(preferences, providerFailure) { now }.authenticate(PIN)
        }
    }

    private companion object {
        const val PIN = "135790"
    }
}
