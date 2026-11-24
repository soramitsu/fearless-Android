package jp.co.soramitsu.runtime.multiNetwork

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.CAGA_ANKARA_CHAIN_ID
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.bundledConfig
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.reviewedRegistryChains
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ChainActivationPolicyTest {

    @Test
    fun `staking Hub keeps its relay clock connected after relay staking is gone`() {
        val stakingAsset = mock(Asset::class.java).also { `when`(it.staking).thenReturn(Asset.StakingType.RELAYCHAIN) }
        val hub = chain(AssetHubStaking.POLKADOT).also { `when`(it.assets).thenReturn(listOf(stakingAsset)) }
        val relay = chain(polkadotChainId)
        val otherHub = chain(AssetHubStaking.KUSAMA)
        val otherRelay = chain(kusamaChainId)

        val selected = selectChainsForBackgroundSync(listOf(hub, relay, otherHub, otherRelay), emptySet())

        assertEquals(setOf(hub.id, relay.id), selected.map { it.id }.toSet())
    }

    @Test
    fun `native Bitcoin balance subscription does not require a websocket node`() {
        val bitcoin = chain("bitcoin-mainnet", rank = 1)
        `when`(bitcoin.nodes).thenReturn(emptyList())

        assertEquals(listOf(bitcoin), selectChainsForBackgroundSync(listOf(bitcoin), emptySet()))
    }

    @Test
    fun `stale crowdloan metadata alone cannot activate a network`() {
        val legacyOnly = chain("legacy-crowdloan-chain").also { chain ->
            `when`(chain.hasCrowdloans).thenReturn(true)
        }

        val selected = selectChainsForBackgroundSync(
            chains = listOf(legacyOnly),
            chainIdsNeedingSync = emptySet()
        )

        assertTrue(selected.isEmpty())
    }

    @Test
    fun `ranked testnet is not popular`() {
        val westend = chain("westend", rank = 108, isTestNet = true)
        val listedTestnet = chain("caga-ankara", rank = 120)
        val polkadot = chain("polkadot", rank = 8)

        val selected = selectChainsForBackgroundSync(
            chains = listOf(westend, listedTestnet, polkadot),
            chainIdsNeedingSync = emptySet(),
            isTestnet = { it.isTestNet || it.id == "caga-ankara" }
        )

        assertEquals(listOf("polkadot"), selected.map(Chain::id))
    }

    @Test
    fun `chain with a funded auto or explicitly shown asset is synced, testnets included`() {
        val unrankedWithFundedAutoAsset = chain("asset-hub-route")
        val testnetWithShownAsset = chain("westend", rank = 108, isTestNet = true)
        val untouched = chain("untouched")

        val selected = selectChainsForBackgroundSync(
            chains = listOf(unrankedWithFundedAutoAsset, testnetWithShownAsset, untouched),
            chainIdsNeedingSync = setOf("asset-hub-route", "westend")
        )

        assertEquals(listOf("asset-hub-route", "westend"), selected.map(Chain::id).sorted())
    }

    /**
     * The production registry: a wallet that holds nothing connects the ranked mainnets and the
     * networks a live feature offers, and no testnet for its rank alone.
     */
    @Test
    fun `reviewed registry connects ranked mainnets and no testnet for its rank alone`() {
        val config = bundledConfig()
        val chains = reviewedRegistryChains()

        val selected = selectChainsForBackgroundSync(chains, chainIdsNeedingSync = emptySet(), isTestnet = config::isTestnet)

        val rankedMainnets = chains.filter { it.rank != null && !config.isTestnet(it) }
        assertEquals(15, rankedMainnets.size)
        assertTrue(selected.map(Chain::id).containsAll(rankedMainnets.map(Chain::id)))
        selected.filter(config::isTestnet).forEach { testnet ->
            assertTrue(
                "${testnet.name} is a testnet and may only sync for staking",
                testnet.assets.any { it.staking != Asset.StakingType.UNSUPPORTED || it.supportStakingPool }
            )
        }
        assertTrue(selected.none { it.id == CAGA_ANKARA_CHAIN_ID })
    }

    @Test
    fun `a testnet without a live feature syncs once it holds a shown or funded asset`() {
        val config = bundledConfig()
        val chains = reviewedRegistryChains()

        val selected = selectChainsForBackgroundSync(
            chains = chains,
            chainIdsNeedingSync = setOf(CAGA_ANKARA_CHAIN_ID),
            isTestnet = config::isTestnet
        )

        assertTrue(selected.any { it.id == CAGA_ANKARA_CHAIN_ID })
    }

    private fun chain(id: String, rank: Int? = null, isTestNet: Boolean = false): Chain =
        mock(Chain::class.java).also { chain ->
            `when`(chain.id).thenReturn(id)
            `when`(chain.rank).thenReturn(rank)
            `when`(chain.isTestNet).thenReturn(isTestNet)
            `when`(chain.hasCrowdloans).thenReturn(false)
            `when`(chain.assets).thenReturn(emptyList())
            `when`(chain.identityChain).thenReturn(null)
            `when`(chain.nodes).thenReturn(
                listOf(ChainNode("wss://$id.example", id, false, true))
            )
        }
}
