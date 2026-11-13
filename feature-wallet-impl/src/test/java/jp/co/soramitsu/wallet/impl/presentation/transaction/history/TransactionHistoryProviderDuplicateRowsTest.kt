package jp.co.soramitsu.wallet.impl.presentation.transaction.history

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.OperationsPageChange
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.latestById
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.model.DayHeader
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

/**
 * The history list keys each row by its operation id, and Compose closes the app when two rows share a key
 * ("Key ... was already used"). A later page can repeat an id: the same transfer again, now completed, or two token
 * transfers inside one transaction.
 */
class TransactionHistoryProviderDuplicateRowsTest {

    @Test
    fun `one operation per id and the last copy wins`() {
        val pending = transfer("0xa", Operation.Status.PENDING)
        val completed = transfer("0xa", Operation.Status.COMPLETED)
        val other = transfer("0xb", Operation.Status.COMPLETED)

        assertEquals(listOf(completed, other), listOf(pending, other, completed).latestById())
        assertEquals(emptyList<Operation>(), emptyList<Operation>().latestById())
    }

    @Test
    fun `a next page that repeats ids gives the list one row per key`() = runBlocking {
        val firstPage = listOf(transfer("0xa", Operation.Status.PENDING), transfer("0xb", Operation.Status.COMPLETED))
        val nextPage = listOf(
            // The first page's pending transfer, completed since.
            transfer("0xa", Operation.Status.COMPLETED),
            // Two token transfers inside one transaction: the explorer lists the hash twice.
            transfer("0xc", Operation.Status.COMPLETED, amount = BigInteger.ONE),
            transfer("0xc", Operation.Status.COMPLETED, amount = BigInteger.TEN)
        )
        val payload = AssetPayload("chain", "asset")
        val provider = TransactionHistoryProvider(interactor(firstPage, nextPage), mockk(relaxed = true), mockk(relaxed = true),
            HistoryFiltersProvider(), mockk<ResourceManager>(relaxed = true), mockk(relaxed = true), MutableStateFlow(payload))

        try {
            val rows = withTimeout(30_000) {
                var rows: List<Any>? = null
                while (rows == null) {
                    when (val state = provider.state().value) {
                        is TransactionHistoryUi.State.Data -> {
                            // Scrolling half-way down the loaded page asks for the next one.
                            if (state.operations().none { it.id == "0xc" }) provider.scrolled(HALF_PAGE, payload) else rows = state.items
                        }
                        // The provider's startup reload can race its own event subscription, so ask again.
                        else -> provider.tryReloadHistory()
                    }
                    delay(300)
                }
                rows
            }

            val keys = rows.map(::rowKey)
            assertEquals("every row needs its own key: $keys", keys.distinct(), keys)
            val operations = rows.filterIsInstance<OperationModel>()
            assertEquals(setOf("0xa", "0xb", "0xc"), operations.map { it.id }.toSet())
            assertEquals(OperationStatusAppearance.COMPLETED, operations.single { it.id == "0xa" }.statusAppearance)
        } finally {
            provider.cancel()
        }
    }

    private fun TransactionHistoryUi.State.Data.operations() = items.filterIsInstance<OperationModel>()

    /** The key BalanceDetailsScreen gives each history row. */
    private fun rowKey(row: Any): Any = when (row) {
        is OperationModel -> row.id
        is DayHeader -> row.daysSinceEpoch
        else -> row.hashCode()
    }

    private fun interactor(firstPage: List<Operation>, nextPage: List<Operation>): WalletInteractor {
        val chain = mockk<Chain>(relaxed = true)
        every { chain.ecosystem } returns Ecosystem.Ton
        return object : WalletInteractor by mockk(relaxed = true) {
            override suspend fun getChain(chainId: String): Chain = chain
            override suspend fun syncOperationsFirstPage(chainId: String, chainAssetId: String, pageSize: Int,
                filters: Set<TransactionFilter>): Result<CursorPage<Operation>> = Result.success(CursorPage(NEXT, firstPage))
            override fun operationsFirstPageFlow(chainId: String, chainAssetId: String): Flow<OperationsPageChange> =
                flowOf(OperationsPageChange(CursorPage(NEXT, firstPage), accountChanged = false))
            override suspend fun getOperations(chainId: String, chainAssetId: String, pageSize: Int, cursor: String?,
                filters: Set<TransactionFilter>): Result<CursorPage<Operation>> =
                if (cursor == NEXT) Result.success(CursorPage(null, nextPage)) else Result.success(CursorPage(null, emptyList()))
        }
    }

    private fun transfer(id: String, status: Operation.Status, amount: BigInteger = BigInteger.ONE) = Operation(
        id = id,
        address = ME,
        type = Operation.Type.Transfer(hash = id, myAddress = ME, amount = amount, receiver = ME, sender = "sender",
            status = status, fee = null),
        time = DAY,
        chainAsset = ASSET
    )

    private companion object {
        const val ME = "me"
        const val NEXT = "2"
        const val DAY = 1_700_000_000_000L
        const val HALF_PAGE = TransactionHistoryProvider.NEXT_PAGE_LOADING_OFFSET
        val ASSET: Asset = mockk<Asset>(relaxed = true).also {
            every { it.precision } returns 18
            every { it.symbol } returns "ETH"
        }
    }
}
