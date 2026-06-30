package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import jp.co.soramitsu.common.utils.TonKeyDerivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

class TonKeyDerivationTest {

    @Test
    fun `derives universal wallet v2 ton vectors`() {
        loadVectors().forEach { vector ->
            val expected = vector.getAsJsonObject("expected").getAsJsonObject("ton")

            val account = TonKeyDerivation.deriveAccount(vector["mnemonic"].asString)

            assertEquals(UniversalWalletDerivationPaths.TON_DEFAULT, account.derivationPath)
            assertEquals(expected["derivationPath"].asString, account.derivationPath)
            assertEquals(expected["publicKeyHex"].asString, account.publicKeyHex)
            assertEquals(expected["addressBounceable"].asString, account.addressBounceable)
            assertEquals(expected["addressNonBounceable"].asString, account.addressNonBounceable)
            assertEquals(expected["testnetNonBounceable"].asString, account.testnetNonBounceable)
            assertEquals(32, account.privateKey.size)
            assertEquals(32, account.chainCode.size)
            assertEquals(32, account.publicKey.size)
            assertTrue(account.accountId.startsWith("0:"))
        }
    }

    @Test
    fun `normalizes mnemonic whitespace before ton derivation`() {
        val vector = loadVectors().first()
        val mnemonic = vector["mnemonic"].asString
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("ton")
        val padded = "  ${mnemonic.replace(" ", "   \n\t")}  "

        val account = TonKeyDerivation.deriveAccount(padded)

        assertEquals(expected["publicKeyHex"].asString, account.publicKeyHex)
        assertEquals(expected["addressNonBounceable"].asString, account.addressNonBounceable)
    }

    @Test
    fun `does not reuse ton vectors across paths passphrases or network flags`() {
        val vector = loadVectors().first()
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("ton")
        val wrongPath = TonKeyDerivation.deriveAccount(
            mnemonic = vector["mnemonic"].asString,
            derivationPath = "m/44'/607'/1'/0'/0'"
        )
        val withPassphrase = TonKeyDerivation.deriveAccount(
            mnemonic = vector["mnemonic"].asString,
            passphrase = "fearless"
        )

        assertNotEquals(expected["publicKeyHex"].asString, wrongPath.publicKeyHex)
        assertNotEquals(expected["addressNonBounceable"].asString, wrongPath.addressNonBounceable)
        assertNotEquals(expected["publicKeyHex"].asString, withPassphrase.publicKeyHex)
        assertNotEquals(expected["addressNonBounceable"].asString, withPassphrase.addressNonBounceable)
        assertNotEquals(expected["addressNonBounceable"].asString, expected["testnetNonBounceable"].asString)
    }

    @Test
    fun `rejects malformed ton derivation inputs`() {
        val mnemonic = loadVectors().first()["mnemonic"].asString

        assertThrows(IllegalArgumentException::class.java) {
            TonKeyDerivation.deriveAccount("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TonKeyDerivation.deriveAccount(mnemonic, derivationPath = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TonKeyDerivation.deriveAccount(mnemonic, derivationPath = "44'/607'/0'/0'/0'")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TonKeyDerivation.deriveAccount(mnemonic, derivationPath = "m/44'/607/0'/0'/0'")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TonKeyDerivation.addressFromPublicKey(ByteArray(31))
        }
    }

    private fun loadVectors(): List<JsonObject> {
        val stream = javaClass.classLoader?.getResourceAsStream("universal-wallet-v2-vectors.json")
            ?: error("universal-wallet-v2-vectors.json is missing from test resources")

        return stream.use {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject.getAsJsonArray("vectors").map { vector -> vector.asJsonObject }
        }
    }
}
