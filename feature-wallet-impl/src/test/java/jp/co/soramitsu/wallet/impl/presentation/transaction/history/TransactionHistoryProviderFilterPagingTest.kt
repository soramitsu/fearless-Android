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
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
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
 * With history filters set (for example transfers only), the first page showed only matching operations, but
 * loading the next page showed every loaded operation: the first page's hidden rows came back and the new page was
 * not filtered at all.
 */
class TransactionHistoryProviderFilterPagingTest {

    @Test
    fun `later pages keep the active filters`() = runBlocking {
        val firstPage = listOf(transfer("0xa"), reward("r1"), extrinsic("x1"))
        // History sources may ignore the filters they are given; the list must still apply them.
        val nextPage = listOf(transfer("0xc"), reward("r2"), extrinsic("x2"))
        val filters = HistoryFiltersProvider().apply { setCustomFilters(setOf(TransactionFilter.TRANSFER)) }
        val payload = AssetPayload("chain", "asset")
        val provider = TransactionHistoryProvider(interactor(firstPage, nextPage), mockk(relaxed = true), mockk(relaxed = true),
            filters, mockk<ResourceManager>(relaxed = true), mockk(relaxed = true), MutableStateFlow(payload))

        try {
            val firstIds = withTimeout(30_000) { awaitIds(provider) { true } }
            assertEquals(setOf("0xa"), firstIds)

            val ids = withTimeout(30_000) {
                awaitIds(provider) { ids ->
                    ("0xc" in ids).also { loaded -> if (!loaded) provider.scrolled(HALF_PAGE, payload) }
                }
            }
            assertEquals(setOf("0xa", "0xc"), ids)
        } finally {
            provider.cancel()
        }
    }

    private suspend fun awaitIds(provider: TransactionHistoryProvider, done: (Set<String>) -> Boolean): Set<String> {
        while (true) {
            when (val state = provider.state().value) {
                is TransactionHistoryUi.State.Data -> {
                    val ids = state.items.filterIsInstance<OperationModel>().map { it.id }.toSet()
                    if (done(ids)) return ids
                }
                // The provider's startup reload can race its own event subscription, so ask again.
                else -> provider.tryReloadHistory()
            }
            delay(300)
        }
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

    private fun transfer(id: String) = operation(id, Operation.Type.Transfer(hash = id, myAddress = ME, amount = BigInteger.ONE,
        receiver = ME, sender = "sender", status = Operation.Status.COMPLETED, fee = null))

    private fun reward(id: String) = operation(id, Operation.Type.Reward(amount = BigInteger.ONE, isReward = true, era = 1, validator = null))

    private fun extrinsic(id: String) = operation(id, Operation.Type.Extrinsic(hash = id, module = "staking", call = "bond",
        fee = BigInteger.ONE, status = Operation.Status.COMPLETED))

    private fun operation(id: String, type: Operation.Type) = Operation(id = id, address = ME, type = type, time = DAY, chainAsset = ASSET)

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
