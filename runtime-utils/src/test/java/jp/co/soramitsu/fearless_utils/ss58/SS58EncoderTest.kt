package jp.co.soramitsu.fearless_utils.ss58

import jp.co.soramitsu.fearless_utils.common.TestAddressBytes
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.addressByte
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class SS58EncoderTest {

    private val PUBLIC_KEY = "6addccf0b805e2d0dc445239b800201e1fb6f17f92ef4eaa1516f4d0e2cf1664"
    private val KUSAMA_ADDRESS = "EzSUv17LNHTU2xdPKLuLkPy7fCD795DZ6d5CnF4x4HSkcb4"

    @Test
    fun `should encode address from public key`() {
        val result = PUBLIC_KEY.fromHex().toAddress(TestAddressBytes.KUSAMA)

        assertEquals(KUSAMA_ADDRESS, result)
    }

    @Test
    fun `should decode public key from address`() {
        val result = KUSAMA_ADDRESS.toAccountId().toHexString()

        assertEquals(PUBLIC_KEY, result)
    }

    @Test
    fun `should encode public key to address with prefix 255`() {
        val pk = "0x84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859"
        val address = pk.fromHex().toAddress(255)
        assertEquals("yGFjZAdDruDn4CzV1ZkYZD5m2cexzXceAnTGmXLHpfUh4FUgc", address)
    }

    @Test
    fun `should encode public key to address with prefix 10041`() {
        val pk = "0x84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859"
        val address = pk.fromHex().toAddress(10041)
        assertEquals("bXjbTjWHWhkx18SofTq3Vv23owpSGBN6mp2k6HejCMFw4v3Wi", address)
    }

    @Test
    fun `should encode public key to address with prefix 78`() {
        val pk = "0x84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859"
        val address = pk.fromHex().toAddress(78)
        assertEquals("dmwwH4BrfFSJWx3k3Sd5Vh17MN33zoXp1yVhSAd17b8uQBTLE", address)
    }

    @Test
    fun `decode address to key with 78 prefix`() {
        val address = "dmwwH4BrfFSJWx3k3Sd5Vh17MN33zoXp1yVhSAd17b8uQBTLE"
        val hexKey = address.toAccountId().toHexString(true)
        val prefix = address.addressByte()
        assertEquals("0x84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859", hexKey)
        assertEquals(78.toShort(), prefix)
    }

    @Test
    fun `encode key to address with 69 prefix`() {
        val hexKey = "0x84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859"
        val address = hexKey.fromHex().toAddress(69)
        assertEquals("cnUVLAjzRsrXrzEiqjxMpBwvb6YgdBy8DKibonvZgtcQY5ZKe", address)
    }

    @Test
    fun `encode key to address with kusama prefix`() {
        val hexKey = "0x84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859"
        val address = hexKey.fromHex().toAddress(TestAddressBytes.KUSAMA)
        assertEquals("FaNDBF8erbEfQtpLuwpk6kmQrYyiKNzBQm6BSuh2fdCqajb", address)
    }

    @Test
    fun `encode key to address with polkadot prefix`() {
        val hexKey = "0x84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859"
        val address = hexKey.fromHex().toAddress(TestAddressBytes.POLKADOT)
        assertEquals("1413hCAKtGqnMJ5tXrBmzJDv7tGPbx7woXepx5d66xSEH6qM", address)
    }

    @Test
    fun `encode key to address with westend prefix`() {
        val hexKey = "0x84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859"
        val address = hexKey.fromHex().toAddress(TestAddressBytes.WESTEND)
        assertEquals("5F4kYruG2VaJum5NaD8mr9PmGGGjueZoj2vLnndjYsQi6Vkw", address)
    }

    @Test
    fun `decode address to key with 69 prefix`() {
        val address = "cnUVLAjzRsrXrzEiqjxMpBwvb6YgdBy8DKibonvZgtcQY5ZKe"
        val hexKey = address.toAccountId().toHexString(true)
        val prefix = address.addressByte()
        assertEquals("0x84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859", hexKey)
        assertEquals(69.toShort(), prefix)
    }

    @Test
    fun `decode address to key with kusama prefix`() {
        val address = "FaNDBF8erbEfQtpLuwpk6kmQrYyiKNzBQm6BSuh2fdCqajb"
        val hexKey = address.toAccountId().toHexString(true)
        val prefix = address.addressByte()
        assertEquals("0x84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859", hexKey)
        assertEquals(TestAddressBytes.KUSAMA, prefix)
    }

    @Test
    fun `decode address to key with polkadot prefix`() {
        val address = "1413hCAKtGqnMJ5tXrBmzJDv7tGPbx7woXepx5d66xSEH6qM"
        val hexKey = address.toAccountId().toHexString(true)
        val prefix = address.addressByte()
        assertEquals("0x84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859", hexKey)
        assertEquals(TestAddressBytes.POLKADOT, prefix)
    }

    @Test
    fun `decode address to key with westend prefix`() {
        val address = "5F4kYruG2VaJum5NaD8mr9PmGGGjueZoj2vLnndjYsQi6Vkw"
        val hexKey = address.toAccountId().toHexString(true)
        val prefix = address.addressByte()
        assertEquals("0x84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859", hexKey)
        assertEquals(TestAddressBytes.WESTEND, prefix)
    }
}