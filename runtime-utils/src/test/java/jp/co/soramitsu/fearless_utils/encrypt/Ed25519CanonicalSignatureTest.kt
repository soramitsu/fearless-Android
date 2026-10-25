package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class Ed25519CanonicalSignatureTest {

    private val message = byteArrayOf()
    private val seed = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60".fromHex()
    private val publicKey = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a".fromHex()
    private val signature = (
        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555f" +
            "b8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
        ).fromHex()
    private val order = BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)

    @Test
    fun `accepts RFC 8032 canonical signature`() {
        assertTrue(Signer.verifyEd25519(message, signature, publicKey))
    }

    @Test
    fun `signing retains RFC 8032 signature bytes`() {
        val actual = Signer.sign(
            MultiChainEncryption.Substrate(EncryptionType.ED25519),
            message,
            BaseKeypair(seed, publicKey)
        )
        assertArrayEquals(signature, actual.signature)
    }

    @Test
    fun `rejects valid signature with group order added to scalar`() {
        val scalar = BigInteger(1, signature.copyOfRange(32, 64).reversedArray())
        assertFalse(Signer.verifyEd25519(message, withScalar(scalar + order), publicKey))
    }

    @Test
    fun `rejects scalar at or above group order`() {
        assertFalse(Signer.verifyEd25519(message, withScalar(order), publicKey))
        assertFalse(Signer.verifyEd25519(message, withScalar(order + BigInteger.ONE), publicKey))
    }

    @Test
    fun `rejects wrong signature and public key lengths`() {
        for (size in listOf(0, 31, 32, 63, 65, 128)) {
            assertFalse(Signer.verifyEd25519(message, signature.copyOf(size), publicKey))
        }
        for (size in listOf(0, 31, 33, 64)) {
            assertFalse(Signer.verifyEd25519(message, signature, publicKey.copyOf(size)))
        }
    }

    private fun withScalar(scalar: BigInteger): ByteArray {
        val littleEndian = scalar.toByteArray().reversedArray().copyOf(32)
        return signature.copyOfRange(0, 32) + littleEndian
    }
}
