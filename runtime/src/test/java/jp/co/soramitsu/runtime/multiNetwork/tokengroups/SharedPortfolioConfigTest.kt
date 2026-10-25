package jp.co.soramitsu.runtime.multiNetwork.tokengroups

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonMainnetChainId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPortfolioConfigTest {

    private val config = bundledConfig()
    private val reviewedChains = reviewedRegistryChains()

    @Test
    fun `bundled testnet list covers the shared seed and the networks this app adds itself`() {
        val testnets = requireNotNull(config.testnetChainIds)
        val sharedSeed = listOf(
            WESTEND_CHAIN_ID,
            "91bc6e169807aaa54802737e1c504b2577d4fafedd5a02c10293b1cd60e39527", // Moonbase Alpha
            "a37725fd8943d2a524cb7ecc65da438f9fa644db78ba24dcd0003e2f95645e8f", // Canary Matrixchain
            CAGA_ANKARA_CHAIN_ID,
            "d3d2f3a3495dc597434a99d7d449ebad6616db45e4e4f178f31cc6fa14378b70", // Avail Turing Testnet
            "1001", // Kaia Kairos
            "-3" // TON Testnet
        )
        val appOwnedTestnets = listOf(
            UniversalWalletRegistry.bitcoinTestnet.id,
            UniversalWalletRegistry.bitcoinTestnet.chainId,
            UniversalWalletRegistry.solanaDevnet.id,
            UniversalWalletRegistry.solanaDevnet.chainId,
            UniversalWalletRegistry.taira.id,
            UniversalWalletRegistry.taira.chainId
        )

        (sharedSeed + appOwnedTestnets).forEach { chainId ->
            assertTrue("$chainId must be a shared testnet", testnets.contains(chainId))
        }
        assertFalse(testnets.contains(UniversalWalletRegistry.bitcoinMainnet.chainId))
        assertFalse(testnets.contains(UniversalWalletRegistry.solanaMainnet.chainId))
        assertFalse(testnets.contains(UniversalWalletRegistry.nexus.chainId))
    }

    @Test
    fun `every registry testnet in the reviewed snapshot is on the shared list`() {
        val testnets = requireNotNull(config.testnetChainIds)

        reviewedChains.filter { it.isTestNet }.forEach { chain ->
            assertTrue("${chain.name} has the testnet option but is not listed", testnets.contains(chain.id))
        }
    }

    @Test
    fun `isTestnet honours the registry option and the shared list`() {
        val caga = reviewedChains.chain(CAGA_ANKARA_CHAIN_ID)
        val westend = reviewedChains.chain(WESTEND_CHAIN_ID)
        val polkadot = reviewedChains.chain(POLKADOT_CHAIN_ID)
        val withoutList = SharedPortfolioConfig(
            tokenGroups = config.tokenGroups,
            testnetChainIds = null,
            defaultVisibleAtZero = null,
            untrustedAssets = config.untrustedAssets
        )

        assertFalse("CAGA Ankara has no testnet option in the registry", caga.isTestNet)
        assertTrue(config.isTestnet(caga))
        assertTrue(config.isTestnet(westend))
        assertFalse(config.isTestnet(polkadot))

        assertFalse(withoutList.isTestnet(caga))
        assertTrue(withoutList.isTestnet(westend))
    }

    @Test
    fun `bundled zero list holds the ranked mainnet utilities plus TON, Bitcoin and Solana`() {
        val zeroDefaults = requireNotNull(config.defaultVisibleAtZero)

        assertEquals(
            listOf(
                "xor", "eth", "BNB", "pol", "eth", "eth", "avax", "eth", "dot", "ksm", "pen", "movr", "glmr", "okb", "anlog",
                "ton", "BTC", "SOL"
            ),
            zeroDefaults.entries.map(DefaultVisibleAtZeroEntry::symbol)
        )
        assertEquals(
            List(15) { DefaultVisibleAtZeroEntry.Reason.RankedMainnetUtility } + List(3) { DefaultVisibleAtZeroEntry.Reason.EcosystemPrimary },
            zeroDefaults.entries.map(DefaultVisibleAtZeroEntry::reason)
        )
    }

    @Test
    fun `every registry zero default is the primary utility asset of a mainnet in the reviewed snapshot`() {
        val zeroDefaults = requireNotNull(config.defaultVisibleAtZero)
        val chainsById = reviewedChains.associateBy { it.id }

        zeroDefaults.entries.filter { it.ecosystem !in setOf(SharedPortfolioEcosystem.Bitcoin, SharedPortfolioEcosystem.Solana) }
            .forEach { entry ->
                val chain = requireNotNull(chainsById[entry.chainId]) { "${entry.symbol} chain ${entry.chainId} is not in the registry" }
                assertFalse(config.isTestnet(chain))
                assertEquals("${entry.symbol} on ${chain.name}", entry.assetId, chain.utilityAsset?.id)
                assertEquals(entry.rank, chain.rank)
            }
    }

    @Test
    fun `zero defaults for app-owned networks use this app's chain and asset ids`() {
        val zeroDefaults = requireNotNull(config.defaultVisibleAtZero)

        assertTrue(zeroDefaults.contains(UniversalWalletRegistry.bitcoinMainnet.chainId, UniversalWalletRegistry.bitcoinMainnet.nativeAsset.id))
        assertTrue(zeroDefaults.contains(UniversalWalletRegistry.solanaMainnet.chainId, UniversalWalletRegistry.solanaMainnet.nativeAsset.id))
        assertEquals(tonMainnetChainId, zeroDefaults.entries.single { it.ecosystem == SharedPortfolioEcosystem.Ton }.chainId)
        assertFalse(zeroDefaults.contains(UniversalWalletRegistry.bitcoinTestnet.chainId, UniversalWalletRegistry.bitcoinTestnet.nativeAsset.id))
    }

    @Test
    fun `zero list matches evm asset ids case-insensitively and every other id exactly`() {
        val zeroDefaults = requireNotNull(config.defaultVisibleAtZero)

        assertTrue(zeroDefaults.contains(ETHEREUM_CHAIN_ID, ETHEREUM_ETH_ASSET_ID))
        assertTrue(zeroDefaults.contains(ETHEREUM_CHAIN_ID, ETHEREUM_ETH_ASSET_ID.uppercase()))
        assertTrue(zeroDefaults.contains("137", "0x0000000000000000000000000000000000001010"))
        assertTrue(zeroDefaults.contains(POLKADOT_CHAIN_ID, DOT_ASSET_ID))
        assertFalse(zeroDefaults.contains(POLKADOT_CHAIN_ID, DOT_ASSET_ID.uppercase()))
        assertFalse(zeroDefaults.contains(POLKADOT_ASSET_HUB_CHAIN_ID, DOT_ASSET_ID))
    }

    @Test
    fun `testnet and zero lists in a shape this build does not know are rejected`() {
        assertThrows(Exception::class.java) {
            TestnetChainIds.parse(mutated(SharedPortfolioConfigSource.TESTNET_CHAIN_IDS_ASSET) { addProperty("version", 2) })
        }
        assertThrows(Exception::class.java) {
            TestnetChainIds.parse(mutated(SharedPortfolioConfigSource.TESTNET_CHAIN_IDS_ASSET) { firstListed("chainIds").addProperty("mainnet", true) })
        }
        assertThrows(Exception::class.java) {
            TestnetChainIds.parse(mutated(SharedPortfolioConfigSource.TESTNET_CHAIN_IDS_ASSET) { firstListed("chainIds").addProperty("source", "remote") })
        }
        assertThrows(Exception::class.java) {
            DefaultVisibleAtZero.parse(mutated(SharedPortfolioConfigSource.DEFAULT_VISIBLE_AT_ZERO_ASSET) { firstListed("entries").addProperty("ecosystem", "cosmos") })
        }
        assertThrows(Exception::class.java) {
            DefaultVisibleAtZero.parse(mutated(SharedPortfolioConfigSource.DEFAULT_VISIBLE_AT_ZERO_ASSET) { firstListed("entries").addProperty("reason", "popular") })
        }
        assertThrows(Exception::class.java) {
            DefaultVisibleAtZero.parse(mutated(SharedPortfolioConfigSource.DEFAULT_VISIBLE_AT_ZERO_ASSET) { firstListed("entries").remove("rank") })
        }
        assertThrows(Exception::class.java) {
            DefaultVisibleAtZero.parse(
                mutated(SharedPortfolioConfigSource.DEFAULT_VISIBLE_AT_ZERO_ASSET) {
                    getAsJsonArray("entries").add(firstListed("entries").deepCopy())
                }
            )
        }
    }

    @Test
    fun `one unusable file does not disable the other two`() {
        val failures = mutableListOf<String>()
        val source = SharedPortfolioConfigSource(
            readAsset = { name ->
                if (name == SharedPortfolioConfigSource.TESTNET_CHAIN_IDS_ASSET) error("asset missing") else bundledAsset(name)
            },
            onLoadFailure = { name, _ -> failures += name }
        )

        val loaded = source.config

        assertNotNull(loaded.tokenGroups)
        assertNull(loaded.testnetChainIds)
        assertNotNull(loaded.defaultVisibleAtZero)
        assertEquals(listOf(SharedPortfolioConfigSource.TESTNET_CHAIN_IDS_ASSET), failures)
        assertTrue("the registry testnet option still applies", loaded.isTestnet(reviewedChains.chain(WESTEND_CHAIN_ID)))
    }

    @Test
    fun `the untrusted list survives a groups catalog this build cannot read`() {
        val failures = mutableListOf<String>()
        val unreadableCatalog = mutated(SharedPortfolioConfigSource.TOKEN_GROUPS_ASSET) {
            getAsJsonArray("groups")[0].asJsonObject.addProperty("renderHint", "from a newer build")
        }
        val loaded = SharedPortfolioConfigSource(
            readAsset = { name ->
                if (name == SharedPortfolioConfigSource.TOKEN_GROUPS_ASSET) unreadableCatalog else bundledAsset(name)
            },
            onLoadFailure = { name, _ -> failures += name }
        ).config

        assertNull("the catalog falls back to no groups", loaded.tokenGroups)
        assertEquals(listOf(SharedPortfolioConfigSource.TOKEN_GROUPS_ASSET), failures)
        assertTrue(
            "a rejected catalog must not give the third-party usdt its registry price back",
            loaded.isPriceUntrusted(KUSAMA_ASSET_HUB_CHAIN_ID, KUSAMA_ASSET_HUB_THIRD_PARTY_USDT_ASSET_ID)
        )
        assertFalse(loaded.isPriceUntrusted(POLKADOT_CHAIN_ID, DOT_ASSET_ID))
    }

    @Test
    fun `a salvaged untrusted list keeps every entry that names a chain and an asset`() {
        val salvaged = UntrustedAssets.salvage(
            mutated(SharedPortfolioConfigSource.TOKEN_GROUPS_ASSET) {
                addProperty("version", 2)
                val entries = getAsJsonArray("untrustedAssets")
                entries.add(JsonObject().apply { addProperty("chainId", POLKADOT_CHAIN_ID); addProperty("assetId", "new-shape"); addProperty("level", 3) })
                entries.add(JsonObject().apply { addProperty("chainId", POLKADOT_CHAIN_ID) })
                entries.add("not an object")
            }
        )

        assertTrue(salvaged.contains(KUSAMA_ASSET_HUB_CHAIN_ID, KUSAMA_ASSET_HUB_THIRD_PARTY_USDT_ASSET_ID))
        assertTrue(salvaged.contains(POLKADOT_CHAIN_ID, "new-shape"))
        assertEquals(2, salvaged.entries.size)
    }

    @Test
    fun `without any usable file the documented fallbacks apply`() {
        val failures = mutableListOf<String>()
        val loaded = SharedPortfolioConfigSource(
            readAsset = { "not json".toByteArray() },
            onLoadFailure = { name, _ -> failures += name }
        ).config

        assertNull(loaded.tokenGroups)
        assertNull(loaded.testnetChainIds)
        assertNull(loaded.defaultVisibleAtZero)
        assertNull(loaded.untrustedAssets)
        assertFalse(loaded.isPriceUntrusted(KUSAMA_ASSET_HUB_CHAIN_ID, KUSAMA_ASSET_HUB_THIRD_PARTY_USDT_ASSET_ID))
        assertEquals(3, failures.size)
        assertEquals(ResolvedTokenGroups.EMPTY, resolveTokenGroups(loaded.tokenGroups, reviewedChains, loaded::isTestnet))
        assertFalse(loaded.isTestnet(reviewedChains.chain(CAGA_ANKARA_CHAIN_ID)))
    }

    @Test
    fun `the bundled files are read once`() {
        var reads = 0
        val source = SharedPortfolioConfigSource(readAsset = { name -> reads++; bundledAsset(name) })

        repeat(3) { source.config }

        assertEquals(3, reads)
    }

    private fun mutated(assetName: String, mutate: JsonObject.() -> Unit): ByteArray {
        return JsonParser.parseString(bundledAsset(assetName).decodeToString()).asJsonObject.apply(mutate).toBytes()
    }

    private fun JsonObject.firstListed(name: String): JsonObject = getAsJsonArray(name)[0].asJsonObject
}
