package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import com.google.gson.JsonObject
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfiguratorProvider
import jp.co.soramitsu.runtime.multiNetwork.configurator.NativeWalletEnvironmentConfigurator
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class UnknownEcosystemAdmissionTest {
    private val unknowns = listOf("FUTURE_UNSUPPORTED", "", " Substrate ", "Bitcoin", "Solana", "Iroha")

    private fun fixture(id: String, ecosystem: String = "Substrate") = JoinedChainInfo(
        ChainLocal(
            id, null, null, 1, id, null, "public-icon", null, 42, false, false, false,
            false, false, false, false, false, null, ecosystem, null, null, null, null
        ),
        listOf(ChainNodeLocal(id, "wss://public.invalid/$id", "Public endpoint", true, false)),
        emptyList(),
        emptyList()
    )

    private fun remote(value: String?): ChainRemote {
        val json = JsonObject().apply {
            addProperty("chainId", "public-chain")
            addProperty("name", "Public chain")
            addProperty("addressPrefix", 42)
            if (value != null) addProperty("ecosystem", value)
        }
        return Gson().fromJson(json, ChainRemote::class.java)
    }

    @Test
    fun knownCaseInsensitiveAndAbsentRemoteContractRemainExact() {
        for (known in Ecosystem.entries) {
            for (spelling in listOf(known.name, known.name.lowercase())) {
                assertEquals(known, remote(spelling).toChain().ecosystem)
                assertEquals(known, mapChainLocalToChain(fixture("known", spelling)).ecosystem)
            }
        }
        assertEquals(Ecosystem.Substrate, remote(null).toChain().ecosystem)
        assertEquals(4, Ecosystem.entries.size)
    }

    @Test
    fun explicitUnknownRemoteCannotAcquireOperationalSemantics() {
        for (value in unknowns) {
            val row = remote(value)
            val before = Gson().toJson(row)
            assertThrows(IllegalArgumentException::class.java) { row.toChain() }
            assertEquals(before, Gson().toJson(row))
        }
    }

    @Test
    fun explicitUnknownCachedParentCannotAcquireOperationalSemantics() {
        for (value in unknowns) {
            val row = fixture("unsupported", value)
            val before = Gson().toJson(row)
            assertThrows(IllegalArgumentException::class.java) { mapChainLocalToChain(row) }
            assertEquals(before, Gson().toJson(row))
        }
    }

    @Test
    fun fourRepositoryPathsPreserveExactHealthyPeerAndRawInputsInBothOrders() = runBlocking {
        val healthy = fixture("healthy")
        val expected = mapChainLocalToChain(healthy)
        for (value in unknowns) {
            for (reverse in listOf(false, true)) {
                val damaged = fixture("unsupported", value)
                val rows = if (reverse) listOf(healthy, damaged) else listOf(damaged, healthy)
                val before = Gson().toJson(rows)
                val dao = Mockito.mock(ChainDao::class.java)
                Mockito.`when`(dao.getJoinChainInfo()).thenReturn(rows)
                Mockito.`when`(dao.joinChainInfoFlow()).thenReturn(flowOf(rows))
                val repository = ChainsRepository(dao)
                val outputs = listOf(
                    repository.getChains(),
                    repository.getChainsById().values.toList(),
                    repository.chainsFlow().first(),
                    repository.chainsByIdFlow().first().values.toList()
                )
                for (actual in outputs) {
                    assertEquals(listOf(expected), actual)
                    assertEquals(Gson().toJson(listOf(expected)), Gson().toJson(actual))
                }
                Mockito.verify(dao, Mockito.times(2)).getJoinChainInfo()
                Mockito.verify(dao, Mockito.times(2)).joinChainInfoFlow()
                Mockito.verifyNoMoreInteractions(dao)
                assertEquals(before, Gson().toJson(rows))
            }
        }
    }

    @Test
    fun unsupportedOnlyCatalogEmitsEmptyRatherThanFailingCollection() = runBlocking {
        val dao = Mockito.mock(ChainDao::class.java)
        Mockito.`when`(dao.joinChainInfoFlow()).thenReturn(flowOf(listOf(fixture("unsupported", "future"))))
        assertTrue(ChainsRepository(dao).chainsFlow().first().isEmpty())
    }

    @Test
    fun appOwnedNativeIdsRetainTheirSeparateConfigurator() {
        val factory = ChainEnvironmentConfiguratorProvider(
            Mockito.mock(ConnectionPool::class.java),
            Mockito.mock(RuntimeProviderPool::class.java),
            Mockito.mock(RuntimeSyncService::class.java),
            Mockito.mock(RuntimeSubscriptionPool::class.java),
            Mockito.mock(EthereumConnectionPool::class.java)
        )
        for (ids in listOf(emptySet(), setOf("bitcoin-mainnet", "bitcoin:mainnet", "solana-mainnet", "solana:mainnet"))) {
            for (chain in nativeWalletChains(ids)) {
                val restored = mapChainLocalToChain(mapChainToChainLocal(chain))
                assertEquals(Ecosystem.Substrate, restored.ecosystem)
                assertEquals(chain.id, restored.id)
                assertTrue(factory.provide(restored) is NativeWalletEnvironmentConfigurator)
            }
        }
    }
}
