package jp.co.soramitsu.common.data.storage.pin

import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletMasterKeyAttestation
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletEmergencyAuthenticatorTest {

    private val preferences = InMemoryPreferences()
    private val encryptedPreferences = HashMapEncryptedPreferences()
    private var sinceBoot = 1_000_000L
    private val wallClock = 1_800_000_000_000L
    private val clock = PinAttemptClock(wallClock = { wallClock }, sinceBoot = { sinceBoot })
    private val authenticator = WalletEmergencyAuthenticator(preferences, encryptedPreferences, clock)

    @Test
    fun `the wallet PIN unlocks without the wallet database`() {
        encryptedPreferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, PIN)

        assertEquals(WalletEmergencyAuthentication.WrongPin, authenticator.authenticate("000000"))
        assertEquals(WalletEmergencyAuthentication.Authenticated, authenticator.authenticate(PIN))
    }

    @Test
    fun `failed guesses share the PIN screen lockout`() {
        encryptedPreferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, PIN)
        // Four failures on the PIN screen, one here: the fifth delays both.
        val screenLimiter = PinAttemptLimiter(preferences, clock)
        repeat(4) { screenLimiter.registerFailure() }

        assertEquals(WalletEmergencyAuthentication.Locked(30_000), authenticator.authenticate("000000"))
        // Even the right PIN waits for the delay.
        sinceBoot += 10_000
        assertEquals(WalletEmergencyAuthentication.Locked(20_000), authenticator.authenticate(PIN))
        assertEquals(20_000L, screenLimiter.remainingDelayMillis())

        sinceBoot += 20_000
        assertEquals(WalletEmergencyAuthentication.Authenticated, authenticator.authenticate(PIN))
        assertEquals("The emergency success clears the PIN screen's count", 0L, screenLimiter.registerFailure())
    }

    @Test
    fun `emergency guesses delay the PIN screen and keep counting`() {
        encryptedPreferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, PIN)
        repeat(4) { assertEquals(WalletEmergencyAuthentication.WrongPin, authenticator.authenticate("000000")) }
        assertEquals(WalletEmergencyAuthentication.Locked(30_000), authenticator.authenticate("000000"))
        val screenLimiter = PinAttemptLimiter(preferences, clock)
        assertEquals(30_000L, screenLimiter.remainingDelayMillis())

        sinceBoot += 30_000
        assertEquals(WalletEmergencyAuthentication.Locked(60_000), authenticator.authenticate("000000"))
        sinceBoot += 60_000
        assertEquals("The PIN screen continues the count", 120_000L, screenLimiter.registerFailure())
        assertEquals(WalletEmergencyAuthentication.Locked(120_000), authenticator.authenticate(PIN))
    }

    @Test
    fun `lock doubles and is capped at five minutes`() {
        encryptedPreferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, PIN)
        val expected = listOf(30_000L, 60_000, 120_000, 240_000, 300_000, 300_000, 300_000)
        repeat(4) { authenticator.authenticate("000000") }
        expected.forEach { delay ->
            assertEquals(WalletEmergencyAuthentication.Locked(delay), authenticator.authenticate("000000"))
            sinceBoot += delay
        }
    }

    @Test
    fun `no PIN is read while a delay runs`() {
        encryptedPreferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, PIN)
        val reads = mutableListOf<String>()
        val observed = object : EncryptedPreferences by encryptedPreferences {
            override fun getDecryptedString(field: String): String? {
                reads += field
                return encryptedPreferences.getDecryptedString(field)
            }
        }
        val observedAuthenticator = WalletEmergencyAuthenticator(preferences, observed, clock)
        repeat(5) { observedAuthenticator.authenticate("000000") }
        assertEquals(5, reads.size)

        repeat(3) { assertEquals(WalletEmergencyAuthentication.Locked(30_000), observedAuthenticator.authenticate(PIN)) }
        assertEquals(5, reads.size)
    }

    @Test
    fun `an unreadable PIN is no wrong guess`() {
        encryptedPreferences.putEncryptedString(WalletMasterKeyAttestation.PIN_CODE_KEY, "")
        repeat(10) { assertEquals(WalletEmergencyAuthentication.Unavailable, authenticator.authenticate("000000")) }
        assertEquals(0L, PinAttemptLimiter(preferences, clock).remainingDelayMillis())
        assertEquals(0L, PinAttemptLimiter(preferences, clock).registerFailure())
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
            WalletEmergencyAuthenticator(preferences, wrongType, clock).authenticate(PIN))
        val providerFailure = object : jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences by encryptedPreferences {
            override fun getDecryptedString(field: String): String? = throw jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException("test provider failure")
        }
        org.junit.Assert.assertThrows(jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException::class.java) {
            WalletEmergencyAuthenticator(preferences, providerFailure, clock).authenticate(PIN)
        }
    }

    private companion object {
        const val PIN = "135790"
    }
}
