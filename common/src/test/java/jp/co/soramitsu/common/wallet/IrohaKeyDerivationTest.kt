package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.InputStreamReader

class IrohaKeyDerivationTest {

    @Test
    fun `derives universal wallet v2 iroha vectors`() {
        loadVectors().forEach { vector ->
            val iroha = vector.getAsJsonObject("expected").getAsJsonObject("iroha")
            val taira = iroha.getAsJsonObject("taira")
            val nexus = iroha.getAsJsonObject("nexus")

            val account = IrohaKeyDerivation.deriveAccount(vector["mnemonic"].asString)
            val tairaAddress = IrohaKeyDerivation.deriveAddress(
                mnemonic = vector["mnemonic"].asString,
                chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
            )
            val nexusAddress = IrohaKeyDerivation.deriveAddress(
                mnemonic = vector["mnemonic"].asString,
                chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant
            )

            assertEquals(UniversalWalletDerivationPaths.IROHA_DEFAULT, account.derivationPath)
            assertEquals(iroha["derivationPath"].asString, account.derivationPath)
            assertEquals(taira["publicKeyHex"].asString, account.publicKeyHex)
            assertEquals(taira["canonicalHex"].asString, account.canonicalHex)
            assertEquals(taira["i105"].asString, tairaAddress.i105)
            assertEquals(nexus["i105"].asString, nexusAddress.i105)
            assertEquals(taira["publicKeyHex"].asString, nexus["publicKeyHex"].asString)
            assertEquals(taira["canonicalHex"].asString, nexus["canonicalHex"].asString)
        }
    }

    @Test
    fun `does not reuse iroha vectors across paths or passphrases`() {
        val vector = loadVectors().first()
        val iroha = vector.getAsJsonObject("expected").getAsJsonObject("iroha")
        val taira = iroha.getAsJsonObject("taira")

        val wrongPath = IrohaKeyDerivation.deriveAccount(
            mnemonic = vector["mnemonic"].asString,
            derivationPath = "m/44'/617'/1'/0'"
        )
        val withPassphrase = IrohaKeyDerivation.deriveAccount(
            mnemonic = vector["mnemonic"].asString,
            passphrase = "fearless"
        )

        assertNotEquals(taira["publicKeyHex"].asString, wrongPath.publicKeyHex)
        assertNotEquals(taira["canonicalHex"].asString, wrongPath.canonicalHex)
        assertNotEquals(taira["publicKeyHex"].asString, withPassphrase.publicKeyHex)
        assertNotEquals(taira["canonicalHex"].asString, withPassphrase.canonicalHex)
    }

    @Test
    fun `rejects malformed iroha derivation inputs`() {
        val mnemonic = loadVectors().first()["mnemonic"].asString

        assertThrows(IllegalArgumentException::class.java) {
            IrohaKeyDerivation.deriveAccount("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IrohaKeyDerivation.deriveAccount(mnemonic, derivationPath = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IrohaKeyDerivation.deriveAccount(mnemonic, derivationPath = "44'/617'/0'/0'")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IrohaKeyDerivation.deriveAccount(mnemonic, derivationPath = "m/44'/617/0'/0'")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IrohaKeyDerivation.deriveAddress(mnemonic, chainDiscriminant = -1)
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
