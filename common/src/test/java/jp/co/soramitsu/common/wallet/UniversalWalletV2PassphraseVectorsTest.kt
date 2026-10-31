package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.TonKeyDerivation
import jp.co.soramitsu.common.utils.UniversalWalletPassphrase
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** The shared passphrase fixture, computed independently of every client (web/iOS/Android). */
class UniversalWalletV2PassphraseVectorsTest {

    @Test
    fun `fixture lists both seed lengths sources and negative cases`() {
        val fixture = loadFixture()

        assertEquals(1, fixture["version"].asInt)
        assertEquals(
            listOf("import12-trezor" to 12, "default24-unicode" to 24),
            vectors().map { it["id"].asString to it["wordCount"].asInt }
        )
        assertEquals(
            setOf("password-ignored", "passphrase-not-normalised", "path-is-not-password"),
            fixture.getAsJsonArray("negativeCases").map { it.asJsonObject["id"].asString }.toSet()
        )
        vectors().forEach { vector ->
            assertEquals(vector["wordCount"].asInt, vector["mnemonic"].asString.split(" ").size)
            assertEquals(
                vector["passphraseNfkdUtf8Hex"].asString,
                UniversalWalletPassphrase.normalize(vector["passphrase"].asString).toByteArray(Charsets.UTF_8).toHex()
            )
        }
    }

    @Test
    fun `derives every universal account with the wallet password`() {
        vectors().forEach { vector ->
            val mnemonic = vector["mnemonic"].asString
            val passphrase = vector["passphrase"].asString
            val expected = vector.getAsJsonObject("expected")

            for ((network, key) in listOf(
                BitcoinKeyDerivation.Network.Mainnet to "mainnet",
                BitcoinKeyDerivation.Network.Testnet to "testnet"
            )) {
                val bitcoin = expected.getAsJsonObject("bitcoin").getAsJsonObject(key)
                val account = BitcoinKeyDerivation.deriveAccount(mnemonic, passphrase, network)
                assertEquals(bitcoin["accountPath"].asString, account.accountPath)
                assertEquals(bitcoin["accountXpub"].asString, account.accountXpub)
                assertEquals(bitcoin["firstReceiveAddress"].asString, account.firstReceiveAddress)
            }

            val solana = expected.getAsJsonObject("solana")
            val solanaAccount = SolanaKeyDerivation.deriveAccount(mnemonic, passphrase)
            assertEquals(UniversalWalletDerivationPaths.SOLANA_DEFAULT, solana["derivationPath"].asString)
            assertEquals(solana["publicKeyHex"].asString, solanaAccount.publicKey.toHex())
            assertEquals(solana["address"].asString, solanaAccount.address)

            val ton = expected.getAsJsonObject("ton")
            val tonAccount = TonKeyDerivation.deriveAccount(mnemonic, passphrase)
            assertEquals(UniversalWalletDerivationPaths.TON_DEFAULT, ton["derivationPath"].asString)
            assertEquals(ton["publicKeyHex"].asString, tonAccount.publicKeyHex)
            assertEquals(ton["addressBounceable"].asString, tonAccount.addressBounceable)
            assertEquals(ton["addressNonBounceable"].asString, tonAccount.addressNonBounceable)
            assertEquals(ton["testnetNonBounceable"].asString, tonAccount.testnetNonBounceable)
            assertTrue(
                TonKeyDerivation.matchesUniversalMnemonicKeypair(mnemonic, tonAccount.privateKey, tonAccount.publicKey, passphrase)
            )

            val iroha = expected.getAsJsonObject("iroha")
            for ((network, key) in listOf(UniversalWalletRegistry.taira to "taira", UniversalWalletRegistry.nexus to "nexus")) {
                val entry = iroha.getAsJsonObject(key)
                val address = IrohaKeyDerivation.deriveAddress(mnemonic, passphrase, chainDiscriminant = network.chainDiscriminant)
                assertEquals(entry["chainDiscriminant"].asInt, network.chainDiscriminant)
                assertEquals(entry["publicKeyHex"].asString, address.account.publicKeyHex)
                assertEquals(entry["canonicalHex"].asString, address.account.canonicalHex)
                assertEquals(entry["i105"].asString, address.i105)
            }
        }
    }

