package jp.co.soramitsu.common.utils

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import org.junit.Assert.assertEquals
import org.junit.Test

class CryptoUtilsKtTest {

    @Test
    fun `should generate correct ethereum address`() {
        val publicKey = "6e145ccef1033dea239875dd00dfb4fee6e3348b84985c92f103444683bae07b83b5c38e5e2b0c8529d7fa3f64d46daa1ece2d9ac14cab9477d042c84c32ccd0".fromHex()
        val expectedAddress = "001d3f1ef827552ae1114027bd3ecf1f086ba0f9"

        assertEquals(expectedAddress, publicKey.ethereumAddressFromPublicKey())
    }
}
