package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

class SolanaKeyDerivationTest {

    @Test
    fun `derives universal wallet v2 solana vectors`() {
        loadFixture().getAsJsonArray("vectors").forEach { element ->
            val vector = element.asJsonObject
            val expected = vector.getAsJsonObject("expected").getAsJsonObject("solana")

            val account = SolanaKeyDerivation.deriveAccount(vector["mnemonic"].asString)

            assertEquals(UniversalWalletDerivationPaths.SOLANA_DEFAULT, account.derivationPath)
            assertEquals(expected["publicKeyHex"].asString, Hex.toHexString(account.publicKey))
            assertEquals(expected["address"].asString, account.address)
            assertEquals(32, account.privateKey.size)
            assertEquals(32, account.chainCode.size)
        }
    }

    @Test
    fun `normalizes mnemonic whitespace before derivation`() {
        val vector = loadFixture().getAsJsonArray("vectors").first().asJsonObject
        val mnemonic = vector["mnemonic"].asString
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("solana")
        val padded = "  ${mnemonic.replace(" ", "   \n\t")}  "

        val account = SolanaKeyDerivation.deriveAccount(padded)

        assertEquals(expected["publicKeyHex"].asString, Hex.toHexString(account.publicKey))
        assertEquals(expected["address"].asString, account.address)
    }

    @Test
    fun `different solana derivation paths and passphrases cannot reuse vector addresses`() {
        val vector = loadFixture().getAsJsonArray("vectors").first().asJsonObject
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("solana")
        val wrongPath = SolanaKeyDerivation.deriveAccount(
            mnemonic = vector["mnemonic"].asString,
            derivationPath = "m/44'/501'/1'/0'"
        )
        val withPassphrase = SolanaKeyDerivation.deriveAccount(
            mnemonic = vector["mnemonic"].asString,
            passphrase = "fearless"
        )

        assertNotEquals(expected["publicKeyHex"].asString, Hex.toHexString(wrongPath.publicKey))
        assertNotEquals(expected["address"].asString, wrongPath.address)
        assertNotEquals(expected["publicKeyHex"].asString, Hex.toHexString(withPassphrase.publicKey))
        assertNotEquals(expected["address"].asString, withPassphrase.address)
    }

    @Test
    fun `rejects malformed or unsafe solana derivation inputs`() {
        assertThrows(IllegalArgumentException::class.java) {
            SolanaKeyDerivation.deriveAccount("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SolanaKeyDerivation.deriveAccount("abandon abandon abandon", derivationPath = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SolanaKeyDerivation.deriveAccount("abandon abandon abandon", derivationPath = "44'/501'/0'/0'")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SolanaKeyDerivation.deriveAccount("abandon abandon abandon", derivationPath = "m/44'/501/0'/0'")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SolanaKeyDerivation.deriveAccount("abandon abandon abandon", derivationPath = "m/44'/501'/x'/0'")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SolanaKeyDerivation.deriveAccount("abandon abandon abandon", derivationPath = "m/44'/501'/2147483648'/0'")
        }
    }

    @Test
    fun `rejects invalid solana public and private key lengths`() {
        assertThrows(IllegalArgumentException::class.java) {
            SolanaKeyDerivation.publicKeyFromPrivateKey(ByteArray(31))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SolanaKeyDerivation.addressFromPublicKey(ByteArray(31))
        }

        assertTrue(SolanaKeyDerivation.addressFromPublicKey(ByteArray(32)).isNotBlank())
    }

    private fun loadFixture(): JsonObject {
        val stream = javaClass.classLoader?.getResourceAsStream("universal-wallet-v2-vectors.json")
            ?: error("universal-wallet-v2-vectors.json is missing from test resources")

        return stream.use {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject
        }
    }
}
