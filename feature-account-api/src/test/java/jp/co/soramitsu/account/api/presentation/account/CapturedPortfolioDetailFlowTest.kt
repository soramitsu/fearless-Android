package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.model.TotalBalance
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapturedPortfolioDetailFlowTest {
    @Test fun resolverReceivesTheExactReadyCatalogAndCurrencyInstance() = runTest {
        val source = originReady()
        val detail = Any()
        val values = capturedPortfolioDetailFlow(flowOf(source)) { captured ->
            assertSame(source, captured)
            assertSame(source.chainsById, captured.chainsById)
            assertSame(source.meta, captured.meta)
            assertEquals("cad", captured.currencyId)
            detail
        }.toList()
        assertSame(source, values.last().source)
        assertSame(detail, values.last().detail)
    }

    @Test fun nullableDetailRetainsCompletedPortfolioWithoutInventingAnIdentity() = runTest {
        val source = originReady()
        val values = capturedPortfolioDetailFlow<String>(flowOf(source)) { null }.toList()
        assertSame(source, values.last().source)
        assertNull(values.last().detail)
        assertTrue(values.none { it.source === TotalBalance.Unavailable })
    }

    @Test fun pendingStatesNeverCallTheDetailResolver() = runTest {
        val loading = TotalBalance.Loading(7, "cad")
        val values = capturedPortfolioDetailFlow<String>(
            flowOf(TotalBalance.AwaitingSelection, loading, TotalBalance.Unavailable)
        ) { fail("Pending portfolio must not resolve details"); "unexpected" }.toList()
        assertSame(TotalBalance.Unavailable, values.last().source)
        assertTrue(values.all { it.detail == null })
    }

    @Test fun catalogReplacementWithSameWalletAndCurrencyRevokesBeforeNewResult() = runTest {
        val first = originReady()
        val second = originReady(meta = first.meta, chains = mapOf("captured-chain" to originChain(2)))
        val balances = Channel<TotalBalance>(Channel.UNLIMITED)
        val requests = mutableListOf<Pair<TotalBalance.Ready, CompletableDeferred<Any?>>>()
        val values = mutableListOf<CapturedPortfolioDetail<Any>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            capturedPortfolioDetailFlow(balances.receiveAsFlow()) { source ->
                CompletableDeferred<Any?>().also { requests += source to it }.await()
            }.collect { values += it }
        }
        try {
            balances.send(first); runCurrent()
            val oldDetail = Any(); requests[0].second.complete(oldDetail); runCurrent()
            assertSame(oldDetail, values.last().detail)
            balances.send(second); runCurrent()
            assertSame(second, values.last().source); assertNull(values.last().detail)
            assertSame(second, requests[1].first)
            val newDetail = Any(); requests[1].second.complete(newDetail); runCurrent()
            assertSame(second, values.last().source); assertSame(newDetail, values.last().detail)
        } finally { job.cancelAndJoin(); balances.close() }
    }
}
