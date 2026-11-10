package jp.co.soramitsu.fearless_utils.encrypt

import java.security.MessageDigest
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises libsr25519java, built from runtime-utils/native/sr25519, through
 * the real JNI class. The digests and signatures below were produced by the
 * library that Android 4.3.1 and earlier shipped (schnorrkel 0.9.1); every
 * value comes from the public Substrate development phrase.
 */
class Sr25519NativeTest {

    @Before
    fun requireHostLibrary() = Sr25519HostLibrary.assumeAvailable()

    @Test
    fun `keypair from the development seed matches Substrate and the legacy library`() {
        val keypair = Sr25519.keypairFromSeed(DEV_SEED)

        assertEquals(Sr25519.KEYPAIR_SIZE, keypair.size)
        assertEquals(DEV_PUBLIC, keypair.publicPart().hex())
        assertEquals(LEGACY_ROOT_KEYPAIR_SHA256, sha256(keypair))
    }

    @Test
    fun `alice derives from the development phrase`() {
        val seed = SubstrateSeedFactory.deriveSeed(DEV_PHRASE, null).seed.copyOf(Sr25519.SEED_SIZE)
        val path = SubstrateJunctionDecoder.decode("//Alice")

        val keypair = SubstrateKeypairFactory.generate(EncryptionType.SR25519, seed, path.junctions)

        assertEquals(ALICE_PUBLIC, keypair.publicKey.hex())
    }

    @Test
    fun `hard derivation is deterministic and matches the legacy library`() {
        val root = Sr25519.keypairFromSeed(DEV_SEED)

        val alice = Sr25519.deriveKeypairHard(root, chainCode("Alice"))

        assertEquals(ALICE_PUBLIC, alice.publicPart().hex())
        assertEquals(LEGACY_ALICE_KEYPAIR_SHA256, sha256(alice))
        assertArrayEquals(alice, Sr25519.deriveKeypairHard(root, chainCode("Alice")))
    }

    @Test
    fun `soft derivation keeps the legacy scalar and public key and draws a new nonce`() {
        val root = Sr25519.keypairFromSeed(DEV_SEED)

        val first = Sr25519.deriveKeypairSoft(root, chainCode("foo"))
        val second = Sr25519.deriveKeypairSoft(root, chainCode("foo"))

        assertEquals(FOO_SOFT_PUBLIC, first.publicPart().hex())
        assertEquals(LEGACY_FOO_SCALAR_AND_PUBLIC_SHA256, sha256(first.copyOfRange(0, 32), first.publicPart()))
        assertArrayEquals(first.copyOfRange(0, 32), second.copyOfRange(0, 32))
        assertFalse(first.copyOfRange(32, 64).contentEquals(second.copyOfRange(32, 64)))
    }

    @Test
    fun `public soft derivation matches keypair soft derivation`() {
        val root = Sr25519.keypairFromSeed(DEV_SEED)

        val public = Sr25519.derivePublicSoft(root.publicPart(), chainCode("foo"))

        assertEquals(Sr25519.PUBLIC_SIZE, public.size)
        assertEquals(FOO_SOFT_PUBLIC, public.hex())
    }

    @Test
    fun `signatures made by the legacy library verify`() {
        val alicePublic = Hex.decode(ALICE_PUBLIC)

        assertTrue(Sr25519.verify(Hex.decode(LEGACY_ROOT_SIGNATURE), LEGACY_MESSAGE, Hex.decode(DEV_PUBLIC)))
        assertTrue(Sr25519.verify(Hex.decode(LEGACY_ALICE_SIGNATURE), LEGACY_MESSAGE, alicePublic))
        assertFalse(Sr25519.verify(Hex.decode(LEGACY_ALICE_SIGNATURE), LEGACY_MESSAGE, Hex.decode(DEV_PUBLIC)))
        assertFalse(Sr25519.verify(Hex.decode(LEGACY_ROOT_SIGNATURE), "other".toByteArray(), Hex.decode(DEV_PUBLIC)))
    }

