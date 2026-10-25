package jp.co.soramitsu.coredb.dao

import java.math.BigInteger
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.FundedCatalogAssetRow
import jp.co.soramitsu.coredb.model.chain.JoinedChainCatalogMetadata
import jp.co.soramitsu.coredb.model.chain.RuntimeChainCatalogRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings

/** Room wake-ups stay idle while the real mutation lane completes an independently committed write. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChainDaoRuntimeFlowTest {
    @Test
    fun nodesObserveCommittedLaneChangeWithoutRoomInvalidation() = runTest {
        val fixture = Fixture()
        val events = mutableListOf<List<ChainNodeLocal>>()
        backgroundScope.launch { fixture.dao.nodesFlow("a").collect { events += it } }
        runCurrent()
        assertEquals("Before", events.single().single().name)
        fixture.commitName("After")
        runCurrent()
        assertEquals("After", events.last().single().name)
    }

    @Test
    fun joinedCatalogObservesCommittedLaneChangeWithoutRoomInvalidation() = runTest {
        val fixture = Fixture()
        val names = mutableListOf<String>()
        backgroundScope.launch {
            fixture.dao.joinChainInfoFlow().collect { names += it.single().nodes.single().name }
        }
        runCurrent()
        assertEquals(listOf("Before"), names)
        fixture.commitName("After")
        runCurrent()
        assertEquals("After", names.last())
    }

    @Test
    fun equalCountRoomWakeupStillReadsFreshPayload() = runTest {
        val fixture = Fixture()
        val names = mutableListOf<String>()
        backgroundScope.launch {
            fixture.dao.nodesFlow("a").collect { names += it.single().name }
        }
        runCurrent()
        assertEquals(listOf("Before"), names)
        fixture.nodes = fixture.nodes.map { it.copy(name = "Room update") }
        fixture.roomChanges.emit(1L)
        runCurrent()
        assertEquals("Room update", names.last())
    }

    @Test
    fun fundedNetworksHydrateBoundedNodesAfterLaneCommit() = runTest {
        val fixture = Fixture()
        val names = mutableListOf<String>()
        backgroundScope.launch {
            fixture.dao.observeChainsWithBalance(7L, "0").collect { result ->
                assertEquals("a", result.values.single().asset.chainId)
                names += result.keys.single().nodes.single().name
            }
        }
        runCurrent()
        assertEquals(listOf("Before"), names)
        fixture.commitName("After")
        runCurrent()
        assertEquals("After", names.last())
    }

    @Test
    fun equalCountCatalogWakeupReadsUpdatedParent() = runTest {
        val fixture = Fixture()
        val names = mutableListOf<String>()
        backgroundScope.launch {
            fixture.dao.joinChainInfoFlow().collect { names += it.single().chain.name }
        }
        runCurrent()
        assertEquals(listOf("a"), names)
        fixture.catalog = fixture.catalog.copy(chain = fixture.catalog.chain.copy(name = "Updated parent"))
        fixture.catalogChanges.emit(1L)
        runCurrent()
        assertEquals(listOf("a", "Updated parent"), names)
    }

    @Test
    fun equalCountBalanceWakeupReadsUpdatedFundedBalance() = runTest {
        val fixture = Fixture()
        val balances = mutableListOf<BigInteger?>()
        backgroundScope.launch {
            fixture.dao.observeChainsWithBalance(7L, "0").collect { balances += it.values.single().asset.freeInPlanks }
        }
        runCurrent()
        assertEquals(listOf<BigInteger?>(null), balances)
        fixture.balance = AssetWithToken(fixture.balance.asset.copy(freeInPlanks = BigInteger.TEN), null)
        fixture.balanceChanges.emit(1L)
        runCurrent()
        assertEquals(listOf(null, BigInteger.TEN), balances)
    }

    @Test
    fun equalCountCatalogWakeupRehydratesFundedParent() = runTest {
        val fixture = Fixture()
        val names = mutableListOf<String>()
        backgroundScope.launch {
            fixture.dao.observeChainsWithBalance(7L, "0").collect { names += it.keys.single().chain.name }
        }
        runCurrent()
        assertEquals(listOf("a"), names)
        fixture.catalog = fixture.catalog.copy(chain = fixture.catalog.chain.copy(name = "Funded parent"))
        fixture.catalogChanges.emit(1L)
        runCurrent()
        assertEquals(listOf("a", "Funded parent"), names)
    }

    private class Fixture {
        var nodes = listOf(ChainNodeLocal("a", "wss://a.invalid/", "Before", true, true))
        val roomChanges = MutableSharedFlow<Long>(replay = 1).apply { check(tryEmit(1L)) }
        val catalogChanges = MutableSharedFlow<Long>(replay = 1).apply { check(tryEmit(1L)) }
        val balanceChanges = MutableSharedFlow<Long>(replay = 1).apply { check(tryEmit(1L)) }
        var balance = AssetWithToken(AssetLocal.createEmpty(byteArrayOf(1), "0", "a", 7L, null, true), null)
        var catalog = JoinedChainCatalogMetadata(
            ChainLocal(
                "a", null, null, null, "a", null, "", null, 42, false, false, false,
                false, false, false, false, false, null, "Substrate", null, null, null, null
            ), emptyList(), emptyList()
        )
        val dao: ChainDao = mock(
            ChainDao::class.java,
            withSettings().useConstructor().defaultAnswer { call ->
                when (call.method.name) {
                    "nodesFlow", "joinChainInfoFlow", "getEndpointMutationLane", "getJoinChainInfo",
                    "observeChainsWithBalance", "readFundedRuntimeCatalog" -> call.callRealMethod()
                    "runtimeNodeInvalidationFlow" -> roomChanges
                    "catalogInvalidationFlow" -> catalogChanges
                    "walletBalanceInvalidationFlow" -> balanceChanges
                    "observeAssetSymbolById" -> flowOf("TEST")
                    "fundedCatalogAssets" -> listOf(FundedCatalogAssetRow("a", balance))
                    "runtimeChainPage" -> if (call.getArgument<Long?>(1) == null) {
                        listOf(RuntimeChainCatalogRow(catalog.chain, 1L, null, null))
                    } else emptyList<RuntimeChainCatalogRow>()
                    "runtimeAssetPage", "runtimeExplorerPage" -> emptyList<Any>()
                    "readRuntimeNodes" -> nodes
                    else -> error("Unexpected DAO call: ${call.method.name}")
                }
            }
        )

        suspend fun commitName(name: String) {
            val lane = dao.endpointMutationLane
            val operation = lane.admit(listOf("a"))
            lane.withOperation(operation) {
                lane.grantFinal(operation) {}
                nodes = nodes.map { it.copy(name = name) }
            }
        }
    }
}
