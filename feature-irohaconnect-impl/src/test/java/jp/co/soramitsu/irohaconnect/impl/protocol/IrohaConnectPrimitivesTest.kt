package jp.co.soramitsu.irohaconnect.impl.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IrohaConnectPrimitivesTest {

    private fun reader(bytes: ByteArray, compact: Boolean) =
        NoritoReader(bytes, "test", compact) { message -> throw IllegalStateException(message) }

    @Test
    fun shouldEncodeLittleEndianIntegersAndFields_whenBuildingConnectFrames() {
        assertArrayEquals(byteArrayOf(0x34, 0x12), NoritoBytes.u16(0x1234))
        assertArrayEquals(byteArrayOf(4, 3, 2, 1), NoritoBytes.u32(0x01020304))
        assertArrayEquals(ByteArray(8) { -1 }, NoritoBytes.u64(-1))
        assertArrayEquals(byteArrayOf(2, 0, 0, 0, 0, 0, 0, 0, 7, 8), NoritoBytes.field(byteArrayOf(7, 8)))
        assertArrayEquals(byteArrayOf(5, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 9), NoritoBytes.tagged(5, byteArrayOf(9)))
        assertArrayEquals(byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0x2a), NoritoBytes.elementFramed(byteArrayOf(0x2a)))
        assertThrows(IllegalArgumentException::class.java) { NoritoBytes.u16(0x10000) }
        assertThrows(IllegalArgumentException::class.java) { NoritoBytes.u32(-1) }
    }

    @Test
    fun shouldWriteAndReadCanonicalLeb128Lengths_whenBuildingTransactionPayloads() {
        assertArrayEquals(byteArrayOf(0), NoritoBytes.compactLength(0))
        assertArrayEquals(byteArrayOf(0x7f), NoritoBytes.compactLength(127))
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x01), NoritoBytes.compactLength(128))
        assertArrayEquals(byteArrayOf(0xac.toByte(), 0x02), NoritoBytes.compactLength(300))
        val field = NoritoBytes.compactField(ByteArray(300) { 1 })
        assertEquals(300, reader(field, compact = true).readField("f").size)
        // An overlong prefix for the same length is refused.
        assertThrows(IllegalStateException::class.java) {
            reader(byteArrayOf(0x81.toByte(), 0x00, 9), compact = true).readField("f")
        }
        assertThrows(IllegalStateException::class.java) { reader(byteArrayOf(0x80.toByte()), compact = true).readField("f") }
        assertThrows(IllegalStateException::class.java) {
            reader(ByteArray(11) { 0x80.toByte() }, compact = true).readField("f")
        }
    }

    @Test
    fun shouldReadBoundedFieldsAndRefuseTrailingBytes_whenDecodingU64Layouts() {
        val frame = NoritoBytes.struct(listOf(byteArrayOf(1, 2), NoritoBytes.u32(7)))
        val r = reader(frame, compact = false)
        assertArrayEquals(byteArrayOf(1, 2), r.readField("a", 2))
        assertEquals(7L, reader(r.readField("b"), compact = false).readU32("b"))
        r.done()
        assertThrows(IllegalStateException::class.java) { reader(frame, compact = false).readField("a", 1) }
        assertThrows(IllegalStateException::class.java) {
            reader(frame + byteArrayOf(0), compact = false).apply {
                readField("a")
            readField("b")
            }.done()
        }
        assertThrows(IllegalStateException::class.java) { reader(NoritoBytes.u64(-1), compact = false).readField("huge") }
        assertEquals(0xffff, reader(byteArrayOf(-1, -1), compact = false).readU16("x"))
        assertEquals(255, reader(byteArrayOf(-1), compact = false).readU8("x"))
        assertEquals(-1L, reader(ByteArray(8) { -1 }, compact = false).readU64("x"))
        assertThrows(IllegalStateException::class.java) { reader(ByteArray(8) { -1 }, compact = false).readU64Length("x") }
        assertArrayEquals(byteArrayOf(9), reader(NoritoBytes.concat(NoritoBytes.u64(1), byteArrayOf(9)), compact = true).readFieldU64("frame"))
    }

    @Test
    fun shouldAcceptOnlyCanonicalUnpaddedBase64Url() {
        val bytes = ByteArray(32) { it.toByte() }
        val text = IrohaConnectEncoding.toBase64Url(bytes)
        assertArrayEquals(bytes, IrohaConnectEncoding.fromCanonicalBase64Url(text))
        assertNull(IrohaConnectEncoding.fromCanonicalBase64Url("$text="))
        // Standard-alphabet characters are not base64url.
        val standardAlphabet = IrohaConnectEncoding.toBase64Url(ByteArray(32) { -1 }).replace('_', '/')
        assertNull(IrohaConnectEncoding.fromCanonicalBase64Url(standardAlphabet))
        // The last character carries unused bits that a canonical spelling leaves at zero.
        assertNull(IrohaConnectEncoding.fromCanonicalBase64Url(text.dropLast(1) + "B"))
        assertNull(IrohaConnectEncoding.fromCanonicalBase64Url(""))
        assertEquals("0001ff", IrohaConnectEncoding.hex(byteArrayOf(0, 1, -1)))
        assertArrayEquals(byteArrayOf(0, 1, -1), IrohaConnectEncoding.unhex("0x0001FF"))
        assertThrows(IllegalArgumentException::class.java) { IrohaConnectEncoding.unhex("0g") }
    }

    @Test
    fun shouldProduceKnownDigestsAndRefuseBadKeys_whenUsingCryptoPrimitives() {
        assertEquals(
            "af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262",
            IrohaConnectEncoding.hex(IrohaConnectCrypto.blake3(ByteArray(0)))
        )
        assertEquals(
            "0e5751c026e543b2e8ab2eb06099daa1d1e5df47778f7787faab45cdf12fe3a8",
            IrohaConnectEncoding.hex(IrohaConnectCrypto.blake2b256(ByteArray(0)))
        )
        val marked = IrohaConnectCrypto.irohaHash(ByteArray(0))
        assertEquals(1, marked.last().toInt() and 1)
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            IrohaConnectEncoding.hex(IrohaConnectCrypto.sha256(ByteArray(0)))
        )
        // X25519 refuses a low-order peer key whose shared secret is all zeroes.
        assertThrows(IllegalArgumentException::class.java) {
            IrohaConnectCrypto.x25519SharedSecret(ByteArray(32) { 1 }, ByteArray(32))
        }
        val (privateKey, publicKey) = IrohaConnectCrypto.x25519KeyPair()
        assertArrayEquals(publicKey, IrohaConnectCrypto.x25519PublicKey(privateKey))
        val key = ByteArray(32) { 3 }
        val nonce = ByteArray(12)
        val sealed = IrohaConnectCrypto.chaCha20Poly1305Seal(key, nonce, byteArrayOf(1), byteArrayOf(4, 5))
        assertArrayEquals(byteArrayOf(4, 5), IrohaConnectCrypto.chaCha20Poly1305Open(key, nonce, byteArrayOf(1), sealed))
        assertNull(IrohaConnectCrypto.chaCha20Poly1305Open(key, nonce, byteArrayOf(2), sealed))
        val seed = ByteArray(32) { 9 }
        val signature = IrohaConnectCrypto.ed25519Sign(seed, byteArrayOf(1, 2))
        assertTrue(IrohaConnectCrypto.ed25519Verify(IrohaConnectCrypto.ed25519PublicKey(seed), byteArrayOf(1, 2), signature))
        assertFalse(IrohaConnectCrypto.ed25519Verify(IrohaConnectCrypto.ed25519PublicKey(seed), byteArrayOf(1, 3), signature))
        assertFalse(IrohaConnectCrypto.ed25519Verify(ByteArray(31), byteArrayOf(1), signature))
        assertEquals(16, IrohaConnectCrypto.randomBytes(16).size)
        assertEquals(32, IrohaConnectCrypto.hkdfSha256(key, byteArrayOf(1), byteArrayOf(2), 32).size)
    }

    @Test
    fun shouldTreatJavaScriptWhitespaceAsUntrimmed_whenCheckingDisplayText() {
        assertTrue(IrohaConnectWire.isDisplayText("Hanami Market"))
        listOf("", " a", "a ", "﻿a", "a　", "a\u0001b", "a\u0085b").forEach {
            assertFalse(it, IrohaConnectWire.isDisplayText(it))
        }
        assertThrows(IllegalArgumentException::class.java) { IrohaConnectWire.noritoString("x".repeat(9), "label", 8) }
    }
}
