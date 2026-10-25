package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

class BitcoinKeyDerivationTest {

    @Test
    fun `derives universal wallet v2 bitcoin vectors`() {
        loadFixture().getAsJsonArray("vectors").forEach { element ->
            val vector = element.asJsonObject
            val expected = vector.getAsJsonObject("expected").getAsJsonObject("bitcoin")
            val expectedMainnet = expected.getAsJsonObject("mainnet")
            val expectedTestnet = expected.getAsJsonObject("testnet")

            val mainnet = BitcoinKeyDerivation.deriveAccount(vector["mnemonic"].asString, network = BitcoinKeyDerivation.Network.Mainnet)
            val testnet = BitcoinKeyDerivation.deriveAccount(vector["mnemonic"].asString, network = BitcoinKeyDerivation.Network.Testnet)

            assertEquals(UniversalWalletDerivationPaths.BITCOIN_MAINNET_ACCOUNT, mainnet.accountPath)
            assertEquals(UniversalWalletDerivationPaths.BITCOIN_MAINNET_FIRST_RECEIVE, mainnet.firstReceivePath)
            assertEquals(expectedMainnet["accountXpub"].asString, mainnet.accountXpub)
            assertEquals(expectedMainnet["firstReceiveAddress"].asString, mainnet.firstReceiveAddress)
            assertEquals(32, mainnet.privateKey.size)
            assertEquals(32, mainnet.chainCode.size)
            assertEquals(33, mainnet.publicKey.size)

            assertEquals(UniversalWalletDerivationPaths.BITCOIN_TESTNET_ACCOUNT, testnet.accountPath)
            assertEquals(UniversalWalletDerivationPaths.BITCOIN_TESTNET_FIRST_RECEIVE, testnet.firstReceivePath)
            assertEquals(expectedTestnet["accountXpub"].asString, testnet.accountXpub)
            assertEquals(expectedTestnet["firstReceiveAddress"].asString, testnet.firstReceiveAddress)
            assertEquals(32, testnet.privateKey.size)
            assertEquals(32, testnet.chainCode.size)
            assertEquals(33, testnet.publicKey.size)
        }
    }

    @Test
    fun `normalizes mnemonic whitespace before bitcoin derivation`() {
        val vector = loadFixture().getAsJsonArray("vectors").first().asJsonObject
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("bitcoin").getAsJsonObject("mainnet")
        val padded = "  ${vector["mnemonic"].asString.replace(" ", "   \n\t")}  "

        val account = BitcoinKeyDerivation.deriveAccount(padded, network = BitcoinKeyDerivation.Network.Mainnet)

        assertEquals(expected["accountXpub"].asString, account.accountXpub)
        assertEquals(expected["firstReceiveAddress"].asString, account.firstReceiveAddress)
    }

    @Test
    fun `different bitcoin paths and passphrases cannot reuse vector addresses`() {
        val vector = loadFixture().getAsJsonArray("vectors").first().asJsonObject
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("bitcoin").getAsJsonObject("mainnet")
        val mnemonic = vector["mnemonic"].asString
        val wrongPurpose = BitcoinKeyDerivation.deriveKey(
            mnemonic = mnemonic,
            derivationPath = "m/44'/0'/0'/0/0",
            network = BitcoinKeyDerivation.Network.Mainnet
        )
        val withPassphrase = BitcoinKeyDerivation.deriveAccount(
            mnemonic = mnemonic,
            passphrase = "fearless",
            network = BitcoinKeyDerivation.Network.Mainnet
        )

        assertNotEquals(expected["firstReceiveAddress"].asString, wrongPurpose.address)
        assertNotEquals(expected["accountXpub"].asString, withPassphrase.accountXpub)
        assertNotEquals(expected["firstReceiveAddress"].asString, withPassphrase.firstReceiveAddress)
    }

    @Test
    fun `builds bitcoin receive paths safely`() {
        assertEquals("m/84'/0'/0'/0/7", BitcoinKeyDerivation.getReceivePath(BitcoinKeyDerivation.Network.Mainnet, 7))
        assertEquals("m/84'/1'/0'/0/7", BitcoinKeyDerivation.getReceivePath(BitcoinKeyDerivation.Network.Testnet, 7))
        assertEquals("m/84'/0'/0'/1/2", BitcoinKeyDerivation.getReceivePath(BitcoinKeyDerivation.Network.Mainnet, 2, 1))

        assertThrows(IllegalArgumentException::class.java) {
            BitcoinKeyDerivation.getReceivePath(BitcoinKeyDerivation.Network.Mainnet, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BitcoinKeyDerivation.getReceivePath(BitcoinKeyDerivation.Network.Mainnet, 0, 2)
        }
    }

    @Test
    fun `rejects malformed or unsafe bitcoin derivation inputs`() {
        val seed = ByteArray(64) { 1 }

        assertThrows(IllegalArgumentException::class.java) {
            BitcoinKeyDerivation.deriveAccount("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BitcoinKeyDerivation.derivePrivateKey(ByteArray(0), UniversalWalletDerivationPaths.BITCOIN_MAINNET_FIRST_RECEIVE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BitcoinKeyDerivation.derivePrivateKey(seed, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BitcoinKeyDerivation.derivePrivateKey(seed, "84'/0'/0'/0/0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BitcoinKeyDerivation.derivePrivateKey(seed, "m/84'//0'")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BitcoinKeyDerivation.derivePrivateKey(seed, "m/84'/x'/0'")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BitcoinKeyDerivation.derivePrivateKey(seed, "m/84'/2147483648'/0'")
        }
    }

    @Test
    fun `rejects invalid bitcoin public and private key lengths`() {
        assertThrows(IllegalArgumentException::class.java) {
            BitcoinKeyDerivation.publicKeyFromPrivateKey(ByteArray(31))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BitcoinKeyDerivation.addressFromPublicKey(ByteArray(32), BitcoinKeyDerivation.Network.Mainnet)
        }

        val account = BitcoinKeyDerivation.deriveAccount(loadFixture().getAsJsonArray("vectors").first().asJsonObject["mnemonic"].asString)
        assertTrue(account.firstReceiveAddress.startsWith("bc1q"))
        assertFalse(account.firstReceiveAddress.startsWith("tb1q"))
    }

    private fun loadFixture(): JsonObject {
        val stream = javaClass.classLoader?.getResourceAsStream("universal-wallet-v2-vectors.json")
            ?: error("universal-wallet-v2-vectors.json is missing from test resources")

        return stream.use {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject
        }
    }
}