    @Test
    fun `derivation paths resolve to the fixture passphrase`() {
        loadFixture().getAsJsonArray("passphraseSources").map { it.asJsonObject }.forEach { source ->
            val ethereumPassword = source["ethereumPathPassword"].takeUnless { it.isJsonNull }?.asString
            val ethereumPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH +
                ethereumPassword?.let { "///$it" }.orEmpty()

            assertEquals(
                source["id"].asString,
                source["passphrase"].asString,
                UniversalWalletPassphrase.resolve(source["substrateDerivationPath"].asString, ethereumPath, ethereumSharesWords = true)
            )
        }
        // An Ethereum secret holding other words never lends its password to the universal root.
        assertEquals("", UniversalWalletPassphrase.resolve("//polkadot", "//44//60//0/0/0///TREZOR", ethereumSharesWords = false))
    }

    @Test
    fun `negative cases cannot reach the fixture addresses`() {
        vectors().forEach { vector ->
            val mnemonic = vector["mnemonic"].asString
            val passphrase = vector["passphrase"].asString
            val expected = vector.getAsJsonObject("expected")
            val expectedSolana = expected.getAsJsonObject("solana")["publicKeyHex"].asString

            // password-ignored: the empty passphrase gives the v2 address, never the password one.
            assertNotEquals(expectedSolana, SolanaKeyDerivation.deriveAccount(mnemonic).publicKey.toHex())
            assertNotEquals(
                expected.getAsJsonObject("bitcoin").getAsJsonObject("mainnet")["firstReceiveAddress"].asString,
                BitcoinKeyDerivation.deriveAccount(mnemonic).firstReceiveAddress
            )

            // passphrase-not-normalised: PBKDF2 over the raw input differs whenever NFKD changes it.
            if (Normalizer.normalize(passphrase, Normalizer.Form.NFKD) != passphrase) {
                val rawSeed = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
                    .generateSecret(PBEKeySpec(mnemonic.toCharArray(), "mnemonic$passphrase".toByteArray(Charsets.UTF_8), 2048, 512))
                    .encoded
                val raw = SolanaKeyDerivation.derivePrivateKey(rawSeed)
                assertNotEquals(expectedSolana, SolanaKeyDerivation.publicKeyFromPrivateKey(raw.privateKey).toHex())
                // Normalising first is exactly what the derivation does.
                assertArrayEquals(
                    SolanaKeyDerivation.deriveAccount(mnemonic, Normalizer.normalize(passphrase, Normalizer.Form.NFKD)).publicKey,
                    SolanaKeyDerivation.deriveAccount(mnemonic, passphrase).publicKey
                )
            }
        }
        // path-is-not-password
        assertEquals("", UniversalWalletPassphrase.resolve("//polkadot", BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH, true))
        assertEquals(listOf(""), UniversalWalletPassphrase.candidates(""))
        assertEquals(listOf("TREZOR", ""), UniversalWalletPassphrase.candidates("TREZOR"))
    }

    @Test
    fun `undecodable paths carry no password`() {
        assertEquals("", UniversalWalletPassphrase.substratePassword("no-leading-slash///TREZOR"))
        assertEquals("", UniversalWalletPassphrase.substratePassword("//a///b///c"))
        assertEquals("", UniversalWalletPassphrase.ethereumPassword("//44//60//x///TREZOR"))
        assertEquals("", UniversalWalletPassphrase.substratePassword(null))
        assertFalse(UniversalWalletPassphrase.substratePassword("///TREZOR").isEmpty())
    }

    private fun vectors(): List<JsonObject> = loadFixture().getAsJsonArray("vectors").map { it.asJsonObject }

    private fun loadFixture(): JsonObject {
        val stream = javaClass.classLoader?.getResourceAsStream("universal-wallet-v2-passphrase-vectors.json")
            ?: error("universal-wallet-v2-passphrase-vectors.json is missing from test resources")

        return stream.use { JsonParser.parseReader(InputStreamReader(it, Charsets.UTF_8)).asJsonObject }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
}
