package jp.co.soramitsu.walletconnect.impl.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Vectors: the RFC 7748 X25519 key pairs, with the symmetric key, topic, envelopes and message hash
 * produced by the official `@walletconnect/utils` 2.26.0 (`deriveSymKey`, `hashKey`, `encrypt`, `hashMessage`).
 */
class WcCryptoTest {

    @Test
    fun `key agreement derives the symmetric key the official SDK derives, from either side`() {
        assertEquals(SYM_KEY, WcEncoding.hex(WcCrypto.deriveSymKey(unhex(ALICE_PRIVATE), unhex(BOB_PUBLIC))))
        assertEquals(SYM_KEY, WcEncoding.hex(WcCrypto.deriveSymKey(unhex(BOB_PRIVATE), unhex(ALICE_PUBLIC))))
    }

    @Test
    fun `topic is the sha256 of the symmetric key`() {
        assertEquals(TOPIC, WcCrypto.topicOf(unhex(SYM_KEY)))
    }

    @Test
    fun `public key is derived from the private key`() {
        assertEquals(ALICE_PUBLIC, WcEncoding.hex(WcCrypto.publicKeyOf(unhex(ALICE_PRIVATE))))
    }

    @Test
    fun `generated key pairs are consistent and fresh`() {
        val first = WcCrypto.generateKeyPair()
        val second = WcCrypto.generateKeyPair()

        assertArrayEquals(first.publicKey, WcCrypto.publicKeyOf(first.privateKey))
        assertNotEquals(WcEncoding.hex(first.privateKey), WcEncoding.hex(second.privateKey))
        assertEquals(WcCrypto.KEY_LENGTH, first.publicKey.size)
    }

    @Test
    fun `random bytes have the requested length and differ`() {
        assertEquals(12, WcCrypto.randomBytes(12).size)
        assertNotEquals(WcEncoding.hex(WcCrypto.randomBytes(32)), WcEncoding.hex(WcCrypto.randomBytes(32)))
    }

    @Test
    fun `a low order peer key is refused instead of yielding a zero secret`() {
        assertThrows(WcCryptoException::class.java) {
            WcCrypto.deriveSymKey(unhex(ALICE_PRIVATE), ByteArray(WcCrypto.KEY_LENGTH))
        }
        assertThrows(WcCryptoException::class.java) {
            WcCrypto.deriveSymKey(unhex(ALICE_PRIVATE), ByteArray(31))
        }
    }

    @Test
    fun `type 0 envelope encodes byte for byte as the official SDK`() {
        val encoded = WcCrypto.encodeType0(unhex(SYM_KEY), MESSAGE, unhex(IV))

        assertEquals(TYPE_0, encoded)
        assertEquals(MESSAGE, WcCrypto.decodeType0(unhex(SYM_KEY), WcCrypto.parseEnvelope(TYPE_0)))
    }

    @Test
    fun `type 1 envelope carries the sender key and decodes with the receiver key`() {
        val sender = WcKeyPair(unhex(ALICE_PRIVATE), unhex(ALICE_PUBLIC))
        val encoded = WcCrypto.encodeType1(sender, unhex(BOB_PUBLIC), MESSAGE, unhex(IV))

        assertEquals(TYPE_1, encoded)
        val envelope = WcCrypto.parseEnvelope(TYPE_1)
        assertEquals(WcCrypto.TYPE_1, envelope.type)
        assertArrayEquals(unhex(ALICE_PUBLIC), envelope.senderPublicKey)
        assertEquals(MESSAGE, WcCrypto.decodeType1(unhex(BOB_PRIVATE), envelope))
    }

    @Test
    fun `a tampered envelope or a wrong key is refused`() {
        val bytes = WcEncoding.unbase64(TYPE_0)
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 1).toByte()

        assertThrows(WcCryptoException::class.java) {
            WcCrypto.decodeType0(unhex(SYM_KEY), WcCrypto.parseEnvelope(WcEncoding.base64(bytes)))
        }
        assertThrows(WcCryptoException::class.java) {
            WcCrypto.decodeType0(ByteArray(32) { 7 }, WcCrypto.parseEnvelope(TYPE_0))
        }
        assertThrows(WcCryptoException::class.java) {
            WcCrypto.decodeType0(unhex(SYM_KEY), WcCrypto.parseEnvelope(TYPE_1))
        }
    }

    @Test
    fun `malformed envelopes are refused`() {
        assertThrows(WcCryptoException::class.java) { WcCrypto.parseEnvelope("not base64!") }
        assertThrows(WcCryptoException::class.java) { WcCrypto.parseEnvelope("") }
        assertThrows(WcCryptoException::class.java) { WcCrypto.parseEnvelope(WcEncoding.base64(byteArrayOf(0, 1, 2))) }
        assertThrows(WcCryptoException::class.java) { WcCrypto.parseEnvelope(WcEncoding.base64(ByteArray(64) { 2 })) }
    }

    @Test
    fun `seal and open round trip`() {
        val key = WcCrypto.randomBytes(32)
        val iv = WcCrypto.randomBytes(12)
        val sealed = WcCrypto.seal(key, iv, "payload".toByteArray())

        assertEquals("payload", String(WcCrypto.open(key, iv, sealed)))
        assertThrows(WcCryptoException::class.java) { WcCrypto.open(key, iv, ByteArray(4)) }
    }

    @Test
    fun `message hash matches the Verify id the official SDK computes`() {
        assertEquals(MESSAGE_HASH, WcCrypto.hashMessage(TYPE_0))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            WcEncoding.hex(WcCrypto.sha256(ByteArray(0)))
        )
    }

    private fun unhex(hex: String) = WcEncoding.unhex(hex)

    companion object {
        const val ALICE_PRIVATE = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
        const val ALICE_PUBLIC = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"
        const val BOB_PRIVATE = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"
        const val BOB_PUBLIC = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"
        const val SYM_KEY = "ea1d8a20f476d1e1ec952ca42708b8f7161ce7c81eadf97e520e2b40333decd5"
        const val TOPIC = "1ca1d70db64cab0f93de5934e27f7114e8e9fd7dd3c7145d81ce7f2dd2cd05c8"
        const val IV = "000102030405060708090a0b"
        const val MESSAGE = """{"id":1,"jsonrpc":"2.0","method":"wc_sessionPing","params":{}}"""
        const val TYPE_0 = "AAABAgMEBQYHCAkKC1NpmVwoe1LoBKBdzMFtbY6qYKa2PRkZ4zOs55VRUm6CaXZJLLvgLytXMlKUqgTVeI0u4hTO8kwfaVwDIERjQorYy/tllqI/rSrjTCb9Cg=="
        const val TYPE_1 = "AYUg8AmJMKdUdIt93LQ+91oNvzoNJjga9OukqY6qm05qAAECAwQFBgcICQoLU2mZXCh7UugEoF3MwW1tjqpgprY9GRnjM6znlVFSboJpdkksu+AvK1cyUpSqBNV4jS7iFM7yTB9pXAMgRGNCitjL+2WWoj+tKuNMJv0K"
        const val MESSAGE_HASH = "9773f8583dc0808cc74e57eaa864105ba9289f6c9f44b7a58703125950672f32"
    }
}
