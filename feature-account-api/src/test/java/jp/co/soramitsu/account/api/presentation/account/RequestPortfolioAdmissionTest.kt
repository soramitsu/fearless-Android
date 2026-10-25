package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class RequestPortfolioAdmissionTest {
    @Test fun keepsExactRequestSnapshotInsteadOfGloballySelectedWallet() {
        val request = requestReady(7)
        assertSame(request, admitRequestPortfolio(request, 7, 7) { true })
        assertSame(TotalBalance.Unavailable, admitRequestPortfolio(requestReady(9), 7, 7) { true })
    }
    @Test fun absentRequestBindingRejectsReady() {
        assertSame(TotalBalance.Unavailable, admitRequestPortfolio(requestReady(), null, null) { true })
    }
    @Test fun disappearedBindingRevokesReady() {
        assertSame(TotalBalance.Unavailable, admitRequestPortfolio(requestReady(), 7, null) { true })
    }
    @Test fun changedBindingRejectsEvenMatchingWallet() {
        assertSame(TotalBalance.Unavailable, admitRequestPortfolio(requestReady(), 7, 8) { true })
    }
    @Test fun matchingLoadingRetainsCurrencyAndIdentity() {
        val source = TotalBalance.Loading(7, "jpy")
        assertSame(source, admitRequestPortfolio(source, 7, 7) { error("No Ready") })
    }
    @Test fun anotherWalletLoadingIsUnavailable() {
        assertSame(TotalBalance.Unavailable, admitRequestPortfolio(TotalBalance.Loading(8, "jpy"), 7, 7) { true })
    }
    @Test fun matchingAwaitingAndUnavailableArePreserved() {
        for (source in listOf(TotalBalance.AwaitingSelection, TotalBalance.Unavailable)) {
            assertSame(source, admitRequestPortfolio(source, 7, 7) { error("No Ready") })
        }
    }
    @Test fun missingBindingMakesAwaitingUnavailable() {
        assertSame(TotalBalance.Unavailable, admitRequestPortfolio(TotalBalance.AwaitingSelection, 7, null) { true })
    }
    @Test fun unsupportedRequestNeverAdmitsWalletValue() {
        assertSame(TotalBalance.Unavailable, admitRequestPortfolio(requestReady(), 7, 7) { false })
    }
    @Test fun mismatchedWalletDoesNotInvokeNetworkValidation() {
        var calls = 0
        admitRequestPortfolio(requestReady(8), 7, 7) { calls++; true }
        assertEquals(0, calls)
    }
    @Test fun networkValidationFailureFailsClosed() {
        assertSame(TotalBalance.Unavailable, admitRequestPortfolio(requestReady(), 7, 7) { error("bad address") })
    }
    @Test fun networkValidationCancellationPropagatesUnchanged() {
        val expected = CancellationException("cancel validation")
        try { admitRequestPortfolio(requestReady(), 7, 7) { throw expected }; fail() }
        catch (actual: CancellationException) { assertSame(expected, actual) }
    }
    @Test fun sameIdMetadataRevisionIsNotCollapsed() {
        val first = requestReady(name = "First")
        val second = requestReady(name = "Replacement")
        assertSame(first, admitRequestPortfolio(first, 7, 7) { true })
        assertSame(second, admitRequestPortfolio(second, 7, 7) { true })
        assertNotSame(first, second)
    }
    @Test fun confirmationRequiresExactCapturedInstance() {
        val source = requestReady()
        assertTrue(isCurrentRequestPortfolio(source, source, 7, 7))
        assertFalse(isCurrentRequestPortfolio(source, requestReady(), 7, 7))
    }
    @Test fun confirmationRejectsUnavailableAndLoading() {
        val source = requestReady()
        assertFalse(isCurrentRequestPortfolio(source, TotalBalance.Unavailable, 7, 7))
        assertFalse(isCurrentRequestPortfolio(source, TotalBalance.Loading(7, "jpy"), 7, 7))
    }
    @Test fun confirmationRejectsAbsentOrDifferentActionBinding() {
        val source = requestReady()
        assertFalse(isCurrentRequestPortfolio(source, source, 7, null))
        assertFalse(isCurrentRequestPortfolio(source, source, 7, 8))
        assertFalse(isCurrentRequestPortfolio(source, source, null, 7))
        assertFalse(isCurrentRequestPortfolio(source, source, 8, 8))
    }
    @Test fun invalidationClearsWhileOldIconCleanupRemainsBlocked() = runTest {
        val source = requestReady()
        val balances = Channel<Pair<TotalBalance, Long?>>(Channel.UNLIMITED)
        val entered = CompletableDeferred<Unit>()
        val cleanup = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val values = mutableListOf<CapturedPortfolioDetail<String>>()
        val job = launch {
            capturedPortfolioDetailFlow(balances.receiveAsFlow().map { (value, binding) ->
                admitRequestPortfolio(value, 7, binding) { true }
            }) {
                entered.complete(Unit)
                try { CompletableDeferred<Unit>().await(); "unused" }
                finally { withContext(NonCancellable) { cleanup.complete(Unit); release.await() } }
            }.collect { values += it }
        }
        balances.send(source to 7); runCurrent(); assertTrue(entered.isCompleted)
        balances.send(source to null); runCurrent()
        assertTrue(cleanup.isCompleted); assertFalse(release.isCompleted)
        assertSame(TotalBalance.Unavailable, values.last().source); assertNull(values.last().detail)
        release.complete(Unit); runCurrent(); balances.close(); job.join()
        assertTrue(values.none { it.detail != null })
    }
    @Test fun replacementRejectsLateOldIconAndKeepsNewNameAndCurrencyTogether() = runTest {
        val first = requestReady(name = "Old", currency = "usd")
        val second = requestReady(name = "New", currency = "jpy")
        val balances = Channel<TotalBalance>(Channel.UNLIMITED)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val values = mutableListOf<CapturedPortfolioDetail<String>>()
        val job = launch {
            capturedPortfolioDetailFlow(balances.receiveAsFlow().map { admitRequestPortfolio(it, 7, 7) { true } }) { source ->
                if (source === first) withContext(NonCancellable) { entered.complete(Unit); release.await() }
                source.meta.name
            }.collect { values += it }
        }
        balances.send(first); runCurrent(); assertTrue(entered.isCompleted)
        balances.send(second); runCurrent()
        assertSame(second, values.last().source); assertNull(values.last().detail)
        release.complete(Unit); runCurrent(); balances.close(); job.join()
        assertTrue(values.none { it.detail == "Old" })
        assertEquals("New", values.last().detail)
        assertSame(second, values.last().source)
        assertEquals("jpy", (values.last().source as TotalBalance.Ready).currencyId)
    }
}

private fun requestReady(id: Long = 7, name: String = "Request wallet", currency: String = "usd"): TotalBalance.Ready {
    val key = ByteArray(32) { 1 }
    val meta = MetaAccount(id, emptyMap(), emptyMap(), key, CryptoType.SR25519, key,
        null, null, null, true, true, null, name, true)
    val balance = PortfolioBalance(BigDecimal.TEN, BigDecimal.TEN, null, null, 1, 1, 0, 0)
    val hidden = PortfolioBalance(BigDecimal.ZERO, BigDecimal.ZERO, null, null, 0, 0, 0, 0)
    return TotalBalance.Ready(meta, emptyMap(), currency, "$", true, balance, hidden, balance)
}
