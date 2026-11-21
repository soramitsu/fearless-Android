package jp.co.soramitsu.wallet.impl.presentation.transaction.history

import android.graphics.Picture
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.PictureDrawable
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.WalletTestModels.ALICE
import jp.co.soramitsu.wallet.impl.WalletTestModels.BOB
import jp.co.soramitsu.wallet.impl.data.network.subquery.ExplorerOnlyHistoryException
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.OperationsPageChange
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.SwapDetailState
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.model.DayHeader
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The history list: filtering and day grouping of the cached first page, paging, and operation details. */
@RunWith(RobolectricTestRunner::class)
class TransactionHistoryProviderCoverageTest {

    private val dot = WalletTestModels.asset()
    private val polkadot = WalletTestModels.chain(assets = listOf(dot))
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } answers { "res:${firstArg<Int>()}" }
        every { getDrawable(any()) } returns ColorDrawable(0)
    }
    private val icons = mockk<AddressIconGenerator> {
        coEvery { createAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture())
        coEvery { createEthereumAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture())
        coEvery { createWalletIcon(any(), any()) } returns PictureDrawable(Picture())
    }
    private val filters = HistoryFiltersProvider()
    private val pageRequests = CopyOnWriteArrayList<String?>()
    private val providers = mutableListOf<TransactionHistoryProvider>()

    private val extrinsic = operation("ext", DAY_ONE + 1_000, Operation.Type.Extrinsic(
        "0xext", "balances", "transfer", BigInteger.valueOf(2_000_000), Operation.Status.COMPLETED
    ))
    private val reward = operation("reward", DAY_ONE + 2_000, Operation.Type.Reward(BigInteger.TEN.pow(9), true, 12, BOB))
    private val swap = operation("swap", DAY_TWO + 1_000, Operation.Type.Swap(
        "0xswap", "liquidityProxy", BigInteger.TEN.pow(10), BigInteger.ZERO, "SMART", null, null, BigInteger.ZERO,
        Operation.Status.COMPLETED
    ))
    private val transfer = operation("transfer", DAY_TWO + 2_000, Operation.Type.Transfer(
        "0xtransfer", ALICE, BigInteger.TEN.pow(10), BOB, ALICE, Operation.Status.COMPLETED, BigInteger.ONE
    ))
    private val older = operation("older", DAY_ONE - 5_000, Operation.Type.Extrinsic(
        "0xolder", "balances", "transfer", BigInteger.ONE, Operation.Status.FAILED
    ))

    @Volatile private var firstPage: Flow<OperationsPageChange> =
        flowOf(OperationsPageChange(CursorPage(CURSOR, listOf(extrinsic, reward, swap, transfer)), accountChanged = false))
    @Volatile private var nextPage: () -> Result<CursorPage<Operation>> = { Result.success(CursorPage(null, emptyList())) }

    @After
    fun stop() {
        providers.forEach { it.cancel() }
    }

    @Test
    fun `the first page lists the filtered operations newest first under day headers`(): Unit = runBlocking {
        filters.setCustomFilters(setOf(TransactionFilter.EXTRINSIC, TransactionFilter.TRANSFER))
        val provider = provider()

        val data = provider.awaitState { it is TransactionHistoryUi.State.Data } as TransactionHistoryUi.State.Data

        // The reward is left out by the filters; swaps count as extrinsics.
        assertEquals(listOf<Any>(DAY_TWO_INDEX, "transfer", "swap", DAY_ONE_INDEX, "ext"), data.items.map(::key))
    }

    @Test
    fun `an unreadable cached page shows the ordinary empty list`(): Unit = runBlocking {
        firstPage = flow<OperationsPageChange> { throw IllegalStateException("cache unreadable") }
        val provider = provider()

        val empty = provider.awaitState { it is TransactionHistoryUi.State.Empty } as TransactionHistoryUi.State.Empty

        assertFalse(empty.explorerOnly)
        assertNull(empty.message)
    }

    @Test
    fun `scrolling past the middle of the page loads and appends the next page once`(): Unit = runBlocking {
        nextPage = { Result.success(CursorPage(null, listOf(older))) }
        val provider = provider()
        provider.awaitState { it is TransactionHistoryUi.State.Data }

        provider.scrolled(10, PAYLOAD) // not far enough yet
        provider.scrolled(60, PAYLOAD)

        val data = withTimeout(TIMEOUT_MS) {
            provider.state().first { it is TransactionHistoryUi.State.Data && it.items.count { item -> item is OperationModel } == 5 }
        } as TransactionHistoryUi.State.Data
        assertEquals("older", (data.items.last() as OperationModel).id)
        assertEquals(listOf<String?>(CURSOR), pageRequests.toList())

        delay(SETTLE_MS)
        provider.scrolled(60, PAYLOAD) // the last page had no cursor: nothing more to load
        delay(SETTLE_MS)
        assertEquals(1, pageRequests.size)
    }

    @Test
    fun `a failed next page is reported`(): Unit = runBlocking {
        nextPage = { Result.failure(IllegalStateException("page failed")) }
        val provider = provider()
        provider.awaitState { it is TransactionHistoryUi.State.Data }
        val effects = CopyOnWriteArrayList<TransactionHistoryUi.SideEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { provider.sideEffects().collect { effects += it } }

        provider.scrolled(60, PAYLOAD)
        withTimeout(TIMEOUT_MS) { while (effects.isEmpty()) delay(20) }
        collector.cancel()

        assertEquals("page failed", (effects.single() as TransactionHistoryUi.SideEffect.Error).message)
        assertEquals(listOf<String?>(CURSOR), pageRequests.toList())
    }

    @Test
    fun `an explorer-only next page stops paging quietly`(): Unit = runBlocking {
        nextPage = { Result.failure(ExplorerOnlyHistoryException()) }
        val provider = provider()
        provider.awaitState { it is TransactionHistoryUi.State.Data }
        val effects = CopyOnWriteArrayList<TransactionHistoryUi.SideEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { provider.sideEffects().collect { effects += it } }

        provider.scrolled(60, PAYLOAD)
        withTimeout(TIMEOUT_MS) { while (pageRequests.isEmpty()) delay(20) }
        delay(SETTLE_MS)
        provider.scrolled(60, PAYLOAD)
        delay(SETTLE_MS)
        collector.cancel()

        assertEquals(1, pageRequests.size)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `an empty next page leaves the list as it was`(): Unit = runBlocking {
        val provider = provider()
        provider.awaitState { it is TransactionHistoryUi.State.Data }

        provider.scrolled(60, PAYLOAD)
        withTimeout(TIMEOUT_MS) { while (pageRequests.isEmpty()) delay(20) }
        delay(SETTLE_MS)

        val data = provider.state().value as TransactionHistoryUi.State.Data
        assertEquals(4, data.items.count { it is OperationModel })
    }

    @Test
    fun `details come from the cached operation and an unknown row is refused`(): Unit = runBlocking {
        val provider = provider()
        val data = provider.awaitState { it is TransactionHistoryUi.State.Data } as TransactionHistoryUi.State.Data
        val swapRow = data.items.filterIsInstance<OperationModel>().single { it.id == "swap" }

        val details = provider.getTransactionDetailsState(swapRow, PAYLOAD)

        assertEquals("0xswap", (details as SwapDetailState).hash)
        assertEquals(ALICE, details.address)
        val missing = mockk<OperationModel> { every { id } returns "missing" }
        val failure = runCatching { provider.getTransactionDetailsState(missing, PAYLOAD) }.exceptionOrNull()
        assertEquals("Cannot find operation in cache", failure?.message)
    }

    private fun key(item: Any): Any = when (item) {
        is DayHeader -> item.daysSinceEpoch
        is OperationModel -> item.id
        else -> item
    }

    /**
     * The provider's startup reload can race its own event subscription, so ask again only until a
     * reload has started; then wait for [predicate] and let any extra reload finish.
     */
    private suspend fun TransactionHistoryProvider.awaitState(
        predicate: (TransactionHistoryUi.State) -> Boolean
    ): TransactionHistoryUi.State = withTimeout(TIMEOUT_MS) {
        while (state().value === TransactionHistoryUi.State.Refreshing) {
            tryReloadHistory()
            delay(200)
        }
        state().first(predicate)
        delay(SETTLE_MS)
        state().first(predicate)
    }

    private fun provider() = TransactionHistoryProvider(
        walletInteractor = interactor(),
        iconGenerator = icons,
        router = mockk(relaxed = true),
        historyFiltersProvider = filters,
        resourceManager = resources,
        addressDisplayUseCase = mockk(relaxed = true),
        assetPayloadStateFlow = MutableStateFlow(PAYLOAD)
    ).also(providers::add)

    private fun interactor(): WalletInteractor = object : WalletInteractor by mockk(relaxed = true) {
        override suspend fun getChain(chainId: String): Chain = polkadot

        override suspend fun syncOperationsFirstPage(
            chainId: String,
            chainAssetId: String,
            pageSize: Int,
            filters: Set<TransactionFilter>
        ): Result<CursorPage<Operation>> = Result.success(CursorPage(CURSOR, emptyList()))

        override fun operationsFirstPageFlow(chainId: String, chainAssetId: String): Flow<OperationsPageChange> = firstPage

        override suspend fun getOperations(
            chainId: String,
            chainAssetId: String,
            pageSize: Int,
            cursor: String?,
            filters: Set<TransactionFilter>
        ): Result<CursorPage<Operation>> {
            pageRequests += cursor
            return nextPage()
        }
    }

    private fun operation(id: String, time: Long, type: Operation.Type) = Operation(id, ALICE, type, time, dot)

    private companion object {
        const val CURSOR = "cursor-1"
        const val TIMEOUT_MS = 20_000L
        const val SETTLE_MS = 400L
        const val DAY_ONE_INDEX = 20_000L
        const val DAY_TWO_INDEX = 20_001L
        val DAY_ONE = TimeUnit.DAYS.toMillis(DAY_ONE_INDEX)
        val DAY_TWO = TimeUnit.DAYS.toMillis(DAY_TWO_INDEX)
        val PAYLOAD = AssetPayload("polkadot", "dot")
    }
}
