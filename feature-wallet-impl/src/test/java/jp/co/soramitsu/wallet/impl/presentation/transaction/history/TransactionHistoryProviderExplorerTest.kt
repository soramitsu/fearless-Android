package jp.co.soramitsu.wallet.impl.presentation.transaction.history

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.subquery.ExplorerOnlyHistoryException
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.OperationsPageChange
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A history service without data for the account is replaced by the explorer link, never an error or "no operations". */
class TransactionHistoryProviderExplorerTest {
    @Test
    fun `missing indexer data shows the explorer state and reports no error`() = runBlocking {
        val (state, effects) = settle(ExplorerOnlyHistoryException())
        assertTrue(state.explorerOnly)
        assertEquals(UNSUPPORTED, state.message)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `other history failures keep their error and the ordinary empty list`() = runBlocking {
        val (state, effects) = settle(IllegalStateException("history provider failed"))
        assertFalse(state.explorerOnly)
        assertTrue(effects.isNotEmpty() && effects.all { it is TransactionHistoryUi.SideEffect.Error })
    }

    private suspend fun settle(failure: Throwable): Pair<TransactionHistoryUi.State.Empty, List<TransactionHistoryUi.SideEffect>> {
        val effects = mutableListOf<TransactionHistoryUi.SideEffect>()
        val resources = mockk<ResourceManager>()
        every { resources.getString(any<Int>()) } answers {
            if (firstArg<Int>() == R.string.wallet_transaction_history_unsupported_message) UNSUPPORTED else "other"
        }
        val provider = TransactionHistoryProvider(interactor(failure), mockk(relaxed = true), mockk(relaxed = true),
            HistoryFiltersProvider(), resources, mockk(relaxed = true), MutableStateFlow(AssetPayload("solana-mainnet", "SOL")))
        return kotlinx.coroutines.coroutineScope {
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { provider.sideEffects().collect { synchronized(effects) { effects += it } } }
            // The provider's startup reload can race its own event subscription, so ask again until a reload has run.
            withTimeout(30_000) {
                while (provider.state().value !is TransactionHistoryUi.State.Empty) {
                    provider.tryReloadHistory()
                    delay(300)
                }
            }
            // Let the cached-page observer publish its own state after the failed sync.
            delay(500)
            val state = provider.state().value as TransactionHistoryUi.State.Empty
            collector.cancel(); provider.cancel()
            state to synchronized(effects) { effects.toList() }
        }
    }

    private fun interactor(failure: Throwable): WalletInteractor {
        val chain = mockk<Chain>(relaxed = true)
        every { chain.ecosystem } returns Ecosystem.Substrate
        return object : WalletInteractor by mockk(relaxed = true) {
            override suspend fun getChain(chainId: String): Chain = chain
            override suspend fun syncOperationsFirstPage(chainId: String, chainAssetId: String, pageSize: Int,
                filters: Set<TransactionFilter>): Result<CursorPage<Operation>> = Result.failure(failure)
            override fun operationsFirstPageFlow(chainId: String, chainAssetId: String): Flow<OperationsPageChange> =
                flowOf(OperationsPageChange(CursorPage(null, emptyList()), accountChanged = false))
        }
    }

    private companion object {
        const val UNSUPPORTED = "Transaction history for this network is not supported yet"
    }
}
