package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.encrypt.CryptoTestVectors.ED25519_ORDER
import jp.co.soramitsu.fearless_utils.encrypt.CryptoTestVectors.flip
import jp.co.soramitsu.fearless_utils.encrypt.CryptoTestVectors.scalar
import jp.co.soramitsu.fearless_utils.encrypt.CryptoTestVectors.stream
import jp.co.soramitsu.fearless_utils.encrypt.CryptoTestVectors.withScalar
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class Ed25519CryptoTest {

    private val substrateEd25519 = MultiChainEncryption.Substrate(EncryptionType.ED25519)

    @Test
    fun `reproduces every RFC 8032 Ed25519 test vector`() {
        val vectors = CryptoTestVectors.json("crypto/rfc8032-ed25519.json").objects("vectors")
        assertEquals(5, vectors.size)
        vectors.forEach { vector ->
            val seed = vector.string("secretKey").fromHex()
            val publicKey = vector.string("publicKey").fromHex()
            val message = vector.string("message").fromHex()
            val signature = vector.string("signature").fromHex()

            assertArrayEquals(vector.string("name"), publicKey, Ed25519Crypto.publicKey(seed))
            assertArrayEquals(vector.string("name"), signature, Ed25519Crypto.sign(seed, message))
            assertTrue(vector.string("name"), Ed25519Crypto.verify(message, signature, publicKey))

            val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed)
            assertArrayEquals(seed, keypair.privateKey)
            assertArrayEquals(publicKey, keypair.publicKey)
            assertArrayEquals(signature, Signer.sign(substrateEd25519, message, keypair).signature)
            assertArrayEquals(signature, Signer.prepareSigning(substrateEd25519, message, keypair).sign { it() }.signature)
            assertTrue(Signer.verifyEd25519(message, signature, publicKey))
        }
    }

    @Test
    fun `reproduces the keys signatures and verdicts of EdDSA-Java 0_3_0`() {
        val cases = CryptoTestVectors.json("legacy-crypto/ed25519-eddsa-java-0.3.0.json").objects("cases")
        assertEquals(32, cases.size)
        var checked = 0
        cases.forEachIndexed { index, case ->
            val seed = case.string("seed").fromHex()
            val publicKey = case.string("publicKey").fromHex()
            val otherKey = cases[(index + 1) % cases.size].string("publicKey").fromHex()
            assertArrayEquals(seed, stream("fearless-ed25519-seed/$index", 32))

            val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed)
            assertArrayEquals(seed, keypair.privateKey)
            assertArrayEquals(publicKey, keypair.publicKey)

            case.objects("signatures").forEach { expected ->
                val length = expected.get("length").asInt
                val message = stream("fearless-ed25519-message/$index/$length", length)
                val signature = expected.string("signature").fromHex()
                assertArrayEquals("seed $index length $length", signature, Signer.sign(substrateEd25519, message, keypair).signature)

                val variants = mapOf(
                    "valid" to Triple(message, signature, publicKey),
                    "sPlusL" to Triple(message, withScalar(signature, scalar(signature) + ED25519_ORDER), publicKey),
                    "sEqualsL" to Triple(message, withScalar(signature, ED25519_ORDER), publicKey),
                    "flippedR" to Triple(message, flip(signature, 0), publicKey),
                    "flippedS" to Triple(message, flip(signature, 32), publicKey),
                    "otherMessage" to Triple(if (message.isEmpty()) byteArrayOf(0) else flip(message, message.size - 1), signature, publicKey),
                    "otherKey" to Triple(message, signature, otherKey)
                )
                val verdicts = expected.getAsJsonObject("verify")
                variants.forEach { (name, input) ->
                    // EdDSA-Java threw on some malformed input; the old verifier never accepted it, nor does this one.
                    val legacy = verdicts.string(name).toBooleanStrictOrNull() ?: false
                    assertEquals("seed $index length $length $name", legacy, Signer.verifyEd25519(input.first, input.second, input.third))
                    checked++
                }
            }
        }
        assertEquals(32 * 6 * 7, checked)
    }

    @Test
    fun `signs and verifies messages of every size`() {
        for (length in listOf(0, 1, 31, 32, 33, 64, 127, 128, 129, 4096)) {
            val seed = stream("ed25519-sizes/$length", 32)
            val message = stream("ed25519-sizes/message/$length", length)
            val signature = Ed25519Crypto.sign(seed, message)
            assertEquals(64, signature.size)
            assertTrue(Ed25519Crypto.verify(message, signature, Ed25519Crypto.publicKey(seed)))
        }
    }

    @Test
    fun `rejects seeds that are not 32 bytes`() {
        for (size in listOf(0, 31, 33, 64)) {
            assertThrows(IllegalArgumentException::class.java) { Ed25519Crypto.publicKey(ByteArray(size)) }
            assertThrows(IllegalArgumentException::class.java) { Ed25519Crypto.sign(ByteArray(size), byteArrayOf(1)) }
            assertThrows(IllegalArgumentException::class.java) {
                Signer.prepareSigning(substrateEd25519, byteArrayOf(1), jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair(ByteArray(size), ByteArray(32)))
            }
        }
    }

    @Test
    fun `verification rejects non-canonical and off-curve keys without throwing`() {
        val seed = stream("ed25519-strict", 32)
        val message = "strict".encodeToByteArray()
        val signature = Ed25519Crypto.sign(seed, message)
        val identity = ByteArray(32).also { it[0] = 1 }
        val nonCanonicalY = ByteArray(32) { 0xff.toByte() }.also { it[31] = 0x7f }
        listOf(identity, nonCanonicalY, ByteArray(32), stream("not-a-point", 32)).forEach { key ->
            assertFalse(key.toHexString(), Ed25519Crypto.verify(message, signature, key))
        }
        val identitySignature = identity + ByteArray(32)
        assertFalse(Ed25519Crypto.verify(message, identitySignature, identity))
    }

    @Test
    fun `canonical scalar check bounds S by the group order`() {
        val signature = Ed25519Crypto.sign(stream("scalar", 32), byteArrayOf())
        assertTrue(Ed25519Crypto.hasCanonicalScalar(signature))
        assertTrue(Ed25519Crypto.hasCanonicalScalar(withScalar(signature, ED25519_ORDER - BigInteger.ONE)))
        assertFalse(Ed25519Crypto.hasCanonicalScalar(withScalar(signature, ED25519_ORDER)))
        assertFalse(Ed25519Crypto.hasCanonicalScalar(withScalar(signature, BigInteger.ONE.shiftLeft(256) - BigInteger.ONE)))
        assertFalse(Ed25519Crypto.hasCanonicalScalar(signature.copyOf(63)))
    }

    @Test
    fun `hard derivation of Ed25519 keypairs is unchanged`() {
        val seed = stream("ed25519-hdkd", 32)
        val junction = jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder.decode("//polkadot//0").junctions
        val derived = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed, junction)
        assertArrayEquals(Ed25519Crypto.publicKey(derived.privateKey), derived.publicKey)
    }
}