    @Test
    fun `sign and verify round trip with randomized signatures`() {
        val keypair = Sr25519.keypairFromSeed(DEV_SEED)
        val message = "round trip".toByteArray()

        val first = Sr25519.sign(keypair.publicPart(), keypair.secretPart(), message)
        val second = Sr25519.sign(keypair.publicPart(), keypair.secretPart(), message)

        assertEquals(Sr25519.SIGNATURE_SIZE, first.size)
        assertEquals(0x80, first[63].toInt() and 0x80)
        assertNotEquals(first.hex(), second.hex())
        assertTrue(Sr25519.verify(first, message, keypair.publicPart()))
        assertTrue(Sr25519.verify(second, message, keypair.publicPart()))
        assertFalse(Sr25519.verify(first, "tampered".toByteArray(), keypair.publicPart()))
    }

    @Test
    fun `malformed signatures verify as false without an exception`() {
        val keypair = Sr25519.keypairFromSeed(DEV_SEED)
        val unmarked = Sr25519.sign(keypair.publicPart(), keypair.secretPart(), LEGACY_MESSAGE)
        unmarked[63] = (unmarked[63].toInt() and 0x7f).toByte()

        assertFalse(Sr25519.verify(unmarked, LEGACY_MESSAGE, keypair.publicPart()))
        assertFalse(Sr25519.verify(ByteArray(63), LEGACY_MESSAGE, keypair.publicPart()))
    }

    @Test
    fun `ed25519 form round trips and matches the legacy library`() {
        val secret = Sr25519.keypairFromSeed(DEV_SEED).secretPart()

        val expanded = Sr25519.toEd25519Bytes(secret)

        assertEquals(LEGACY_ROOT_ED25519_SHA256, sha256(expanded))
        assertArrayEquals(secret, Sr25519.fromEd25519Bytes(expanded))
    }

    @Test
    fun `a non canonical ed25519 secret returns null`() {
        assertNull(Sr25519.fromEd25519Bytes(ByteArray(64) { 0xff.toByte() }))
        assertNull(Sr25519.fromEd25519Bytes(ByteArray(63)))
    }

    @Test
    fun `refused inputs keep the legacy exception messages`() {
        val keypair = Sr25519.keypairFromSeed(DEV_SEED)
        val badPublic = ByteArray(32).also { it[0] = 1 }

        assertThrowsMessage("Provided seed is invalid") { Sr25519.keypairFromSeed(ByteArray(31)) }
        assertThrowsMessage("Provided public key is invalid.") {
            Sr25519.derivePublicSoft(badPublic, chainCode("foo"))
        }
        assertThrowsMessage("Provided public key is invalid.") {
            Sr25519.verify(Sr25519.sign(keypair.publicPart(), keypair.secretPart(), LEGACY_MESSAGE), LEGACY_MESSAGE, badPublic)
        }
        assertThrowsMessage("Provided private key is invalid.") {
            Sr25519.sign(keypair.publicPart(), ByteArray(63), LEGACY_MESSAGE)
        }
        assertThrowsMessage("Provided private key is invalid.") { Sr25519.toEd25519Bytes(ByteArray(65)) }
        assertThrowsMessage("null pointer") { Sr25519.keypairFromSeed(null) }
        assertThrowsMessage("null pointer") { Sr25519.sign(keypair.publicPart(), keypair.secretPart(), null) }
    }

    @Test
    fun `a refused keypair is not echoed in the exception message`() {
        val keypair = Sr25519.keypairFromSeed(DEV_SEED)
        val nonCanonical = keypair.copyOf().also { it[31] = (it[31].toInt() or 0x80).toByte() }

        assertThrowsMessage("Provided pair is invalid") { Sr25519.deriveKeypairHard(nonCanonical, chainCode("Alice")) }
        assertThrowsMessage("Provided pair is invalid") { Sr25519.deriveKeypairSoft(keypair.copyOf(95), chainCode("foo")) }
    }

