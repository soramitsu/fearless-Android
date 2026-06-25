package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

class UniversalWalletV2VectorsTest {

    @Test
    fun `golden vectors define every universal wallet ecosystem`() {
        val fixture = loadFixture()

        assertEquals(1, fixture["version"].asInt)

        val vectors = fixture.getAsJsonArray("vectors")
        assertEquals(listOf("import12", "default24"), vectors.map { it.asJsonObject["id"].asString })
        assertEquals(setOf("bitcoin-wrong-purpose", "solana-wrong-path", "iroha-wrong-discriminant", "ton-testnet-flag"), fixture.getAsJsonArray("negativeCases").map { it.asJsonObject["id"].asString }.toSet())
        assertEquals(UNIVERSAL_ECOSYSTEM_IDS, UniversalWalletEcosystem.values().map { it.id }.toSet())

        vectors.forEach { element ->
            val vector = element.asJsonObject
            val mnemonicWords = vector["mnemonic"].asString.split(" ")

            assertEquals(vector["wordCount"].asInt, mnemonicWords.size)
            assertTrue(vector["wordCount"].asInt == 12 || vector["wordCount"].asInt == 24)

            val expected = vector.getAsJsonObject("expected")
            assertEquals(UNIVERSAL_ECOSYSTEM_IDS, expected.keySet())

            assertHex(expected.getAsJsonObject("substrate")["publicKeyHex"].asString, 32)
            assertEquals("sr25519", expected.getAsJsonObject("substrate")["cryptoType"].asString)
            assertEquals(0, expected.getAsJsonObject("substrate")["ss58Prefix"].asInt)
            assertTrue(expected.getAsJsonObject("substrate")["polkadotAddress"].asString.startsWith("1"))

            assertEquals("m/44'/60'/0'/0/0", expected.getAsJsonObject("evm")["derivationPath"].asString)
            assertTrue(EVM_ADDRESS.matches(expected.getAsJsonObject("evm")["address"].asString))

            assertBitcoin(expected.getAsJsonObject("bitcoin"))
            assertSolana(expected.getAsJsonObject("solana"))
            assertTon(expected.getAsJsonObject("ton"))
            assertIroha(expected.getAsJsonObject("iroha"))
        }
    }

    @Test
    fun `network-specific fixture values cannot be silently reused across networks`() {
        val vectors = loadFixture().getAsJsonArray("vectors").map { it.asJsonObject }

        vectors.forEach { vector ->
            val expected = vector.getAsJsonObject("expected")
            val bitcoin = expected.getAsJsonObject("bitcoin")
            val ton = expected.getAsJsonObject("ton")
            val iroha = expected.getAsJsonObject("iroha")

            assertNotEquals(bitcoin.getAsJsonObject("mainnet")["firstReceiveAddress"].asString, bitcoin.getAsJsonObject("testnet")["firstReceiveAddress"].asString)
            assertTrue(bitcoin.getAsJsonObject("mainnet")["firstReceiveAddress"].asString.startsWith("bc1q"))
            assertTrue(bitcoin.getAsJsonObject("testnet")["firstReceiveAddress"].asString.startsWith("tb1q"))

            assertFalse(ton["addressNonBounceable"].asString.startsWith("0Q"))
            assertTrue(ton["testnetNonBounceable"].asString.startsWith("0Q"))

            val taira = iroha.getAsJsonObject("taira")
            val nexus = iroha.getAsJsonObject("nexus")
            assertEquals(taira["canonicalHex"].asString, nexus["canonicalHex"].asString)
            assertEquals(369, taira["chainDiscriminant"].asInt)
            assertEquals(753, nexus["chainDiscriminant"].asInt)
            assertTrue(taira["i105"].asString.startsWith("test"))
            assertTrue(nexus["i105"].asString.startsWith("sora"))
            assertNotEquals(taira["i105"].asString, nexus["i105"].asString)
        }
    }

