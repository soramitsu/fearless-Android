package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.recovery.selection.EndpointCatalogSnapshot
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class UnsupportedEcosystemCatalogTest {
    private fun local(id: String, ecosystem: String = "Substrate") = JoinedChainInfo(
        ChainLocal(
            id, null, null, 1, "Stored $id", null, "stored-icon", null, 42, false, false, false,
            false, false, false, false, false, null, ecosystem, null, null, null, "stored-optional"
        ),
        emptyList(),
        emptyList(),
        emptyList()
    )

    private fun remote(id: String, ecosystem: String = "Substrate") = ChainRemote(
        id, null, 1, "Fetched $id", null, emptyList(), emptyList(), null, "fetched-icon", 42,
        emptyList(), null, ecosystem = ecosystem
    )

    private class Fixture(var current: List<JoinedChainInfo>) {
        val dao = Mockito.mock(ChainDao::class.java)
        val fetcher = Mockito.mock(ChainFetcher::class.java)
        val accounts = Mockito.mock(MetaAccountDao::class.java)
        val assets = Mockito.mock(AssetDao::class.java)
        val snapshots = mutableListOf<EndpointCatalogSnapshot>()
        val service = ChainSyncService(dao, fetcher, accounts, assets, Mockito.mock(ContextManager::class.java))

        suspend fun prepare(fetched: List<ChainRemote>) {
            whenever(fetcher.getChains()).thenReturn(fetched)
            whenever(accounts.getJoinedMetaAccountsInfo()).thenReturn(emptyList())
            whenever(dao.endpointMutationLane).thenReturn(EndpointMutationLane())
            whenever(dao.replaceCatalog(any(), any())).thenAnswer { invocation ->
                val plan = invocation.getArgument<(List<JoinedChainInfo>) -> EndpointCatalogSnapshot>(1)
                snapshots += plan(current)
                emptyList<jp.co.soramitsu.coredb.model.chain.ChainAssetLocal>()
            }
        }
    }

    @Test
    fun existingUnknownQuarantinesExactRawSpellingWhileHealthyPeerRefreshesInBothOrders() = runBlocking {
        for (raw in listOf("FUTURE_UNSUPPORTED", "", "u".repeat(3 * 1024 * 1024))) {
            for (reverse in listOf(false, true)) {
                val stored = local("unsupported")
                val healthy = local("healthy")
                val fixture = Fixture(if (reverse) listOf(healthy, stored) else listOf(stored, healthy))
                val incoming = listOf(remote("unsupported", raw), remote("healthy"))
                fixture.prepare(if (reverse) incoming.reversed() else incoming)
                fixture.service.syncUp()
                val snapshot = fixture.snapshots.single()
                assertEquals(setOf("unsupported"), snapshot.preservedCatalogChains)
                assertEquals(mapOf("unsupported" to raw), snapshot.unsupportedEcosystemOverrides)
                assertEquals(Gson().toJson(stored), Gson().toJson(snapshot.chains.single { it.chain.id == "unsupported" }))
                assertEquals("Fetched healthy", snapshot.chains.single { it.chain.id == "healthy" }.chain.name)
                assertEquals(listOf("healthy"), fixture.service.getCurrentProcessXcmDiscoveryChains().map { it.id })
            }
        }
    }

    @Test
    fun newUnsupportedIdIsNotInsertedAndAppOwnedNativeDefinitionsRemainSupported() = runBlocking {
        val fixture = Fixture(emptyList())
        fixture.prepare(listOf(remote("new", "future"), remote("bitcoin-mainnet", "future"), remote("solana:mainnet", "future")))
        fixture.service.syncUp()
        val snapshot = fixture.snapshots.single()
        assertTrue(snapshot.chains.none { it.chain.id == "new" })
        assertTrue(snapshot.unsupportedEcosystemOverrides.isEmpty())
        assertTrue(snapshot.chains.any { it.chain.id == "bitcoin-mainnet" && it.chain.ecosystem == "Substrate" })
        assertTrue(snapshot.chains.any { it.chain.id == "solana:mainnet" && it.chain.ecosystem == "Substrate" })
    }

    @Test
    fun laterSupportedFetchRequestsCompleteRestoration() = runBlocking {
        val fixture = Fixture(listOf(local("restored", "FUTURE_UNSUPPORTED")))
        fixture.prepare(listOf(remote("restored", "substrate")))
        fixture.service.syncUp()
        val snapshot = fixture.snapshots.single()
        assertTrue(snapshot.preservedCatalogChains.isEmpty())
        assertTrue(snapshot.unsupportedEcosystemOverrides.isEmpty())
        assertEquals("Substrate", snapshot.chains.single { it.chain.id == "restored" }.chain.ecosystem)
        assertEquals("Fetched restored", snapshot.chains.single { it.chain.id == "restored" }.chain.name)
    }

    @Test
    fun duplicateRemoteIdsRejectBeforePartitionWithoutChoosingAnEcosystemWinner() = runBlocking {
        for (reverse in listOf(false, true)) {
            val original = local("duplicate")
            val fixture = Fixture(listOf(original))
            val rows = listOf(remote("duplicate"), remote("duplicate", "future"))
            fixture.prepare(if (reverse) rows.reversed() else rows)
            val failure = runCatching { fixture.service.syncUp() }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            val snapshot = fixture.snapshots.single()
            assertEquals(setOf("duplicate"), snapshot.preservedCatalogChains)
            assertTrue(snapshot.unsupportedEcosystemOverrides.isEmpty())
            assertEquals(Gson().toJson(original), Gson().toJson(snapshot.chains.single { it.chain.id == "duplicate" }))
            assertTrue(fixture.service.getCurrentProcessXcmDiscoveryChains().isEmpty())
        }
    }

    @Test
    fun unrelatedMalformedEntryIsSkippedWhileObservedUnsupportedIdIsStillQuarantined() = runBlocking {
        val malformed = Gson().fromJson(
            """{"chainId":"broken","name":"Broken","addressPrefix":42,"ecosystem":"Substrate","nodes":[null]}""",
            ChainRemote::class.java
        )
        val fixture = Fixture(listOf(local("unsupported"), local("broken")))
        fixture.prepare(listOf(remote("unsupported", "future"), malformed))
        // One malformed entry no longer fails the whole refresh: it is skipped and keeps its stored row.
        fixture.service.syncUp()
        val snapshot = fixture.snapshots.single()
        assertEquals(setOf("unsupported", "broken"), snapshot.preservedCatalogChains)
        assertEquals(mapOf("unsupported" to "future"), snapshot.unsupportedEcosystemOverrides)
        assertEquals("Stored broken", snapshot.chains.single { it.chain.id == "broken" }.chain.name)
        assertTrue(fixture.service.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun cancellationPropagatesWithoutAdmittingAnyCatalogOperation() = runBlocking {
        val fixture = Fixture(emptyList())
        val expected = CancellationException("public cancellation")
        whenever(fixture.fetcher.getChains()).thenThrow(expected)
        assertSame(expected, runCatching { fixture.service.syncUp() }.exceptionOrNull())
        Mockito.verifyNoInteractions(fixture.dao, fixture.accounts, fixture.assets)
    }
}
