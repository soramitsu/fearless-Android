package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.historySource.HistorySource
import jp.co.soramitsu.wallet.impl.data.historySource.HistorySourceProvider
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.wallet.impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRepositoryTest {

    private val dot = WalletTestModels.asset()
    private val history = Chain.ExternalApi(null, Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.SUBQUERY, "https://h"), null)
    private val chain = WalletTestModels.chain(assets = listOf(dot), externalApi = history)
    private val accountId = WalletTestModels.substrateAccountId
    private val source = mockk<HistorySource>()
    private val sources = mockk<HistorySourceProvider> { every { this@mockk.invoke("https://h", any()) } returns source }
    private val dao = mockk<OperationDao>(relaxed = true)
    private val preferences = InMemoryPreferences()
    private val cursors = TransferCursorStorage(preferences)
    private val currentAddress = mockk<CurrentAccountAddressUseCase>()
    private val repository = HistoryRepository(sources, dao, cursors, currentAddress)

    private fun transfer(id: String) = Operation(
        id, "me", Operation.Type.Transfer("0x$id", "me", BigInteger.ONE, "you", "me", Operation.Status.COMPLETED, null), 1L, dot
    )

    @Test
    fun operationsComeFromTheChainsHistorySource(): Unit = runBlocking {
        coEvery { source.getOperations(10, "c", any(), accountId, chain, dot, "me") } returns CursorPage("next", listOf(transfer("1")))

        val page = repository.getOperations(10, "c", setOf(TransactionFilter.TRANSFER), accountId, chain, dot, "me")

        assertEquals("next", page.nextCursor)
        assertEquals("1", page.single().id)
    }

    @Test
    fun chainsWithoutHistoryOrNonUtilitySquidAssetsAreUnsupported() {
        val noHistory = WalletTestModels.chain(assets = listOf(dot))
        val squid = WalletTestModels.chain(
            assets = listOf(dot),
            externalApi = Chain.ExternalApi(null, Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.SUBSQUID, "https://s"), null)
        )
        val token = WalletTestModels.asset(id = "usdt", isUtility = false)

        assertThrows(HistoryNotSupportedException::class.java) {
            runBlocking { repository.getOperations(10, null, emptySet(), accountId, noHistory, dot) }
        }
        assertThrows(HistoryNotSupportedException::class.java) {
            runBlocking { repository.getOperations(10, null, emptySet(), accountId, squid, token) }
        }
    }

    @Test
    fun missingSourceGivesAnEmptyPage(): Unit = runBlocking {
        every { sources.invoke(any(), any()) } returns null

        val page = repository.getOperations(10, null, emptySet(), accountId, chain, dot)

        assertTrue(page.isEmpty())
    }

    @Test
    fun firstPageSyncFetchesUntilHalfAPageAndStoresItWithTheCursor(): Unit = runBlocking {
        coEvery { source.getOperations(4, null, any(), any(), any(), any(), any()) } returns CursorPage("p2", listOf(transfer("1")))
        coEvery { source.getOperations(4, "p2", any(), any(), any(), any(), any()) } returns CursorPage("p3", listOf(transfer("2"), transfer("3")))
        val stored = slot<List<OperationLocal>>()
        coEvery { dao.insertFromSubquery(any(), chain.id, dot.id, capture(stored)) } returns Unit
        every { dao.observe(any(), chain.id, dot.id) } answers { flowOf(stored.captured) }

        val page = repository.syncOperationsFirstPage(4, TransactionFilter.entries.toSet(), accountId, chain, dot)
        val observed = repository.operationsFirstPageFlow(accountId, chain, dot).first()

        assertEquals("p3", page.nextCursor)
        assertEquals(listOf("0x1", "0x2", "0x3"), stored.captured.map { it.hash })
        assertEquals("p3", observed.nextCursor)
        assertEquals(3, observed.size)
    }

    @Test
    fun operationAddressesAreReadForTheCurrentAccount(): Unit = runBlocking {
        coEvery { currentAddress.invoke("polkadot") } returns "me"
        coEvery { currentAddress.invoke("none") } returns null
        every { dao.observeOperationAddresses("polkadot", "me", 5) } returns flowOf(listOf("a", "a", "b"))
        every { dao.getOperationAddresses("polkadot", "me", -1) } returns listOf("a", "", "c")

        assertEquals(setOf("a", "b"), repository.getOperationAddressWithChainIdFlow("polkadot", 5).first())
        assertEquals(setOf("a", "c"), repository.getOperationAddressWithChainId("polkadot", null))
        assertEquals(emptySet<String>(), repository.getOperationAddressWithChainId("none", 3))
        coVerify(exactly = 0) { dao.getOperationAddresses("none", any(), any()) }
    }
}
