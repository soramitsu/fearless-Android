package jp.co.soramitsu.wallet.impl.presentation.transaction.history

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.OperationsPageChange
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * `ChainsRepository.getChain` gives up after 60 seconds when a network is missing from the catalogue. The history
 * panel asked for it in a scope without a handler, so the failure was uncaught and Android closed the app.
 */
class TransactionHistoryProviderChainUnavailableTest {

    @Test
    fun `a network missing from the catalogue shows the error state and closes nothing`() = runBlocking {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, failure -> uncaught += failure }
        val resources = mockk<ResourceManager>()
        every { resources.getString(any<Int>()) } answers {
            if (firstArg<Int>() == R.string.wallet_transaction_history_error_message) ERROR else "other"
        }
        val provider = TransactionHistoryProvider(interactor(), mockk(relaxed = true), mockk(relaxed = true),
            HistoryFiltersProvider(), resources, mockk(relaxed = true), MutableStateFlow(AssetPayload("gone", "asset")))
        try {
            val state = withTimeout(15_000) {
                // The provider's startup reload can race its own event subscription, so ask again until one has run.
                while (provider.state().value !is TransactionHistoryUi.State.Empty) {
                    provider.tryReloadHistory()
                    delay(300)
                }
                provider.state().value as TransactionHistoryUi.State.Empty
            }

            assertEquals(ERROR, state.message)
            assertTrue("uncaught: $uncaught", uncaught.isEmpty())
        } finally {
            provider.cancel()
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    private fun interactor(): WalletInteractor = object : WalletInteractor by mockk(relaxed = true) {
        override suspend fun getChain(chainId: String): Chain =
            throw IllegalStateException("Chain $chainId not available locally yet; sync pending or failed")
        override suspend fun syncOperationsFirstPage(chainId: String, chainAssetId: String, pageSize: Int,
            filters: Set<TransactionFilter>): Result<CursorPage<Operation>> = Result.success(CursorPage(null, emptyList()))
        override fun operationsFirstPageFlow(chainId: String, chainAssetId: String): Flow<OperationsPageChange> =
            flowOf(OperationsPageChange(CursorPage(null, emptyList()), accountChanged = false))
    }

    private companion object {
        const val ERROR = "Transaction history is unavailable"
    }
}
