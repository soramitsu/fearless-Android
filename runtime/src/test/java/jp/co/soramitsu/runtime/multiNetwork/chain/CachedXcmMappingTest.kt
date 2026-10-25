package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.concurrent.CancellationException
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
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito

/** A damaged optional XCM value cannot hide peers or mask cancellation and runtime failures. */
class CachedXcmMappingTest {
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

    @Test fun absentXcmRemainsAbsent() {
        assertEquals(null, mapChainLocalToChain(fixture("absent")).xcm)
    }

    @Test fun validXcmIsPreservedAcrossRepositoryReads() = runBlocking {
        checkRepositories("{\"chainId\":\"public-origin\",\"xcmVersion\":\"3\",\"availableAssets\":[],\"availableDestinations\":[]}",
            Chain.Xcm("public-origin", "3", emptyList(), emptyList()))
    }

    @Test fun malformedXcmDoesNotSuppressHealthyPeers() = runBlocking {
        for (json in listOf("{", "[]", "{\"availableAssets\":true}")) checkRepositories(json, null)
    }

    @Test fun gsonParseFailureOmitsOnlyXcm() {
        withInjectedFailure(JsonSyntaxException("Public malformed metadata")) { row ->
            assertEquals(null, mapChainLocalToChain(row).xcm)
        }
    }

    @Test fun cancellationFromXcmParsingPropagates() = checkFailure(CancellationException("Public cancellation control"))
    @Test fun fatalAllocationErrorFromXcmParsingPropagates() = checkFailure(OutOfMemoryError("Public fatal control"))
    @Test fun assertionErrorFromXcmParsingPropagates() = checkFailure(AssertionError("Public assertion control"))
    @Test fun unrelatedFailureFromXcmParsingPropagates() = checkFailure(IllegalStateException("Public unrelated control"))

    private fun checkFailure(failure: Throwable) {
        withInjectedFailure(failure) { row ->
            val actual = try { mapChainLocalToChain(row); null } catch (caught: Throwable) { caught }
            assertSame(failure, actual)
        }
    }

    private fun withInjectedFailure(failure: Throwable, action: (JoinedChainInfo) -> Unit) {
        val base = fixture("failure")
        // Keep other optional decoders absent: the injected call is exactly the cached XCM parse.
        val row = base.copy(chain = base.chain.copy(xcm = "{}"), assets = emptyList(), explorers = emptyList())
        Mockito.mockConstruction(Gson::class.java) { mock, _ ->
            Mockito.doThrow(failure).`when`(mock).fromJson(Mockito.anyString(), Mockito.eq(Chain.Xcm::class.java))
        }.use { construction ->
            action(row)
            assertEquals(1, construction.constructed().size)
            Mockito.verify(construction.constructed().single()).fromJson("{}", Chain.Xcm::class.java)
        }
    }

    private suspend fun checkRepositories(json: String, expectedXcm: Chain.Xcm?) {
        val original = fixture("optional-xcm")
        val affected = original.copy(chain = original.chain.copy(xcm = json))
        val healthy = fixture("healthy-peer")
        for (rows in listOf(listOf(affected, healthy), listOf(healthy, affected))) {
            val before = Gson().toJson(rows)
            val dao = Mockito.mock(ChainDao::class.java)
            Mockito.`when`(dao.getJoinChainInfo()).thenReturn(rows)
            Mockito.`when`(dao.joinChainInfoFlow()).thenReturn(flowOf(rows))
            val repository = ChainsRepository(dao)
            val outputs = listOf(repository.getChains(), repository.getChainsById().values.toList(),
                repository.chainsFlow().first(), repository.chainsByIdFlow().first().values.toList())
            val expected = rows.map { row -> if (row.chain.id == affected.chain.id) {
                mapChainLocalToChain(original).copy(xcm = expectedXcm)
            } else mapChainLocalToChain(healthy) }
            for (actual in outputs) {
                assertEquals(expected, actual)
                assertEquals(Gson().toJson(expected), Gson().toJson(actual))
            }
            Mockito.verify(dao, Mockito.times(2)).getJoinChainInfo()
            Mockito.verify(dao, Mockito.times(2)).joinChainInfoFlow()
            Mockito.verifyNoMoreInteractions(dao)
            assertEquals(before, Gson().toJson(rows))
        }
    }
}
