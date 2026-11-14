package jp.co.soramitsu.runtime.assethub

import com.google.gson.Gson
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainToChainLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.westendChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainAssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssetHubStakingTest {
    // The 4.2 catalogue: relay staking, pools and crowdloans on, nothing on the Hubs.
    private fun legacyCatalogue(): List<ChainRemote> =
        javaClass.getResourceAsStream("/asset-hub-catalogue.json")!!.reader().use {
            Gson().fromJson(it, Array<ChainRemote>::class.java).toList()
        }

    private fun production(): List<ChainRemote> =
        Gson().fromJson(File(PRODUCTION).readText(), Array<ChainRemote>::class.java).toList()

    private fun ChainRemote.withNativeStaking(value: String?): ChainRemote = copy(
        assets = assets!!.map { asset -> if (asset.isUtility == true) asset.withStaking(value) else asset }
    )

    private fun ChainAssetRemote.withStaking(value: String?) = ChainAssetRemote(
        id, name, symbol, precision, icon, priceId, currencyId, existentialDeposit, color, isUtility, isNative,
        value, purchaseProviders, type, ethereumType, tonType, priceProvider, coinbaseUrl
    )

    private fun enabled(catalogue: List<ChainRemote>) = catalogue.map {
        if (AssetHubStaking.isHub(it.chainId)) it.withNativeStaking(AssetHubStaking.CATALOGUE_STAKING) else it
    }

    private fun assertStakingOnlyOnHubNatives(chains: List<Chain>, hubs: Set<String>) {
        chains.forEach { chain ->
            val hub = chain.id in hubs
            assertEquals(chain.name, hub, chain.supportStakingPool)
            chain.assets.forEach { asset ->
                val native = hub && asset.isUtility
                val expected = if (native) Asset.StakingType.RELAYCHAIN else Asset.StakingType.UNSUPPORTED
                assertEquals("${chain.name} ${asset.symbol}", expected, asset.staking)
                assertEquals("${chain.name} ${asset.symbol}", native, asset.supportStakingPool)
            }
            val persisted = mapChainLocalToChain(mapChainToChainLocal(chain))
            assertEquals(chain.supportStakingPool, persisted.supportStakingPool)
            assertEquals(chain.assets.map { it.staking }, persisted.assets.map { it.staking })
            assertEquals(chain.assets.map { it.supportStakingPool }, persisted.assets.map { it.supportStakingPool })
            assertEquals(chain.identityChain, persisted.identityChain)
        }
    }

    @Test fun `all migrated relay genesis identities route to their own hub`() {
        listOf(polkadotChainId to AssetHubStaking.POLKADOT, kusamaChainId to AssetHubStaking.KUSAMA, westendChainId to AssetHubStaking.WESTEND).forEach { (relay, hub) ->
            assertEquals(hub, AssetHubStaking.destination(relay))
            assertEquals(hub, AssetHubStaking.destination(hub))
            assertEquals(relay, AssetHubStaking.relay(hub))
            assertTrue(AssetHubStaking.isHub(hub))
            assertTrue(AssetHubStaking.isMigratedRelay(relay))
        }
    }

    @Test fun `unrelated chains cannot impersonate a staking route by name`() {
        assertEquals("Polkadot AssetHub", AssetHubStaking.destination("Polkadot AssetHub"))
        assertNull(AssetHubStaking.relay("unknown"))
        assertFalse(AssetHubStaking.isHub("unknown"))
        assertTrue(AssetHubStaking.poolPolicy("unknown", true, emptyList()))
        assertFalse(AssetHubStaking.poolPolicy("unknown", false, emptyList()))
        assertFalse(AssetHubStaking.poolPolicy(polkadotChainId, true, emptyList()))
    }

    @Test fun `staking Hubs use People for identities only while staking is enabled`() {
        assertEquals("67fa177a097bfa18f77ea95ab56e9bcdfeb0e5b8a40e46298bb93e16b6fc5008", AssetHubStaking.identityChain(AssetHubStaking.POLKADOT, null, true))
        assertEquals("c1af4cb4eb3918e5db15086c0cc5ec17fb334f728b7c65dd44bfe1e174ff8b3f", AssetHubStaking.identityChain(AssetHubStaking.KUSAMA, "old", true))
        assertNull(AssetHubStaking.identityChain(AssetHubStaking.POLKADOT, null, false))
        assertEquals("old", AssetHubStaking.identityChain(AssetHubStaking.KUSAMA, "old", false))
        assertNull(AssetHubStaking.identityChain(AssetHubStaking.WESTEND, null, true))
        assertEquals("custom", AssetHubStaking.identityChain("other", "custom", true))
    }

    @Test fun `relay staking pools and Hub staking stay off without the catalogue switch`() {
        assertStakingOnlyOnHubNatives(legacyCatalogue().map { it.toChain() }, emptySet())
    }

    @Test fun `catalogue switch enables only the Hub native assets`() {
        val chains = enabled(legacyCatalogue()).map { it.toChain() }
        assertStakingOnlyOnHubNatives(chains, setOf(AssetHubStaking.POLKADOT, AssetHubStaking.KUSAMA))
        assertEquals(
            "67fa177a097bfa18f77ea95ab56e9bcdfeb0e5b8a40e46298bb93e16b6fc5008",
            chains.single { it.id == AssetHubStaking.POLKADOT }.identityChain
        )
    }

    @Test fun `values older builds act on never enable Hub staking`() {
        listOf("relaychain", "parachain", "AssetHub", "").forEach { value ->
            val chains = legacyCatalogue().map { if (AssetHubStaking.isHub(it.chainId)) it.withNativeStaking(value) else it }.map { it.toChain() }
            assertStakingOnlyOnHubNatives(chains, emptySet())
        }
    }

    @Test fun `a Hub whose parent is not its relay gains no staking authority`() {
        val chains = enabled(legacyCatalogue()).map {
            if (it.chainId == AssetHubStaking.POLKADOT) it.copy(parentId = kusamaChainId) else it
        }.map { it.toChain() }
        assertStakingOnlyOnHubNatives(chains, setOf(AssetHubStaking.KUSAMA))
    }

    @Test fun `foreign asset carrying native symbol does not gain staking authority`() {
        val hub = enabled(legacyCatalogue()).single { it.chainId == AssetHubStaking.POLKADOT }.toChain()
        val native = hub.assets.first { it.isUtility }
        val enabledValue = AssetHubStaking.CATALOGUE_STAKING
        assertEquals(Asset.StakingType.RELAYCHAIN, AssetHubStaking.remoteAssetPolicy(native, enabledValue, polkadotChainId).staking)
        assertEquals(Asset.StakingType.UNSUPPORTED, AssetHubStaking.remoteAssetPolicy(native.copy(id = "foreign", isUtility = false), enabledValue, polkadotChainId).staking)
        assertEquals(Asset.StakingType.UNSUPPORTED, AssetHubStaking.remoteAssetPolicy(native.copy(currencyId = "1984"), enabledValue, polkadotChainId).staking)
        assertEquals(native.copy(chainId = "other"), AssetHubStaking.remoteAssetPolicy(native.copy(chainId = "other"), enabledValue, polkadotChainId))
    }

    @Test fun `production catalogue enables Polkadot and Kusama Hub staking in a form 4_3_0 ignores`() {
        val catalogue = production()
        val hubs = setOf(AssetHubStaking.POLKADOT, AssetHubStaking.KUSAMA)
        assertStakingOnlyOnHubNatives(catalogue.filter { !it.disabled }.mapNotNull { runCatching { it.toChain() }.getOrNull() }
            .filter { AssetHubStaking.isHub(it.id) || AssetHubStaking.isMigratedRelay(it.id) }, hubs)
        catalogue.filter { AssetHubStaking.isHub(it.chainId) || AssetHubStaking.isMigratedRelay(it.chainId) }.forEach { chain ->
            // 4.3.0 offers pools for every asset of a chain with this option, and acts on these staking values.
            assertFalse(chain.name, "poolStaking" in chain.options.orEmpty())
            assertFalse(chain.name, "crowdloans" in chain.options.orEmpty())
            chain.assets.orEmpty().forEach { asset ->
                assertFalse("${chain.name} ${asset.symbol}", asset.staking in setOf("relaychain", "parachain"))
                val switched = asset.staking == AssetHubStaking.CATALOGUE_STAKING
                assertEquals("${chain.name} ${asset.symbol}", chain.chainId in hubs && asset.isUtility == true, switched)
            }
        }
        assertTrue(catalogue.flatMap { it.assets.orEmpty() }.count { it.staking == AssetHubStaking.CATALOGUE_STAKING } == 2)
    }

    private companion object {
        const val PRODUCTION = "../remote-config/production/chains/v13/chains.json"
    }
}
