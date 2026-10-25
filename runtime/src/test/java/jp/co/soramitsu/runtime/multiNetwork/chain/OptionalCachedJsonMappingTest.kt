package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertSame
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/** Optional cache damage changes only that metadata field and cannot suppress healthy peer chains. */
class OptionalCachedJsonMappingTest {
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

    @Test fun healthyPeersReachAllRepositoryPathsWithExactOptionalValues() = runBlocking {
        val rows = listOf(fixture("healthy-a"), fixture("healthy-b"))
        val before = Gson().toJson(rows)
        val (repository, dao) = repository(rows)
        val outputs = listOf(repository.getChains(), repository.getChainsById().values.toList(),
            repository.chainsFlow().first(), repository.chainsByIdFlow().first().values.toList())
        outputs.forEach { chains ->
            assertEquals(rows.map { it.chain.id }, chains.map { it.id })
            chains.forEach {
                assertEquals(listOf("provider-one", "provider-two"), it.assets.single().purchaseProviders)
                assertEquals("public-price", it.assets.single().priceProvider!!.id)
                assertEquals(8, it.assets.single().priceProvider!!.precision)
                assertEquals(listOf("account", "extrinsic"), it.explorers.single().types)
                assertTrue(it.nodes.single().isActive)
                assertEquals("wss://public.invalid/${it.id}", it.nodes.single().url)
            }
        }
        verifyReadsOnly(dao)
        assertEquals(before, Gson().toJson(rows))
    }

    @Test fun absentOptionalFieldsPreserveHealthyChain() {
        val original = fixture("healthy-null")
        val row = original.copy(assets = original.assets.map { it.copy(purchaseProviders = null, priceProvider = null) })
        val mapped = mapChainLocalToChain(row)
        assertEquals(row.chain.id, mapped.id)
        assertEquals(null, mapped.assets.single().purchaseProviders)
        assertEquals(null, mapped.assets.single().priceProvider)
        assertEquals(row.nodes.single().url, mapped.nodes.single().url)
    }

    @Test fun purchaseProvidersObjectPreservesHealthyPeerInBothOrders() = runBlocking {
        recoveryControls("purchaseProviders-object") { it.copy(assets = it.assets.map { asset -> asset.copy(purchaseProviders = "{\"public\":\"value\"}") }) }
    }

    @Test fun malformedPurchaseProvidersPreservesHealthyPeerInBothOrders() = runBlocking {
        recoveryControls("purchaseProviders-malformed") { it.copy(assets = it.assets.map { asset -> asset.copy(purchaseProviders = "[\"unterminated") }) }
    }

    @Test fun largeValidPurchaseProviderListMapsWithoutNativeCursorClaim() {
        val provider = "a".repeat(1_048_574)
        val payload = "[\"$provider\"]"
        assertEquals(1_048_578, payload.toByteArray(Charsets.UTF_8).size)
        val original = fixture("large-valid-list")
        val row = original.copy(assets = original.assets.map { it.copy(purchaseProviders = payload) })
        assertEquals(listOf(provider), mapChainLocalToChain(row).assets.single().purchaseProviders)
    }

    @Test fun historicalStartupObjectPreservesRuntimeHealthyPeer() = runBlocking {
        val payload = "{\"public\":\"" + "a".repeat(1_048_565) + "\"}"
        assertEquals(1_048_578, payload.toByteArray(Charsets.UTF_8).size)
        recoveryControls("purchaseProviders-historical-large-object") { it.copy(assets = it.assets.map { asset -> asset.copy(purchaseProviders = payload) }) }
    }

    @Test fun priceProviderArrayPreservesHealthyPeerInBothOrders() = runBlocking {
        recoveryControls("priceProvider-array") { it.copy(assets = it.assets.map { asset -> asset.copy(priceProvider = "[\"wrong-shape\"]") }) }
    }

    @Test fun explorerTypesObjectPreservesHealthyPeerInBothOrders() = runBlocking {
        recoveryControls("explorer-types-object") { it.copy(explorers = it.explorers.map { explorer -> explorer.copy(types = "{\"wrong\":\"shape\"}") }) }
    }

    @Test fun parseFailureUsesAbsentOptionalMetadata() {
        assertEquals(null, decodeOptionalCachedJson<String> { throw JsonSyntaxException("Public malformed cache") })
    }

    @Test fun cancellationIsNeverConvertedToAbsentMetadata() {
        val failure = CancellationException("Public cancellation control")
        val actual = try { decodeOptionalCachedJson<String> { throw failure }; null } catch (caught: Throwable) { caught }
        assertSame(failure, actual)
    }

    @Test fun fatalErrorIsNeverConvertedToAbsentMetadata() {
        val failure = AssertionError("Public fatal control")
        val actual = try { decodeOptionalCachedJson<String> { throw failure }; null } catch (caught: Throwable) { caught }
        assertSame(failure, actual)
    }

    @Test fun unrelatedFailureIsNeverConvertedToAbsentMetadata() {
        val failure = IllegalStateException("Public unrelated failure control")
        val actual = try { decodeOptionalCachedJson<String> { throw failure }; null } catch (caught: Throwable) { caught }
        assertSame(failure, actual)
    }

    private suspend fun recoveryControls(label: String, corrupt: (JoinedChainInfo) -> JoinedChainInfo) {
        val healthy = fixture("healthy-peer")
        val malformed = corrupt(fixture("malformed-cache"))
        // A separate actual mapping establishes the control peer is healthy before each mixed collection.
        assertEquals(healthy.chain.id, mapChainLocalToChain(healthy).id)
        listOf(listOf(malformed, healthy), listOf(healthy, malformed)).forEachIndexed { order, rows ->
            val before = Gson().toJson(rows)
            val (repository, dao) = repository(rows)
            val expected = rows.map { row ->
                val normalized = when {
                    label.startsWith("purchaseProviders-") && row.chain.id == malformed.chain.id ->
                        row.copy(assets = row.assets.map { it.copy(purchaseProviders = null) })
                    label == "priceProvider-array" && row.chain.id == malformed.chain.id ->
                        row.copy(assets = row.assets.map { it.copy(priceProvider = null) })
                    label == "explorer-types-object" && row.chain.id == malformed.chain.id ->
                        row.copy(explorers = row.explorers.map { it.copy(types = "[]") })
                    else -> row
                }
                mapChainLocalToChain(normalized)
            }
            val outputs = listOf(
                "getChains" to repository.getChains(),
                "getChainsById" to repository.getChainsById().values.toList(),
                "chainsFlow" to repository.chainsFlow().first(),
                "chainsByIdFlow" to repository.chainsByIdFlow().first().values.toList()
            )
            outputs.forEach { (path, actual) ->
                assertEquals("$label / $order / $path", expected, actual)
                assertEquals("Every mapped field remains exact", Gson().toJson(expected), Gson().toJson(actual))
                assertEquals(rows.map { it.chain.id }, actual.map { it.id })
                assertEquals(mapChainLocalToChain(healthy), actual.single { it.id == healthy.chain.id })
                println("RECOVERED_OPTIONAL_CACHE $label order=$order path=$path")
            }
            verifyReadsOnly(dao)
            assertEquals("Input models changed", before, Gson().toJson(rows))
            assertEquals(healthy.chain.id, mapChainLocalToChain(healthy).id)
        }
    }
}