    @Test
    fun `a chain code that is not 32 bytes throws instead of aborting`() {
        val keypair = Sr25519.keypairFromSeed(DEV_SEED)

        assertThrowsMessage("Provided chain code is invalid.") { Sr25519.deriveKeypairHard(keypair, ByteArray(31)) }
        assertThrowsMessage("Provided chain code is invalid.") { Sr25519.deriveKeypairSoft(keypair, ByteArray(33)) }
        assertThrowsMessage("Provided chain code is invalid.") { Sr25519.derivePublicSoft(keypair.publicPart(), ByteArray(0)) }
    }

    @Test
    fun `diagnostic export greets like the legacy library`() {
        assertEquals("Hello, sr25519!", Sr25519.test("sr25519"))
    }

    private fun assertThrowsMessage(message: String, block: () -> Unit) {
        try {
            block()
        } catch (expected: Exception) {
            assertEquals(Exception::class.java, expected.javaClass)
            assertEquals(message, expected.message)
            return
        }
        throw AssertionError("Expected java.lang.Exception: $message")
    }

    companion object {
        const val DEV_PHRASE = "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
        val DEV_SEED: ByteArray = Hex.decode("fac7959dbfe72f052e5a0c3c8d6530f202b02fd8f9f5ca3580ec8deb7797479e")
        const val DEV_PUBLIC = "46ebddef8cd9bb167dc30878d7113b7e168e6f0646beffd77d69d39bad76b47a"
        const val ALICE_PUBLIC = "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
        const val FOO_SOFT_PUBLIC = "40b9675df90efa6069ff623b0fdfcf706cd47ca7452a5056c7ad58194d23440a"

        // Produced by the schnorrkel 0.9.1 library that shipped up to 4.3.1.
        const val LEGACY_ROOT_KEYPAIR_SHA256 = "4bd9e97960bad44b2185d3526e17efa59b1537d37fc437d1f384e55cf7b9dde1"
        const val LEGACY_ALICE_KEYPAIR_SHA256 = "fef8796541118e95d5fcaa2f0b974d52814f39f117a6b85099c0846f59f62489"
        const val LEGACY_FOO_SCALAR_AND_PUBLIC_SHA256 =
            "fd91e5f795482c01eeec68b07625906250cf2542a5166dc171bc7125960a2de7"
        const val LEGACY_ROOT_ED25519_SHA256 = "ffc3fba37b2bd5482774767b4fac01c703affe851a3d1544f03fbd14c6af18b4"
        val LEGACY_MESSAGE = "Fearless sr25519 legacy signature vector".toByteArray(Charsets.UTF_8)
        const val LEGACY_ROOT_SIGNATURE =
            "c8e035022f42f7e5d18f91a51d3a6a1434387d77492972a58c7987536f5eec3e" +
                "59655202d8d182a684fab37e4bbe9376cb90bad960395fbd37eb470f9826c58c"
        const val LEGACY_ALICE_SIGNATURE =
            "58e8d7c357cb0f8a5a72943f2b0145979e0567d56d5f5d6cd88f9eb372a75751" +
                "50bd78fea8b5e28900fa3c5507f5b8d15008587c19eb36daa4e2d981d4ebae8b"

        /** SCALE-encoded junction name padded to a 32-byte chain code. */
        fun chainCode(name: String): ByteArray {
            val code = ByteArray(Sr25519.CHAINCODE_SIZE)
            code[0] = (name.length shl 2).toByte()
            name.toByteArray().copyInto(code, destinationOffset = 1)
            return code
        }

        fun sha256(vararg parts: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            parts.forEach(digest::update)
            return digest.digest().hex()
        }

        fun ByteArray.hex(): String = Hex.toHexString(this)

        fun ByteArray.publicPart(): ByteArray = copyOfRange(64, 96)

        fun ByteArray.secretPart(): ByteArray = copyOfRange(0, 64)
    }
}
