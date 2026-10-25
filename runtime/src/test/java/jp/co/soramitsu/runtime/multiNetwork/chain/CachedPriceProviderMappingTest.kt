package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import jp.co.soramitsu.common.utils.diffed
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

/** Optional cache damage changes only that metadata field and cannot suppress healthy peer chains. */
class CachedPriceProviderMappingTest {
    private fun fixture(id: String): JoinedChainInfo = JoinedChainInfo(
        chain = ChainLocal(
            id = id, paraId = null, parentId = null, rank = 1, name = "Public fixture $id",
            minSupportedVersion = null, icon = "https://public.invalid/icon", externalApi = null,
            prefix = 42, isEthereumBased = false, isTestNet = false, hasCrowdloans = false,
            supportStakingPool = false, isEthereumChain = false, isChainlinkProvider = false,
            supportNft = false, isUsesAppId = false, identityChain = null, ecosystem = "Substrate",
            androidMinAppVersion = null, remoteAssetsSource = null, tonBridgeUrl = null, xcm = null
        ),
        nodes = listOf(ChainNodeLocal(chainId = id, name = "Healthy public endpoint", url = "wss://public.invalid/$id", isActive = true, isDefault = false)),
        assets = listOf(ChainAssetLocal(
            id = "0", name = "Public token", symbol = "PUB", chainId = id, icon = "public-icon",
            priceId = null, staking = "UNSUPPORTED", precision = 12,
            purchaseProviders = "[\"provider-one\",\"provider-two\"]", isUtility = true,
            type = null, currencyId = null, existentialDeposit = "1", color = null,
            isNative = true, priceProvider = "{\"id\":\"public-price\",\"type\":\"Chainlink\",\"precision\":8}",
            coinbaseUrl = null
        )),
        explorers = listOf(ChainExplorerLocal(id, "SUBSCAN", "[\"account\",\"extrinsic\"]", "https://public.invalid/explorer"))
    )

    private suspend fun repository(rows: List<JoinedChainInfo>): Pair<ChainsRepository, ChainDao> {
        val dao = Mockito.mock(ChainDao::class.java)
        Mockito.`when`(dao.getJoinChainInfo()).thenReturn(rows)
        Mockito.`when`(dao.joinChainInfoFlow()).thenReturn(flowOf(rows))
        return ChainsRepository(dao) to dao
    }

    private suspend fun verifyReadsOnly(dao: ChainDao) {
        Mockito.verify(dao, Mockito.times(2)).getJoinChainInfo()
        Mockito.verify(dao, Mockito.times(2)).joinChainInfoFlow()
        Mockito.verifyNoMoreInteractions(dao)
    }

    @Test fun validProviderSurvivesActualCollectionDiffing() = runBlocking {
        val row = fixture("valid-price")
        val chain = mapChainLocalToChain(row)
        assertEquals(Asset.PriceProvider("public-price", Asset.PriceProviderType.Chainlink, 8), chain.assets.single().priceProvider)
        val diffs = flowOf<Collection<Chain>>(listOf(chain), listOf(chain)).diffed().toList()
        assertEquals(listOf(chain), diffs[0].addedOrModified)
        assertEquals(emptyList<Chain>(), diffs[1].addedOrModified)
        assertEquals(listOf(chain), diffs[1].all)
    }

    @Test fun explicitUnknownProviderAndAbsentPrecisionRemainExact() = runBlocking {
        val row = fixture("known-unknown")
        val provider = mapToPriceProvider("{\"id\":\"public-price\",\"type\":\"Unknown\"}")
        assertEquals(Asset.PriceProvider("public-price", Asset.PriceProviderType.Unknown, 0), provider)
        assertEquals(null, mapToPriceProvider(null))
        assertEquals(null, mapToPriceProvider("null"))
        val chain = mapChainLocalToChain(row.copy(assets = row.assets.map { it.copy(priceProvider = "{\"id\":\"public-price\",\"type\":\"Unknown\"}") }))
        val diffs = flowOf<Collection<Chain>>(listOf(chain), listOf(chain)).diffed().toList()
        assertEquals(emptyList<Chain>(), diffs[1].addedOrModified)
    }

    @Test fun missingOrNullIdCannotPoisonRegistryDiff() = runBlocking {
        for (json in listOf("{\"type\":\"Chainlink\",\"precision\":8}", "{\"id\":null,\"type\":\"Chainlink\",\"precision\":8}")) assertRecovered(json)
    }

    @Test fun missingOrNullTypeCannotPoisonRegistryDiff() = runBlocking {
        for (json in listOf("{\"id\":\"public-price\",\"precision\":8}", "{\"id\":\"public-price\",\"type\":null,\"precision\":8}")) assertRecovered(json)
    }

    @Test fun unrecognizedProviderTypeCannotPoisonRegistryDiff() = runBlocking {
        assertRecovered("{\"id\":\"public-price\",\"type\":\"FutureProvider\",\"precision\":8}")
    }

    @Test fun emptyProviderObjectCannotPoisonRegistryDiff() = runBlocking {
        assertRecovered("{}")
    }

    private suspend fun assertRecovered(json: String) {
        val original = fixture("damaged-price")
        val malformed = original.copy(assets = original.assets.map { it.copy(priceProvider = json) })
        val normalized = original.copy(assets = original.assets.map { it.copy(priceProvider = null) })
        val healthy = fixture("healthy-peer")
        for (rows in listOf(listOf(malformed, healthy), listOf(healthy, malformed))) {
            val before = Gson().toJson(rows)
            val expected = rows.map { mapChainLocalToChain(if (it.chain.id == malformed.chain.id) normalized else it) }
            val (repository, dao) = repository(rows)
            val outputs = listOf(repository.getChains(), repository.getChainsById().values.toList(),
                repository.chainsFlow().first(), repository.chainsByIdFlow().first().values.toList())
            for (actual in outputs) {
                // Exercise the actual production diff function used by ChainRegistry before inspecting recovery.
                val diffs = flowOf<Collection<Chain>>(actual, actual).diffed().toList()
                assertEquals(emptyList<Chain>(), diffs[1].addedOrModified)
                assertEquals(actual, diffs[1].all)
                assertEquals(expected, actual)
                assertEquals(Gson().toJson(expected), Gson().toJson(actual))
                assertEquals(mapChainLocalToChain(healthy), actual.single { it.id == healthy.chain.id })
                println("RECOVERED_PRICE_SHAPE")
            }
            verifyReadsOnly(dao)
            assertEquals(before, Gson().toJson(rows))
        }
    }
}
