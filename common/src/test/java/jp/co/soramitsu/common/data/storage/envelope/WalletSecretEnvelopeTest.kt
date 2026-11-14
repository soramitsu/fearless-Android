package jp.co.soramitsu.common.data.storage.envelope

import java.security.SecureRandom
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSecretEnvelopeTest {

    private val random = SecureRandom()
    private val key = ByteArray(32) { it.toByte() }
    private val keyId = ByteArray(16) { (it + 1).toByte() }
    private val body = SyntheticSecrets.substrateSr25519(1)

    private fun header(
        name: String = "7:SUBSTRATE_SECRETS",
        scope: WalletSecretEnvelopeScope = WalletSecretEnvelopeScope.SUBSTRATE_ROOT,
        identity: ByteArray = byteArrayOf(9, 8, 7)
    ) = WalletSecretEnvelope.Header(keyId, scope, name, identity)

    @Test
    fun `a sealed value opens to the exact body and keeps its header`() {
        val sealed = WalletSecretEnvelope.seal(key, header(), body, random)

        assertTrue(sealed.startsWith(WalletSecretEnvelope.PREFIX))
        assertTrue(WalletSecretEnvelope.isSealed(sealed))
        assertFalse(sealed.contains(body))
        val opened = WalletSecretEnvelope.open(key, sealed)
        assertEquals(body, opened.body)
        assertEquals("7:SUBSTRATE_SECRETS", opened.header.keyName)
        assertEquals(WalletSecretEnvelopeScope.SUBSTRATE_ROOT, opened.header.scope)
        assertArrayEquals(byteArrayOf(9, 8, 7), opened.header.publicIdentity)
        assertTrue(opened.header.keyIdMatches(keyId))
        assertFalse(opened.toString().contains(body))
    }

    @Test
    fun `the header is readable without SK`() {
        val sealed = WalletSecretEnvelope.seal(key, header(name = "wallet_secret_archive:v1:7:TON_SECRETS"), body, random)
        val read = WalletSecretEnvelope.readHeader(sealed)
        assertEquals("wallet_secret_archive:v1:7:TON_SECRETS", read.keyName)
        assertArrayEquals(keyId, read.keyId)
    }

    @Test
    fun `two seals of one value differ but both open`() {
        val first = WalletSecretEnvelope.seal(key, header(), body, random)
        val second = WalletSecretEnvelope.seal(key, header(), body, random)
        assertNotEquals(first, second)
        assertEquals(body, WalletSecretEnvelope.open(key, first).body)
        assertEquals(body, WalletSecretEnvelope.open(key, second).body)
    }

    @Test
    fun `another SK never opens an envelope`() {
        val sealed = WalletSecretEnvelope.seal(key, header(), body, random)
        val other = ByteArray(32) { 5 }
        assertThrows(WalletSecretEnvelopeAuthenticationException::class.java) { WalletSecretEnvelope.open(other, sealed) }
    }

    @Test
    fun `every header field is authenticated`() {
        val sealed = WalletSecretEnvelope.seal(key, header(), body, random)
        val payload = Base64.getUrlDecoder().decode(sealed.removePrefix(WalletSecretEnvelope.PREFIX))
        // Version (0), key id (1..16) and scope (17) are covered by their own checks; flip a key-name byte,
        // an identity byte, a nonce byte and a ciphertext byte.
        val nameStart = 1 + 16 + 1 + 2
        val identityStart = nameStart + "7:SUBSTRATE_SECRETS".length + 2
        val nonceStart = identityStart + 3
        for (index in listOf(nameStart, identityStart, nonceStart, payload.size - 1)) {
            val tampered = payload.copyOf()
            tampered[index] = (tampered[index].toInt() xor 0x01).toByte()
            val value = WalletSecretEnvelope.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(tampered)
            assertThrows(Exception::class.java) { WalletSecretEnvelope.open(key, value) }
        }
    }

    @Test
    fun `malformed envelopes are format errors, never other values`() {
        val sealed = WalletSecretEnvelope.seal(key, header(), body, random)
        for (bad in listOf(
            "fse1:", "fse1:!!!", "fse1:AAAA=", sealed + "+",
            "fse1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(2) + ByteArray(40))
        )) {
            assertThrows(bad.take(12), WalletSecretEnvelopeFormatException::class.java) { WalletSecretEnvelope.readHeader(bad) }
        }
        assertThrows(WalletSecretEnvelopeFormatException::class.java) { WalletSecretEnvelope.readHeader(body) }
        // A truncated value may still parse, but it never opens.
        assertThrows(Exception::class.java) { WalletSecretEnvelope.open(key, sealed.dropLast(30)) }
    }

    @Test
    fun `only values with the prefix count as sealed`() {
        assertFalse(WalletSecretEnvelope.isSealed(body))
        assertFalse(WalletSecretEnvelope.isSealed(null))
        assertFalse(WalletSecretEnvelope.isSealed(""))
        assertFalse(WalletSecretEnvelope.isSealed("0xfse1:"))
        assertTrue(WalletSecretEnvelope.isSealed("fse1:anything"))
    }

    @Test
    fun `a sealed value is never sealed twice and headers are bounded`() {
        val sealed = WalletSecretEnvelope.seal(key, header(), body, random)
        assertThrows(IllegalArgumentException::class.java) { WalletSecretEnvelope.seal(key, header(), sealed, random) }
        assertThrows(IllegalArgumentException::class.java) { header(name = "") }
        assertThrows(IllegalArgumentException::class.java) { header(identity = ByteArray(257)) }
        assertThrows(IllegalArgumentException::class.java) { WalletSecretEnvelope.seal(ByteArray(16), header(), body, random) }
    }

    @Test
    fun `scope codes are stable`() {
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7, 8),
            WalletSecretEnvelopeScope.entries.map { it.code }
        )
        WalletSecretEnvelopeScope.entries.forEach { assertEquals(it, WalletSecretEnvelopeScope.fromCode(it.code)) }
        assertEquals(null, WalletSecretEnvelopeScope.fromCode(0))
    }
}
