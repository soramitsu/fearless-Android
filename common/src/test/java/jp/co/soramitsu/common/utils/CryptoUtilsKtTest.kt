package jp.co.soramitsu.common.utils

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoUtilsKtTest {

    @Test
    fun `should generate correct ethereum address`() {
        val publicKey = "6e145ccef1033dea239875dd00dfb4fee6e3348b84985c92f103444683bae07b83b5c38e5e2b0c8529d7fa3f64d46daa1ece2d9ac14cab9477d042c84c32ccd0".fromHex()
        val expectedAddress = "001d3f1ef827552ae1114027bd3ecf1f086ba0f9"

        assertArrayEquals(expectedAddress.fromHex(), publicKey.ethereumAddressFromPublicKey())
    }

    @Test
    fun `compressed secp256k1 generator point is valid`() {
        val publicKey =
            "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
                .fromHex()

        assertTrue(publicKey.isValidEthereumCompressedPublicKey())
    }

    @Test
    fun `shape-valid off-curve compressed point is rejected`() {
        val publicKey = byteArrayOf(0x02) +
            ByteArray(32) { 0xFF.toByte() }

        assertFalse(publicKey.isValidEthereumCompressedPublicKey())
    }

    @Test
    fun `uncompressed and wrong-prefix public keys are rejected`() {
        val uncompressed = ByteArray(65).also { it[0] = 0x04 }
        val wrongPrefix = ByteArray(33).also { it[0] = 0x04 }

        assertFalse(uncompressed.isValidEthereumCompressedPublicKey())
        assertFalse(wrongPrefix.isValidEthereumCompressedPublicKey())
    }
}
