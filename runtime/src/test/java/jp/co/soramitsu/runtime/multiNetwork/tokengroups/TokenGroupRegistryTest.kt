package jp.co.soramitsu.runtime.multiNetwork.tokengroups

import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenGroupRegistryTest {

    private val config = bundledConfig()
    private val catalog = requireNotNull(config.tokenGroups)
    private val reviewedChains = reviewedRegistryChains()

    @Test
    fun `every approved member resolves against the reviewed registry snapshot`() {
        val resolved = resolve(reviewedChains)

        assertEquals(listOf("dot", "ksm", "eth", "usdt", "usdc"), resolved.groups.map(ResolvedTokenGroup::id))
        catalog.renderableGroups.filter { it.members.size > 1 }.forEach { group ->
            assertEquals(
                "${group.id} must resolve every curated member",
                group.members.map { it.chainId to it.assetId },
                resolved.members(group.id).map { it.chainId to it.assetId }
            )
        }
        assertEquals(listOf(10, 12, 18, 6, 6), resolved.groups.map(ResolvedTokenGroup::precision))
    }

    @Test
    fun `the single-network btc group never renders as a combined row`() {
        val resolved = resolve(reviewedChains)

        assertTrue(resolved.groups.none { it.id == "btc" })
        assertNull(resolved.groupOf("bitcoin:mainnet", "BTC"))
    }

    @Test
    fun `the bundled offline registry never resolves a curated member to a different asset`() {
        val bundledChains = bundledRegistryChains()
        val resolved = resolve(bundledChains)

        catalog.renderableGroups.filter { it.members.size > 1 }.forEach { group ->
            val active = resolved.members(group.id).map { it.chainId to it.assetId }.toSet()
            group.members.forEach { member ->
                val presentOffline = bundledChains.any { chain ->
                    chain.id == member.chainId && chain.assets.any { it.id.equals(member.assetId, ignoreCase = true) }
                }
                if (presentOffline) {
                    assertTrue("${group.id} ${member.chainId} drifted in local_chains.json", (member.chainId to member.assetId) in active)
                }
            }
        }
        assertEquals(listOf("dot", "ksm", "eth", "usdt", "usdc"), resolved.groups.map(ResolvedTokenGroup::id))
    }

    @Test
    fun `symbol mismatch member is inactive (WETH listed under eth)`() {
        val chains = reviewedChains.replaceAsset(ARBITRUM_CHAIN_ID, ARBITRUM_ETH_ASSET_ID) { it.copy(symbol = "weth") }

        assertEthMembers(chains, ETHEREUM_CHAIN_ID, OPTIMISM_CHAIN_ID, POLYGON_ZKEVM_CHAIN_ID)
        assertNull(resolve(chains).groupOf(ARBITRUM_CHAIN_ID, ARBITRUM_ETH_ASSET_ID))
    }

    @Test
    fun `precision mismatch member is inactive`() {
        val chains = reviewedChains.replaceAsset(OPTIMISM_CHAIN_ID, OPTIMISM_ETH_ASSET_ID) { it.copy(precision = 8) }

        assertEthMembers(chains, ETHEREUM_CHAIN_ID, ARBITRUM_CHAIN_ID, POLYGON_ZKEVM_CHAIN_ID)
    }

    @Test
    fun `priceId mismatch member is inactive`() {
        val chains = reviewedChains.replaceAsset(POLYGON_ZKEVM_CHAIN_ID, POLYGON_ZKEVM_ETH_ASSET_ID) { it.copy(priceId = "weth") }
        val unpriced = reviewedChains.replaceAsset(POLYGON_ZKEVM_CHAIN_ID, POLYGON_ZKEVM_ETH_ASSET_ID) { it.copy(priceId = null) }

        assertEthMembers(chains, ETHEREUM_CHAIN_ID, ARBITRUM_CHAIN_ID, OPTIMISM_CHAIN_ID)
        assertEthMembers(unpriced, ETHEREUM_CHAIN_ID, ARBITRUM_CHAIN_ID, OPTIMISM_CHAIN_ID)
    }

    @Test
    fun `testnet chain member is inactive`() {
        val registryTestnet = reviewedChains.replaceChain(ARBITRUM_CHAIN_ID) { it.copy(isTestNet = true) }
        val listedTestnet = SharedPortfolioConfig(
            tokenGroups = catalog,
            testnetChainIds = TestnetChainIds(catalog.registry, setOf(OPTIMISM_CHAIN_ID)),
            defaultVisibleAtZero = null,
            untrustedAssets = null
        )

        assertEthMembers(registryTestnet, ETHEREUM_CHAIN_ID, OPTIMISM_CHAIN_ID, POLYGON_ZKEVM_CHAIN_ID)
        assertEquals(
            listOf(ETHEREUM_CHAIN_ID, ARBITRUM_CHAIN_ID, POLYGON_ZKEVM_CHAIN_ID),
            resolveTokenGroups(catalog, reviewedChains, listedTestnet::isTestnet).members("eth").map { it.chainId }
        )
    }

    @Test
    fun `detected asset member is inactive`() {
        val chains = reviewedChains.replaceAsset(ARBITRUM_CHAIN_ID, ARBITRUM_ETH_ASSET_ID) { it.copy(type = ChainAssetType.Unknown) }

        assertEthMembers(chains, ETHEREUM_CHAIN_ID, OPTIMISM_CHAIN_ID, POLYGON_ZKEVM_CHAIN_ID)
    }

    @Test
    fun `group with one active member renders ungrouped`() {
        val onlyEthereum = reviewedChains.filterNot { it.id in setOf(ARBITRUM_CHAIN_ID, OPTIMISM_CHAIN_ID, POLYGON_ZKEVM_CHAIN_ID) }
        val resolved = resolve(onlyEthereum)

        assertTrue(resolved.groups.none { it.id == "eth" })
        assertNull(resolved.groupOf(ETHEREUM_CHAIN_ID, ETHEREUM_ETH_ASSET_ID))
        assertTrue(resolved.members("eth").isEmpty())
        assertEquals(listOf("dot", "ksm", "usdt", "usdc"), resolved.groups.map(ResolvedTokenGroup::id))
    }

    @Test
    fun `ids are matched by chain and asset, not by asset id alone`() {
        val polkadot = reviewedChains.chain(POLKADOT_CHAIN_ID)
        val lookalike = polkadot.copy(id = "lookalike-relay", name = "Lookalike", isTestNet = false)
        val resolved = resolve(reviewedChains + lookalike)

        assertEquals("dot", resolved.groupOf(POLKADOT_CHAIN_ID, DOT_ASSET_ID)?.id)
        assertEquals("dot", resolved.groupOf(POLKADOT_ASSET_HUB_CHAIN_ID, DOT_ASSET_ID)?.id)
        assertNull(resolved.groupOf("lookalike-relay", DOT_ASSET_ID))
        assertNull(resolved.groupOf(POLKADOT_CHAIN_ID, "887A17C7-1370-4DE0-97DD-5422E294FA75"))
        assertEquals(12, resolved.members("dot").size)
    }

    @Test
    fun `evm contract ids match case-insensitively and every other id exactly`() {
        val checksummed = "0xaf88d065e77c8cC2239327C5EDb3A432268e5831"
        val evmChains = reviewedChains.replaceAsset(ARBITRUM_CHAIN_ID, ARBITRUM_USDC_ASSET_ID) { it.copy(id = checksummed) }
        val substrateChains = reviewedChains.replaceAsset(POLKADOT_CHAIN_ID, DOT_ASSET_ID) { it.copy(id = DOT_ASSET_ID.uppercase()) }

        val evm = resolve(evmChains)
        assertEquals("usdc", evm.groupOf(ARBITRUM_CHAIN_ID, checksummed)?.id)
        assertTrue(ARBITRUM_CHAIN_ID to checksummed in evm.members("usdc").map { it.chainId to it.assetId })

        val substrate = resolve(substrateChains)
        assertNull(substrate.groupOf(POLKADOT_CHAIN_ID, DOT_ASSET_ID.uppercase()))
        assertEquals(11, substrate.members("dot").size)
    }

    @Test
    fun `an ambiguous live registry makes the member inactive`() {
        val twinAsset = reviewedChains.replaceChain(ARBITRUM_CHAIN_ID) { chain ->
            val usdc = chain.assets.single { it.id == ARBITRUM_USDC_ASSET_ID }
            chain.copy(assets = chain.assets + usdc.copy(id = ARBITRUM_USDC_ASSET_ID.uppercase().replace("0X", "0x")))
        }
        val twinChain = reviewedChains + reviewedChains.chain(OPTIMISM_CHAIN_ID)

        assertTrue(resolve(twinAsset).members("usdc").none { it.chainId == ARBITRUM_CHAIN_ID })
        assertEquals(5, resolve(twinAsset).members("usdc").size)
        assertEthMembers(twinChain, ETHEREUM_CHAIN_ID, ARBITRUM_CHAIN_ID, POLYGON_ZKEVM_CHAIN_ID)
    }

    @Test
    fun `members of a kind this build does not match are skipped`() {
        val mutated = catalogWith { group("eth").getAsJsonArray("members")[1].asJsonObject.addProperty("kind", "solanaMint") }

        assertEquals(
            listOf(ETHEREUM_CHAIN_ID, OPTIMISM_CHAIN_ID, POLYGON_ZKEVM_CHAIN_ID),
            resolveTokenGroups(mutated, reviewedChains, config::isTestnet).members("eth").map { it.chainId }
        )
    }

    @Test
    fun `a member symbol needs the group symbol or a reviewed alias`() {
        val withoutAliases = catalogWith { group("dot").remove("symbolAliases") }
        val resolved = resolveTokenGroups(withoutAliases, reviewedChains, config::isTestnet)

        assertEquals(11, resolved.members("dot").size)
        assertTrue(resolved.members("dot").none { it.chainId == MOONBEAM_CHAIN_ID })
        assertEquals("dot", resolve(reviewedChains).groupOf(MOONBEAM_CHAIN_ID, "7e4e064e-2b23-4eb5-96db-e6491c4031e5")?.id)
    }

    @Test
    fun `without a usable catalog nothing is grouped`() {
        assertEquals(ResolvedTokenGroups.EMPTY, resolveTokenGroups(null, reviewedChains, config::isTestnet))
        assertEquals(ResolvedTokenGroups.EMPTY, resolve(emptyList()))
    }

    @Test
    fun `the registry re-resolves whenever the chains change`() = runTest {
        val chainsRepository = mockk<ChainsRepository>()
        val withoutArbitrum = reviewedChains.filterNot { it.id == ARBITRUM_CHAIN_ID }
        every { chainsRepository.chainsFlow() } returns flowOf(withoutArbitrum, withoutArbitrum, reviewedChains)
        val registry = TokenGroupRegistry(SharedPortfolioConfigSource(::bundledAsset), chainsRepository)

        val emissions = registry.observeResolvedGroups().toList()

        assertEquals(2, emissions.size)
        assertEquals(3, emissions.first().members("eth").size)
        assertEquals(4, emissions.last().members("eth").size)
        assertEquals(resolve(reviewedChains), registry.resolve(reviewedChains))
    }

    @Test
    fun `an unusable bundled catalog leaves every asset as its own row`() = runTest {
        val chainsRepository = mockk<ChainsRepository>()
        every { chainsRepository.chainsFlow() } returns flowOf(reviewedChains)
        val source = SharedPortfolioConfigSource(readAsset = { name ->
            if (name == SharedPortfolioConfigSource.TOKEN_GROUPS_ASSET) "{}".toByteArray() else bundledAsset(name)
        })

        assertEquals(ResolvedTokenGroups.EMPTY, TokenGroupRegistry(source, chainsRepository).observeResolvedGroups().first())
    }

    private fun resolve(chains: List<Chain>): ResolvedTokenGroups = resolveTokenGroups(catalog, chains, config::isTestnet)

    private fun assertEthMembers(chains: List<Chain>, vararg expectedChainIds: String) {
        assertEquals(expectedChainIds.toList(), resolve(chains).members("eth").map { it.chainId })
    }

    private fun catalogWith(mutate: JsonObject.() -> Unit): TokenGroupCatalog {
        return TokenGroupCatalog.parse(bundledCatalogJson().apply(mutate).toBytes())
    }
}