    @Test
    fun `registry constants define public indexers and gated Iroha networks`() {
        assertEquals("https://ti.soramitsu.io", UniversalWalletRegistry.TON_INDEXER_BASE_URL)
        assertEquals("https://si.soramitsu.io", UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL)
        assertEquals("https://blockstream.info/api", UniversalWalletRegistry.BITCOIN_MAINNET_INDEXER_BASE_URL)
        assertEquals("https://blockstream.info/testnet/api", UniversalWalletRegistry.BITCOIN_TESTNET_INDEXER_BASE_URL)

        assertEquals("bitcoin-mainnet", UniversalWalletRegistry.bitcoinMainnet.id)
        assertEquals("bitcoin:mainnet", UniversalWalletRegistry.bitcoinMainnet.chainId)
        assertEquals("Bitcoin", UniversalWalletRegistry.bitcoinMainnet.name)
        assertEquals(0, UniversalWalletRegistry.bitcoinMainnet.slip44CoinType)
        assertEquals("bc", UniversalWalletRegistry.bitcoinMainnet.addressHrp)
        assertEquals("m/84'/0'/0'", UniversalWalletRegistry.bitcoinMainnet.accountPath)
        assertEquals("m/84'/0'/0'/0/0", UniversalWalletRegistry.bitcoinMainnet.firstReceivePath)
        assertEquals("https://blockstream.info/api", UniversalWalletRegistry.bitcoinMainnet.indexerBaseUrl)
        assertEquals(20, UniversalWalletRegistry.bitcoinMainnet.defaultGapLimit)
        assertEquals("BTC", UniversalWalletRegistry.bitcoinMainnet.nativeAsset.id)
        assertEquals("BTC", UniversalWalletRegistry.bitcoinMainnet.nativeAsset.symbol)
        assertEquals(8, UniversalWalletRegistry.bitcoinMainnet.nativeAsset.decimals)
        assertTrue(UniversalWalletRegistry.bitcoinMainnet.enabledByDefault)

        assertEquals("bitcoin-testnet", UniversalWalletRegistry.bitcoinTestnet.id)
        assertEquals("bitcoin:testnet", UniversalWalletRegistry.bitcoinTestnet.chainId)
        assertEquals("Bitcoin Testnet", UniversalWalletRegistry.bitcoinTestnet.name)
        assertEquals(1, UniversalWalletRegistry.bitcoinTestnet.slip44CoinType)
        assertEquals("tb", UniversalWalletRegistry.bitcoinTestnet.addressHrp)
        assertEquals("m/84'/1'/0'", UniversalWalletRegistry.bitcoinTestnet.accountPath)
        assertEquals("m/84'/1'/0'/0/0", UniversalWalletRegistry.bitcoinTestnet.firstReceivePath)
        assertEquals("https://blockstream.info/testnet/api", UniversalWalletRegistry.bitcoinTestnet.indexerBaseUrl)
        assertEquals(20, UniversalWalletRegistry.bitcoinTestnet.defaultGapLimit)
        assertEquals("BTC", UniversalWalletRegistry.bitcoinTestnet.nativeAsset.id)
        assertEquals("BTC", UniversalWalletRegistry.bitcoinTestnet.nativeAsset.symbol)
        assertEquals(8, UniversalWalletRegistry.bitcoinTestnet.nativeAsset.decimals)
        assertFalse(UniversalWalletRegistry.bitcoinTestnet.enabledByDefault)

        assertEquals("solana-mainnet", UniversalWalletRegistry.solanaMainnet.id)
        assertEquals("solana:mainnet", UniversalWalletRegistry.solanaMainnet.chainId)
        assertEquals("Solana", UniversalWalletRegistry.solanaMainnet.name)
        assertEquals("https://si.soramitsu.io", UniversalWalletRegistry.solanaMainnet.indexerBaseUrl)
        assertEquals("https://api.mainnet-beta.solana.com", UniversalWalletRegistry.solanaMainnet.rpcUrl)
        assertEquals("SOL", UniversalWalletRegistry.solanaMainnet.nativeAsset.id)
        assertEquals("SOL", UniversalWalletRegistry.solanaMainnet.nativeAsset.symbol)
        assertEquals(9, UniversalWalletRegistry.solanaMainnet.nativeAsset.decimals)
        assertTrue(UniversalWalletRegistry.solanaMainnet.enabledByDefault)

        assertEquals("solana-devnet", UniversalWalletRegistry.solanaDevnet.id)
        assertEquals("solana:devnet", UniversalWalletRegistry.solanaDevnet.chainId)
        assertEquals("Solana Devnet", UniversalWalletRegistry.solanaDevnet.name)
        assertEquals("https://si.soramitsu.io", UniversalWalletRegistry.solanaDevnet.indexerBaseUrl)
        assertEquals("https://api.devnet.solana.com", UniversalWalletRegistry.solanaDevnet.rpcUrl)
        assertEquals("SOL", UniversalWalletRegistry.solanaDevnet.nativeAsset.id)
        assertEquals("SOL", UniversalWalletRegistry.solanaDevnet.nativeAsset.symbol)
        assertEquals(9, UniversalWalletRegistry.solanaDevnet.nativeAsset.decimals)
        assertFalse(UniversalWalletRegistry.solanaDevnet.enabledByDefault)

        assertEquals("taira-testnet", UniversalWalletRegistry.taira.id)
        assertEquals("iroha3-taira", UniversalWalletRegistry.taira.chainId)
        assertEquals(369, UniversalWalletRegistry.taira.chainDiscriminant)
        assertEquals("https://taira.sora.org", UniversalWalletRegistry.taira.toriiBaseUrl)
        assertEquals("/v1/mcp", UniversalWalletRegistry.taira.mcpPath)
        assertTrue(UniversalWalletRegistry.taira.enabledByDefault)

        assertEquals("sora-nexus-mainnet", UniversalWalletRegistry.nexus.id)
        assertEquals("sora:nexus:global", UniversalWalletRegistry.nexus.chainId)
        assertEquals(753, UniversalWalletRegistry.nexus.chainDiscriminant)
        assertEquals("https://minamoto.sora.org", UniversalWalletRegistry.nexus.toriiBaseUrl)
        assertEquals("/v1/mcp", UniversalWalletRegistry.nexus.mcpPath)
        assertFalse(UniversalWalletRegistry.nexus.enabledByDefault)
    }

