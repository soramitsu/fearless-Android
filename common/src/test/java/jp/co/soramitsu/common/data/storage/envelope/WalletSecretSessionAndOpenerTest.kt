package jp.co.soramitsu.common.data.storage.envelope

import java.security.SecureRandom
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletSecretSessionAndOpenerTest {
    private val keyId = ByteArray(16) { 1 }
    private val key = ByteArray(32) { 2 }
    private val value = SyntheticSecrets.substrateSr25519(3)
    private val sealed = WalletSecretEnvelope.seal(
        key,
        WalletSecretEnvelope.Header(keyId, WalletSecretEnvelopeScope.SUBSTRATE_ROOT, "1:SUBSTRATE_SECRETS", ByteArray(0)),
        value,
        SecureRandom()
    )

    @Before
    fun setUp() = resetEnvelopeGlobals()

    @After
    fun tearDown() = resetEnvelopeGlobals()

    @Test
    fun `a lock zeroes SK and sealed values stop opening`() {
        WalletSecretSession.open(keyId, key, WalletSecretSession.Origin.PIN_WRAP)
        assertTrue(WalletSecretSession.isOpen())
        assertEquals(WalletSecretSession.Origin.PIN_WRAP, WalletSecretSession.origin())
        assertArrayEquals(keyId, WalletSecretSession.keyId())
        assertEquals(value, WalletSecretEnvelopes.open(sealed))
        assertFalse(WalletSecretEnvelopes.isLockedFor(sealed))

        WalletSessionLock.lock()

        assertFalse(WalletSecretSession.isOpen())
        assertNull(WalletSecretSession.keyId())
        assertNull(WalletSecretSession.withKey { _, _ -> error("no key while locked") })
        assertThrows(WalletSecretLockedException::class.java) { WalletSecretEnvelopes.open(sealed) }
        assertNull(WalletSecretEnvelopes.openOrNull(sealed))
        assertTrue(WalletSecretEnvelopes.isLockedFor(sealed))
    }

    @Test
    fun `operations get copies that are zeroed afterwards`() {
        WalletSecretSession.open(keyId, key, WalletSecretSession.Origin.NEW_KEYRING)
        var seen: ByteArray? = null
        WalletSecretSession.withKey { _, copy ->
            seen = copy
            assertArrayEquals(key, copy)
        }
        assertArrayEquals(ByteArray(32), seen)
        // The session's own copy is untouched by an operation's cleanup.
        assertEquals(value, WalletSecretEnvelopes.open(sealed))
    }

    @Test
    fun `every opening is counted for the unlock maintenance`() {
        val before = WalletSecretSession.openings.value
        WalletSecretSession.open(keyId, key, WalletSecretSession.Origin.PIN_WRAP)
        WalletSecretSession.open(keyId, key, WalletSecretSession.Origin.PIN_WRAP)
        assertEquals(before + 2, WalletSecretSession.openings.value)
    }

    @Test
    fun `values that are not sealed pass through unchanged, locked or not`() {
        assertEquals(value, WalletSecretEnvelopes.open(value))
        assertEquals(value, WalletSecretEnvelopes.open(value, keyId, key))
        assertFalse(WalletSecretEnvelopes.isLockedFor(value))
        assertFalse(WalletSecretEnvelopes.isLockedFor(null))
    }

    @Test
    fun `another SK or a damaged envelope is unopenable, never corrupt`() {
        WalletSecretSession.open(ByteArray(16) { 9 }, ByteArray(32) { 9 }, WalletSecretSession.Origin.PIN_WRAP)
        assertThrows(WalletSecretUnopenableException::class.java) { WalletSecretEnvelopes.open(sealed) }
        assertThrows(WalletSecretUnopenableException::class.java) { WalletSecretEnvelopes.open("fse1:@@@") }
        assertThrows(WalletSecretUnopenableException::class.java) { WalletSecretEnvelopes.open(sealed, keyId, ByteArray(32) { 7 }) }
        assertEquals(value, WalletSecretEnvelopes.open(sealed, keyId, key))
    }

    @Test
    fun `the header records the public key a pass needs without SK`() {
        val identity = WalletSecretPayloads.publicIdentity(WalletSecretEnvelopeScope.SUBSTRATE_ROOT, value)
        assertArrayEquals(SyntheticSecrets.bytes(103), identity)
        assertArrayEquals(ByteArray(0), WalletSecretPayloads.publicIdentity(WalletSecretEnvelopeScope.TON_ROOT, "0x00"))
        assertArrayEquals(ByteArray(0), WalletSecretPayloads.publicIdentity(WalletSecretEnvelopeScope.SUBSTRATE_ROOT, sealed))
        assertEquals(WalletRootKeyKind.SR25519, WalletSecretPayloads.inferSubstrateKind(ByteArray(32), ByteArray(32)))
        assertEquals(WalletRootKeyKind.SECP256K1, WalletSecretPayloads.inferSubstrateKind(ByteArray(33), null))
        assertEquals(WalletRootKeyKind.ED25519, WalletSecretPayloads.inferSubstrateKind(ByteArray(32), null))
    }
}
