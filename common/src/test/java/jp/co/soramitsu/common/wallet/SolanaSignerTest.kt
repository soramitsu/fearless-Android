package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import jp.co.soramitsu.common.utils.SolanaSigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

class SolanaSignerTest {

    @Test
    fun `signs solana messages with deterministic ed25519 signatures`() {
        val vector = loadFixture().getAsJsonArray("vectors").first().asJsonObject
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("solana")
        val message = MESSAGE.toByteArray(Charsets.UTF_8)

        val signature = SolanaSigner.signMessage(vector["mnemonic"].asString, message)

        assertEquals(UniversalWalletDerivationPaths.SOLANA_DEFAULT, signature.derivationPath)
        assertEquals(expected["address"].asString, signature.address)
        assertEquals(expected["publicKeyHex"].asString, signature.publicKey.toHex())
        assertEquals(EXPECTED_SIGNATURE_HEX, signature.signatureHex)
        assertEquals(EXPECTED_SIGNATURE_BASE58, signature.signatureBase58)
        assertEquals(64, signature.signature.size)
        assertTrue(SolanaSigner.verifyMessage(signature.publicKey, message, signature.signature))
        assertFalse(SolanaSigner.verifyMessage(signature.publicKey, "tampered".toByteArray(Charsets.UTF_8), signature.signature))
    }

    @Test
    fun `rejects unsafe solana signing inputs`() {
        val vector = loadFixture().getAsJsonArray("vectors").first().asJsonObject
        val message = MESSAGE.toByteArray(Charsets.UTF_8)

        assertThrows(IllegalArgumentException::class.java) {
            SolanaSigner.signMessage(ByteArray(31), message)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SolanaSigner.signMessage(vector["mnemonic"].asString, ByteArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SolanaSigner.signMessage(vector["mnemonic"].asString, ByteArray(64 * 1024 + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SolanaSigner.verifyMessage(ByteArray(31), message, ByteArray(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SolanaSigner.verifyMessage(ByteArray(32), message, ByteArray(63))
        }
    }

    private fun loadFixture(): JsonObject {
        val stream = javaClass.classLoader?.getResourceAsStream("universal-wallet-v2-vectors.json")
            ?: error("universal-wallet-v2-vectors.json is missing from test resources")

        return stream.use {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private companion object {
        const val MESSAGE = "Fearless Solana sign-in challenge"
        const val EXPECTED_SIGNATURE_HEX = "059cfc8a284341824f80de10ec2e9d6816a2f0d2f96cf7e9bec56e000a87d881cc7558c6fae7d8f1aded103607122a8d66d6cfe8c7ae350df31fefb550485c06"
        const val EXPECTED_SIGNATURE_BASE58 = "7WXinTJc5WM9nFWaraQyH1fHyG2WFv7QgBAUpmXByiCoaEr2Hm1AEMUXkRfxcp6sZxbrqedhNHUKBBrU94hmGAZ"
    }
}