    @Test
    fun `derivation constants match fixture defaults`() {
        val vector = loadFixture().getAsJsonArray("vectors").first().asJsonObject.getAsJsonObject("expected")

        assertEquals(UniversalWalletDerivationPaths.SUBSTRATE_ROOT, vector.getAsJsonObject("substrate")["derivationPath"].asString)
        assertEquals(UniversalWalletDerivationPaths.EVM_DEFAULT, vector.getAsJsonObject("evm")["derivationPath"].asString)
        assertEquals(UniversalWalletDerivationPaths.BITCOIN_MAINNET_ACCOUNT, vector.getAsJsonObject("bitcoin").getAsJsonObject("mainnet")["accountPath"].asString)
        assertEquals(UniversalWalletDerivationPaths.BITCOIN_MAINNET_FIRST_RECEIVE, vector.getAsJsonObject("bitcoin").getAsJsonObject("mainnet")["firstReceivePath"].asString)
        assertEquals(UniversalWalletDerivationPaths.BITCOIN_TESTNET_ACCOUNT, vector.getAsJsonObject("bitcoin").getAsJsonObject("testnet")["accountPath"].asString)
        assertEquals(UniversalWalletDerivationPaths.BITCOIN_TESTNET_FIRST_RECEIVE, vector.getAsJsonObject("bitcoin").getAsJsonObject("testnet")["firstReceivePath"].asString)
        assertEquals(UniversalWalletDerivationPaths.SOLANA_DEFAULT, vector.getAsJsonObject("solana")["derivationPath"].asString)
        assertEquals(UniversalWalletDerivationPaths.TON_DEFAULT, vector.getAsJsonObject("ton")["derivationPath"].asString)
        assertEquals(UniversalWalletDerivationPaths.IROHA_DEFAULT, vector.getAsJsonObject("iroha")["derivationPath"].asString)
    }

    private fun loadFixture(): JsonObject {
        val stream = javaClass.classLoader?.getResourceAsStream("universal-wallet-v2-vectors.json")
            ?: error("universal-wallet-v2-vectors.json is missing from test resources")

        return stream.use {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject
        }
    }

    private fun assertBitcoin(bitcoin: JsonObject) {
        val mainnet = bitcoin.getAsJsonObject("mainnet")
        val testnet = bitcoin.getAsJsonObject("testnet")

        assertEquals("m/84'/0'/0'", mainnet["accountPath"].asString)
        assertEquals("m/84'/0'/0'/0/0", mainnet["firstReceivePath"].asString)
        assertTrue(mainnet["accountXpub"].asString.startsWith("xpub"))
        assertTrue(mainnet["firstReceiveAddress"].asString.startsWith("bc1q"))

        assertEquals("m/84'/1'/0'", testnet["accountPath"].asString)
        assertEquals("m/84'/1'/0'/0/0", testnet["firstReceivePath"].asString)
        assertTrue(testnet["accountXpub"].asString.startsWith("xpub"))
        assertTrue(testnet["firstReceiveAddress"].asString.startsWith("tb1q"))
    }

    private fun assertSolana(solana: JsonObject) {
        assertEquals("m/44'/501'/0'/0'", solana["derivationPath"].asString)
        assertHex(solana["publicKeyHex"].asString, 32)
        assertTrue(BASE58_32_TO_44.matches(solana["address"].asString))
    }

    private fun assertTon(ton: JsonObject) {
        assertEquals("m/44'/607'/0'/0'/0'", ton["derivationPath"].asString)
        assertEquals("v4r2", ton["walletVersion"].asString)
        assertEquals(0, ton["workchain"].asInt)
        assertHex(ton["publicKeyHex"].asString, 32)
        assertTrue(ton["addressBounceable"].asString.startsWith("EQ"))
        assertTrue(ton["addressNonBounceable"].asString.startsWith("UQ"))
        assertTrue(ton["testnetNonBounceable"].asString.startsWith("0Q"))
    }

    private fun assertIroha(iroha: JsonObject) {
        assertEquals("m/44'/617'/0'/0'", iroha["derivationPath"].asString)

        listOf("taira", "nexus").forEach { network ->
            val address = iroha.getAsJsonObject(network)
            assertHex(address["publicKeyHex"].asString, 32)
            assertTrue(address["canonicalHex"].asString.startsWith("0x02000120"))
            assertTrue(address["i105"].asString.isNotBlank())
        }
    }

    private fun assertHex(value: String, byteLength: Int) {
        assertTrue(Regex("^[0-9a-f]+$").matches(value))
        assertEquals(byteLength * 2, value.length)
    }

    private companion object {
        val UNIVERSAL_ECOSYSTEM_IDS = setOf("substrate", "evm", "ton", "bitcoin", "solana", "iroha")
        val EVM_ADDRESS = Regex("^0x[0-9a-fA-F]{40}$")
        val BASE58_32_TO_44 = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")
    }
}
