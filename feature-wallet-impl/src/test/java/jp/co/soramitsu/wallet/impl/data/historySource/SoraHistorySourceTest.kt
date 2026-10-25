package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.testshared.whenever
import jp.co.soramitsu.wallet.impl.data.repository.HistoryRepository
import jp.co.soramitsu.wallet.impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.adapters.HistoryInfoRemoteLoader
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.ChainInfo
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxFilter
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxHistoryInfo
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxHistoryItem
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxHistoryItemParam
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

class SoraHistorySourceTest {
    @Test
    fun `transport failure propagates instead of returning empty history`() = runBlocking {
        val failure = IllegalStateException("history unavailable")
        val loader = RecordingLoader { throw failure }

        val result = runCatching { load(loader) }

        assertSame(failure, result.exceptionOrNull())
        assertEquals(1, loader.calls)
    }

    @Test
    fun `cancellation propagates without becoming successful history`() = runBlocking {
        val cancellation = CancellationException("account changed")
        val loader = RecordingLoader { throw cancellation }

        val result = runCatching { load(loader) }

        assertSame(cancellation, result.exceptionOrNull())
        assertEquals(1, loader.calls)
    }

    @Test
    fun `successful empty terminal page remains valid and ignores stale continuation`() = runBlocking {
        val page = load(RecordingLoader { TxHistoryInfo("stale", true, emptyList()) })

        assertTrue(page.items.isEmpty())
        assertEquals(null, page.nextCursor)
    }

    @Test
    fun `successful page keeps operations request identity and continuation`() = runBlocking {
        val loader = RecordingLoader {
            TxHistoryInfo("next-page", false, listOf(transfer()))
        }

        val page = load(loader, "previous-page")

        assertEquals(listOf("transfer-id"), page.items.map { it.id })
        assertEquals("next-page", page.nextCursor)
        assertEquals(25, loader.pageCount)
        assertEquals("previous-page", loader.cursor)
        assertEquals(ACCOUNT, loader.address)
        assertEquals(ChainInfo.Simple(CHAIN), loader.chainInfo)
        assertEquals(setOf(TxFilter.TRANSFER, TxFilter.REWARD, TxFilter.EXTRINSIC), loader.filters)
    }

    @Test
    fun `failed SORA refresh preserves cached operations and cursor for retry`() = runBlocking {
        val failure = IllegalStateException("history unavailable")
        val provider = mock(HistorySourceProvider::class.java)
        val dao = mock(OperationDao::class.java)
        val cursor = mock(TransferCursorStorage::class.java)
        val chain = chain()
        whenever(chain.externalApi).thenReturn(
            Chain.ExternalApi(
                staking = null,
                history = Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.SORA, HISTORY_URL),
                crowdloans = null
            )
        )
        whenever(provider(HISTORY_URL, Chain.ExternalApi.Section.Type.SORA)).thenReturn(
            SoraHistorySource(RecordingLoader { throw failure })
        )
        val repository = HistoryRepository(provider, dao, cursor, mock(CurrentAccountAddressUseCase::class.java))

        val result = runCatching {
            repository.syncOperationsFirstPage(25, FILTERS, byteArrayOf(1, 2), chain, asset(), ACCOUNT)
        }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(failure.message, result.exceptionOrNull()?.message)
        verifyNoInteractions(dao, cursor)
    }

    private suspend fun load(loader: HistoryInfoRemoteLoader, cursor: String? = null) =
        SoraHistorySource(loader).getOperations(25, cursor, FILTERS, byteArrayOf(1, 2), chain(), asset(), ACCOUNT)

    private fun chain() = mock(Chain::class.java).also { whenever(it.id).thenReturn(CHAIN) }

    private fun asset() = mock(Asset::class.java).also {
        whenever(it.id).thenReturn("xor")
        whenever(it.currencyId).thenReturn("asset-xor")
        whenever(it.precision).thenReturn(18)
    }

    private fun transfer() = TxHistoryItem(
        id = "transfer-id", blockHash = "block", module = "assets", method = "transfer",
        timestamp = "1704067200", networkFee = "1", success = true,
        data = listOf(
            TxHistoryItemParam("assetId", "asset-xor"),
            TxHistoryItemParam("from", ACCOUNT),
            TxHistoryItemParam("to", "recipient"),
            TxHistoryItemParam("amount", "1")
        ),
        nestedData = null
    )

    private class RecordingLoader(private val reply: () -> TxHistoryInfo) : HistoryInfoRemoteLoader {
        var calls = 0
        var pageCount: Int? = null
        var cursor: String? = null
        var address: String? = null
        var chainInfo: ChainInfo? = null
        var filters: Set<TxFilter>? = null

        override suspend fun loadHistoryInfo(
            pageCount: Int, cursor: String?, signAddress: String, chainInfo: ChainInfo, filters: Set<TxFilter>
        ): TxHistoryInfo {
            calls++
            this.pageCount = pageCount
            this.cursor = cursor
            address = signAddress
            this.chainInfo = chainInfo
            this.filters = filters
            return reply()
        }
    }

    private companion object {
        const val CHAIN = "sora-chain"
        const val ACCOUNT = "account"
        const val HISTORY_URL = "https://history.example/sora"
        val FILTERS = setOf(TransactionFilter.TRANSFER)
    }
}
