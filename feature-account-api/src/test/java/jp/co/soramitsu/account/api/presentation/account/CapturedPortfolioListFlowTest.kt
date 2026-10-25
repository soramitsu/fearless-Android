package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CapturedPortfolioListFlowTest {
    @Test fun finiteRosterKeepsExactOrderAndCapturedRows() = runTest {
        val first = ready(7); val second = ready(9)
        val values = capturedPortfolioListFlow(flowOf(listOf(9L, 7L)), { flowOf(if (it == 7L) first else second) }) {
            it.meta.name
        }.toList()
        assertEquals(listOf(9L, 7L), values.last().map { it.metaId })
        assertSame(second, values.last()[0].portfolio.source)
        assertSame(first, values.last()[1].portfolio.source)
        assertEquals("Wallet 9", values.last()[0].portfolio.detail)
    }

    @Test fun emptyRosterDoesNotStartAnyWalletObserver() = runTest {
        var calls = 0
        val values = capturedPortfolioListFlow<String>(flowOf(emptyList()), { calls++; error("unexpected") }) { "icon" }.toList()
        assertEquals(0, calls)
        assertTrue(values.all { it.isEmpty() })
    }

    @Test fun repeatedRosterDoesNotDuplicateCollectors() = runTest {
        val ids = Channel<List<Long>>(Channel.UNLIMITED)
        var calls = 0; var stops = 0
        val job = launch {
            capturedPortfolioListFlow(ids.receiveAsFlow(), {
                calls++
                flow { try { emit(ready(it)); CompletableDeferred<Unit>().await() } finally { stops++ } }
            }) { it.meta.name }.collect()
        }
        ids.send(listOf(7)); runCurrent()
        ids.send(listOf(7)); runCurrent()
        assertEquals(1, calls); assertEquals(0, stops)
        job.cancel(); job.join(); assertEquals(1, stops)
    }

    @Test fun removalClearsBeforeBlockedOldCleanup() = runTest {
        val ids = Channel<List<Long>>(Channel.UNLIMITED)
        val entered = CompletableDeferred<Unit>(); val cleanup = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val values = mutableListOf<List<CapturedWalletPortfolio<String>>>()
        val job = launch {
            capturedPortfolioListFlow(ids.receiveAsFlow(), { flowOf(ready(it)) }) {
                entered.complete(Unit)
                try { CompletableDeferred<Unit>().await(); "old" }
                finally { withContext(NonCancellable) { cleanup.complete(Unit); release.await() } }
            }.collect { values += it }
        }
        ids.send(listOf(7)); runCurrent(); assertTrue(entered.isCompleted)
        ids.send(emptyList()); runCurrent()
        assertTrue(cleanup.isCompleted); assertFalse(release.isCompleted); assertTrue(values.last().isEmpty())
        release.complete(Unit); ids.close(); job.join()
        assertTrue(values.last().isEmpty())
    }

    @Test fun replacementRejectsLateIconFromPreviousRoster() = runTest {
        val ids = Channel<List<Long>>(Channel.UNLIMITED); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val values = mutableListOf<List<CapturedWalletPortfolio<String>>>()
        val job = launch {
            capturedPortfolioListFlow(ids.receiveAsFlow(), { flowOf(ready(it)) }) { source ->
                if (source.meta.id == 7L) withContext(NonCancellable) { entered.complete(Unit); release.await() }
                source.meta.name
            }.collect { values += it }
        }
        ids.send(listOf(7)); runCurrent(); assertTrue(entered.isCompleted)
        ids.send(listOf(9)); runCurrent()
        assertEquals(listOf(9L), values.last().map { it.metaId })
        assertNull(values.last().single().portfolio.detail)
        val boundary = values.size
        release.complete(Unit); runCurrent(); ids.close(); job.join()
        assertTrue(values.drop(boundary).flatMap { it }.none { it.metaId == 7L || it.portfolio.detail == "Wallet 7" })
        assertEquals("Wallet 9", values.last().single().portfolio.detail)
    }

    @Test fun sameWalletMetadataAndCurrencyRefreshWithoutNewRosterSubscription() = runTest {
        val first = ready(7); val second = ready(7, "New name", "jpy")
        val balance = MutableStateFlow<TotalBalance>(first); var calls = 0
        val values = mutableListOf<List<CapturedWalletPortfolio<String>>>()
        val job = launch {
            capturedPortfolioListFlow(flowOf(listOf(7L)), { calls++; balance }) { it.meta.name }.collect { values += it }
        }
        runCurrent(); balance.value = second; runCurrent()
        assertEquals(1, calls); assertSame(second, values.last().single().portfolio.source)
        assertEquals("New name", values.last().single().portfolio.detail)
        assertEquals("jpy", (values.last().single().portfolio.source as TotalBalance.Ready).currencyId)
        job.cancel(); job.join()
    }

    @Test fun wrongWalletReadyCannotGenerateIconOrMoney() = runTest {
        var icons = 0
        val values = capturedPortfolioListFlow(flowOf(listOf(7L)), { flowOf(ready(9)) }) { icons++; "wrong" }.toList()
        assertEquals(0, icons)
        assertSame(TotalBalance.Unavailable, values.last().single().portfolio.source)
        assertNull(values.last().single().portfolio.detail)
    }

    @Test fun wrongWalletLoadingCannotLeakCurrencyContext() = runTest {
        val values = capturedPortfolioListFlow<String>(flowOf(listOf(7L)), { flowOf(TotalBalance.Loading(9, "jpy")) }) { "wrong" }.toList()
        assertSame(TotalBalance.Unavailable, values.last().single().portfolio.source)
    }

    @Test fun matchingLoadingRetainsExactContextWithoutIcon() = runTest {
        val source = TotalBalance.Loading(7, "jpy")
        val values = capturedPortfolioListFlow<String>(flowOf(listOf(7L)), { flowOf(source) }) { error("not Ready") }.toList()
        assertSame(source, values.last().single().portfolio.source)
        assertNull(values.last().single().portfolio.detail)
    }

    @Test fun oneFailedWalletDoesNotEraseOtherWallets() = runTest {
        val good = ready(9)
        val values = capturedPortfolioListFlow(flowOf(listOf(7L, 9L)), { id ->
            if (id == 7L) flow { emit(ready(7)); throw IllegalStateException("wallet read") } else flowOf(good)
        }) { it.meta.name }.toList()
        assertSame(TotalBalance.Unavailable, values.last()[0].portfolio.source)
        assertSame(good, values.last()[1].portfolio.source)
        assertEquals("Wallet 9", values.last()[1].portfolio.detail)
    }

    @Test fun failedIconKeepsItsCapturedNameAndValueWithPlaceholder() = runTest {
        val source = ready(7)
        val values = capturedPortfolioListFlow<String>(flowOf(listOf(7L)), { flowOf(source) }) { error("icon read") }.toList()
        assertSame(source, values.last().single().portfolio.source)
        assertNull(values.last().single().portfolio.detail)
    }

    @Test fun duplicateRosterFailsClosed() = runTest {
        val values = mutableListOf<List<CapturedWalletPortfolio<String>>>()
        var observed: Throwable? = null
        try {
            capturedPortfolioListFlow(flowOf(listOf(7L, 7L)), { flowOf(ready(it)) }) { it.meta.name }.collect { values += it }
        } catch (failure: IllegalArgumentException) { observed = failure }
        assertNotNull(observed); assertTrue(values.last().isEmpty())
    }

    @Test fun rosterFailureClearsReadyRowsAndPropagates() = runTest {
        val ids = Channel<List<Long>>(Channel.UNLIMITED)
        val values = mutableListOf<List<CapturedWalletPortfolio<String>>>(); var failure: Throwable? = null
        val job = launch {
            try { capturedPortfolioListFlow(ids.receiveAsFlow(), { flowOf(ready(it)) }) { it.meta.name }.collect { values += it } }
            catch (caught: IllegalStateException) { failure = caught }
        }
        ids.send(listOf(7)); runCurrent(); assertEquals("Wallet 7", values.last().single().portfolio.detail)
        ids.close(IllegalStateException("roster read")); job.join()
        assertNotNull(failure); assertTrue(values.last().isEmpty())
    }

    @Test fun reorderingEndsPreviousObserversAndKeepsNewOrder() = runTest {
        val ids = Channel<List<Long>>(Channel.UNLIMITED)
        val calls = mutableListOf<Long>(); val stops = mutableListOf<Long>()
        val values = mutableListOf<List<CapturedWalletPortfolio<String>>>()
        val job = launch {
            capturedPortfolioListFlow(ids.receiveAsFlow(), { id ->
                flow { calls += id; try { emit(ready(id)); CompletableDeferred<Unit>().await() } finally { stops += id } }
            }) { it.meta.name }.collect { values += it }
        }
        ids.send(listOf(7, 9)); runCurrent()
        ids.send(listOf(9, 7)); runCurrent()
        assertEquals(listOf(7L, 9L, 9L, 7L), calls)
        assertEquals(setOf(7L, 9L), stops.toSet()); assertEquals(2, stops.size)
        assertEquals(listOf(9L, 7L), values.last().map { it.metaId })
        job.cancel(); job.join(); assertEquals(4, stops.size)
    }

    @Test fun synchronousObserverFactoryFailureOnlyRevokesItsWallet() = runTest {
        val good = ready(9)
        val values = capturedPortfolioListFlow(flowOf(listOf(7L, 9L)), { id ->
            if (id == 7L) throw IllegalStateException("factory read") else flowOf(good)
        }) { it.meta.name }.toList()
        assertSame(TotalBalance.Unavailable, values.last()[0].portfolio.source)
        assertSame(good, values.last()[1].portfolio.source)
        assertEquals("Wallet 9", values.last()[1].portfolio.detail)
    }

    @Test fun observerFactoryCancellationTerminatesAndRevokesRoster() = runTest {
        val values = mutableListOf<List<CapturedWalletPortfolio<String>>>()
        var factoryCalls = 0; var observed: CancellationException? = null
        try {
            withTimeout(1_000) {
                capturedPortfolioListFlow<String>(flowOf(listOf(7L)), {
                    factoryCalls++; throw CancellationException("factory cancelled")
                }) { "icon" }.collect { values += it }
            }
        } catch (failure: CancellationException) { observed = failure }
        assertEquals(1, factoryCalls)
        assertEquals("factory cancelled", observed?.message)
        assertTrue(values.last().isEmpty())
    }

    @Test fun activeRowCancellationTerminatesAndCleansSiblingObserver() = runTest {
        val started = CompletableDeferred<Unit>(); var siblingStops = 0
        val values = mutableListOf<List<CapturedWalletPortfolio<String>>>()
        var observed: CancellationException? = null
        try {
            withTimeout(1_000) {
                capturedPortfolioListFlow(flowOf(listOf(9L, 7L)), { id ->
                    if (id == 9L) flow {
                        started.complete(Unit)
                        try { emit(ready(9)); CompletableDeferred<Unit>().await() } finally { siblingStops++ }
                    } else flow {
                        started.await(); emit(ready(7)); throw CancellationException("wallet observer cancelled")
                    }
                }) { it.meta.name }.collect { values += it }
            }
        } catch (failure: CancellationException) { observed = failure }
        assertTrue(started.isCompleted); assertEquals(1, siblingStops)
        assertEquals("wallet observer cancelled", observed?.message)
        assertTrue(values.last().isEmpty())
    }

    private fun ready(id: Long, name: String = "Wallet $id", currency: String = "usd"): TotalBalance.Ready {
        val key = ByteArray(32) { id.toByte() }
        val meta = MetaAccount(id, emptyMap(), emptyMap(), key, CryptoType.SR25519, key,
            null, null, null, true, true, null, name, id == 7L)
        val value = PortfolioBalance(BigDecimal.TEN, BigDecimal.TEN, null, null, 1, 1, 0, 0)
        val hidden = PortfolioBalance(BigDecimal.ZERO, BigDecimal.ZERO, null, null, 0, 0, 0, 0)
        return TotalBalance.Ready(meta, emptyMap(), currency, "$", true, value, hidden, value)
    }
}

