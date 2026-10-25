package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/** Optional cache damage changes only that metadata field and cannot suppress healthy peer chains. */
class CachedEnumMappingTest {
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

    @Test fun knownStakingValuesRemainExact() {
        val original = fixture("known-staking")
        Asset.StakingType.entries.forEach { value ->
            val row = original.copy(assets = original.assets.map { it.copy(staking = value.name) })
            assertEquals(value, mapChainLocalToChain(row).assets.single().staking)
        }
    }

    @Test fun knownRemoteSourcesRemainExact() {
        val original = fixture("known-remote")
        Chain.RemoteAssetsSource.entries.forEach { value ->
            val row = original.copy(chain = original.chain.copy(remoteAssetsSource = value.name))
            assertEquals(value, mapChainLocalToChain(row).remoteAssetsSource)
        }
        assertEquals(null, mapChainLocalToChain(original).remoteAssetsSource)
    }

    @Test fun knownAndAbsentAssetTypesRemainExact() {
        val original = fixture("known-type")
        ChainAssetType.entries.forEach { value ->
            val row = original.copy(assets = original.assets.map { it.copy(type = value.name) })
            assertEquals(value, mapChainLocalToChain(row).assets.single().type)
        }
        assertEquals(null, mapChainLocalToChain(original).assets.single().type)
        assertEquals(null, mapChainLocalToChain(original).assets.single().currency)
    }

    @Test fun unknownStakingDoesNotSuppressHealthyPeer() = runBlocking {
        for (invalid in listOf("FUTURE_UNRECOGNIZED", "")) {
            val original = fixture("invalid-staking")
            val row = original.copy(assets = original.assets.map { it.copy(staking = invalid) })
            val expected = original.copy(assets = original.assets.map { it.copy(staking = "UNSUPPORTED") })
            assertRecovered(row, expected, "staking")
        }
    }

    @Test fun unknownRemoteSourceDoesNotSuppressHealthyPeer() = runBlocking {
        for (invalid in listOf("FUTURE_UNRECOGNIZED", "")) {
            val original = fixture("invalid-remote")
            val row = original.copy(chain = original.chain.copy(remoteAssetsSource = invalid))
            assertRecovered(row, original, "remote")
        }
    }

    @Test fun unknownAssetTypeNeverBecomesNativeCurrency() = runBlocking {
        for (invalid in listOf("FUTURE_UNRECOGNIZED", "")) {
            val original = fixture("invalid-type")
            val row = original.copy(assets = original.assets.map { it.copy(type = invalid, currencyId = "123") })
            val expected = row.copy(assets = row.assets.map { it.copy(type = "Unknown") })
            assertRecovered(row, expected, "asset-type")
            val asset = mapChainLocalToChain(row).assets.single()
            assertEquals("123", asset.currencyId)
            val failure = try { asset.currency; null } catch (caught: IllegalStateException) { caught }
            assertTrue("Unsupported asset must not become the native currency", failure != null)
        }
    }

    private suspend fun assertRecovered(malformed: JoinedChainInfo, normalized: JoinedChainInfo, label: String) {
        val healthy = fixture("healthy-peer")
        for (rows in listOf(listOf(malformed, healthy), listOf(healthy, malformed))) {
            val before = Gson().toJson(rows)
            val expected = rows.map { mapChainLocalToChain(if (it.chain.id == malformed.chain.id) normalized else it) }
            val (repository, dao) = repository(rows)
            val outputs = listOf(repository.getChains(), repository.getChainsById().values.toList(),
                repository.chainsFlow().first(), repository.chainsByIdFlow().first().values.toList())
            for (actual in outputs) {
                assertEquals(expected, actual)
                assertEquals(Gson().toJson(expected), Gson().toJson(actual))
                assertEquals(mapChainLocalToChain(healthy), actual.single { it.id == healthy.chain.id })
                println("RECOVERED_ENUM $label")
            }
            verifyReadsOnly(dao)
            assertEquals(before, Gson().toJson(rows))
        }
    }
}
