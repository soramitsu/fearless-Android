package jp.co.soramitsu.common.data.storage.envelope

import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A keyring whose PIN wrap can never open again while no value is sealed holds
 * nothing: the legacy PIN keeps answering and a correct PIN starts a new SK, so
 * there is never a lockout while values are unsealed. Once anything is sealed,
 * only key material (a backup proof) reopens SK.
 */
class WalletPinDisposableKeyringTest {

    @Before
    fun setUp() {
        resetEnvelopeGlobals()
        WalletSecureStorageHealth.resetForTest()
    }

    @After
    fun tearDown() {
        resetEnvelopeGlobals()
        WalletSecureStorageHealth.resetForTest()
    }

    private val secrets = linkedMapOf(
        "7:SUBSTRATE_SECRETS" to SyntheticSecrets.substrateSr25519(1),
        "7:ETHEREUM_SECRETS" to SyntheticSecrets.ethereum(2)
    )

    /** 4.3.1 storage whose first 4.3.2 unlock created the keyring, then died before sealing anything. */
    private fun keyringCreatedNothingSealed(pin: String = PIN): MapSharedPreferences {
        val first = EnvelopeStorageHarness()
        first.putLegacy("pin_code", pin)
        secrets.forEach(first::putLegacy)
        assertEquals(PinVerification.CORRECT, first.authority.verify(pin))
        assertTrue(first.keyring.exists())
        secrets.keys.forEach { assertFalse(WalletSecretEnvelope.isSealed(first.inner(it))) }
        return first.shared
    }

    /** A new process over [shared]: nothing in memory. */
    private fun restart(shared: MapSharedPreferences): EnvelopeStorageHarness {
        resetEnvelopeGlobals()
        WalletSecureStorageHealth.resetForTest()
        return EnvelopeStorageHarness(shared = shared)
    }

    @Test
    fun `a lost or corrupt factor before anything is sealed falls back to the legacy PIN and starts a new SK`(): Unit = runBlocking {
        for (damage in listOf(EnvelopeStorageHarness::loseFactor, EnvelopeStorageHarness::corruptFactor)) {
            val shared = keyringCreatedNothingSealed()
            val harness = restart(shared)
            damage(harness)
            val oldId = checkNotNull(harness.keyring.currentKeyId())
            assertEquals(WalletSecretKeyring.PinWrapState.UNUSABLE, harness.keyring.pinWrapState())

            assertEquals(PinHealth.HEALTHY, harness.authority.health())
            assertFalse(harness.authority.recoveryNeedsKeyMaterial())
            assertEquals(PinVerification.WRONG, harness.authority.verify("000000"))
            assertFalse(WalletSecretSession.isOpen())
            assertEquals(PinVerification.CORRECT, harness.authority.verify(PIN))

            // A new SK under the PIN just proved; the old SK's records stay where they were.
            val newId = checkNotNull(harness.keyring.currentKeyId())
            assertFalse(oldId.contentEquals(newId))
            assertTrue(harness.preferences.contains(WalletSecretKeyring.pinKey(oldId)))
            assertEquals(WalletSecretSession.Origin.NEW_KEYRING, WalletSecretSession.origin())
            assertArrayEquals(newId, WalletSecretSession.keyId())
            assertEquals(WalletSecretKeyring.PinWrapState.USABLE, harness.keyring.pinWrapState())

            // The unlock migration then seals under the new SK and retires the legacy PIN as usual.
            val report = harness.migration.run()
            assertEquals(secrets.size, report.sealed)
            assertTrue(report.legacyPinRetired)
            secrets.forEach { (key, value) -> assertEquals(key, value, WalletSecretEnvelopes.open(checkNotNull(harness.inner(key)))) }
            WalletSecretSession.clear()
            assertEquals(PinVerification.CORRECT, harness.authority.verify(PIN))
        }
    }

    @Test
    fun `emergency access with a disposable keyring checks the legacy PIN and changes nothing`() {
        val harness = restart(keyringCreatedNothingSealed())
        harness.loseFactor()
        val before = harness.shared.values.toMap()

        assertEquals(PinVerification.WRONG to null, harness.authority.verifyForEmergency("000000"))
        assertEquals(PinVerification.CORRECT to null, harness.authority.verifyForEmergency(PIN))
        assertEquals(before, harness.shared.values.toMap())
        assertFalse(WalletSecretSession.isOpen())
    }

    @Test
    fun `a disposable keyring with an unreadable legacy PIN is recovered by a proof without key material`() {
        val harness = restart(keyringCreatedNothingSealed())
        harness.loseFactor()
        harness.putLegacy("pin_code", "12x")
        val oldId = checkNotNull(harness.keyring.currentKeyId())

        assertEquals(PinHealth.UNREADABLE, harness.authority.health())
        assertEquals(PinVerification.UNREADABLE, harness.authority.verify(PIN))
        assertFalse(harness.authority.recoveryNeedsKeyMaterial())

        harness.authority.saveRecovered("246813", harness.authority.captureResetSnapshot(), null, forgotten = false)

        assertFalse(oldId.contentEquals(checkNotNull(harness.keyring.currentKeyId())))
        assertTrue(WalletSecretSession.isOpen())
        WalletSecretSession.clear()
        assertEquals(PinHealth.HEALTHY, harness.authority.health())
        assertEquals(PinVerification.CORRECT, harness.authority.verify("246813"))
    }

    @Test
    fun `a PIN change with a disposable keyring changes the legacy PIN and starts a new SK`() {
        val harness = restart(keyringCreatedNothingSealed())
        harness.loseFactor()
        assertNull(harness.authority.sessionKey())

        harness.authority.save("135791")

        assertEquals(WalletSecretKeyring.PinWrapState.USABLE, harness.keyring.pinWrapState())
        WalletSecretSession.clear()
        assertEquals(PinVerification.WRONG, harness.authority.verify(PIN))
        assertEquals(PinVerification.CORRECT, harness.authority.verify("135791"))
    }

    @Test
    fun `once anything is sealed a lost factor needs key material, and nothing falls back`(): Unit = runBlocking {
        val harness = restart(keyringCreatedNothingSealed())
        assertEquals(PinVerification.CORRECT, harness.authority.verify(PIN))
        // Seal one value only; the legacy PIN is still there.
        harness.storage.putEncryptedString("8:TON_SECRETS", SyntheticSecrets.ton(3))
        assertTrue(WalletSecretEnvelope.isSealed(harness.inner("8:TON_SECRETS")))
        WalletSecretSession.clear()
        harness.loseFactor()

        assertEquals(PinHealth.UNREADABLE, harness.authority.health())
        assertEquals(PinVerification.UNREADABLE, harness.authority.verify(PIN))
        assertEquals(PinVerification.UNREADABLE to null, harness.authority.verifyForEmergency(PIN))
        assertTrue(harness.authority.recoveryNeedsKeyMaterial())
        assertThrows(IllegalStateException::class.java) {
            harness.authority.saveRecovered("246813", harness.authority.captureResetSnapshot(), null, forgotten = false)
        }
        assertThrows(IllegalStateException::class.java) { harness.authority.save("246813") }
    }

    @Test
    fun `a usable wrap is never replaced`() {
        val harness = restart(keyringCreatedNothingSealed())
        val current = checkNotNull(harness.keyring.currentKeyId())
        assertThrows(IllegalStateException::class.java) { harness.keyring.replaceUnusable(PIN, current) }
        assertArrayEquals(current, harness.keyring.currentKeyId())
    }

    private companion object {
        const val PIN = "604917"
    }
}
