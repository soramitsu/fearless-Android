package jp.co.soramitsu.account.impl.presentation.profile

import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapturedProfileIconFlowTest {
    @Test fun initialAndLoadingHaveNoIconOrResolutionRequest() = runTest {
        withFixture { f ->
            assertSame(TotalBalance.AwaitingSelection, f.events.last().source)
            val loading = TotalBalance.Loading(7, "cad")
            f.balances.send(loading); runCurrent()
            assertSame(loading, f.events.last().source)
            assertTrue(f.events.all { it.icon == null }); assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun capturedMetaAndReadyIdentityOwnTheGeneratedIcon() = runTest {
        withFixture { f ->
            val source = ready(); f.balances.send(source); runCurrent()
            assertSame(source, f.events.last().source); assertNull(f.events.last().icon)
            assertSame(source.meta, f.requests.single().meta)
            val icon = Any(); f.requests.single().answer.complete(icon); runCurrent()
            assertSame(icon, f.events.last().icon); assertSame(source, f.events.last().source)
        }
    }

    @Test fun sameWalletIdWithTonOnlyReplacementRegeneratesTheIcon() = runTest {
        withFixture { f ->
            val first = ready(meta(1)); val second = ready(meta(2)); assertEquals(first.meta, second.meta)
            f.balances.send(first); runCurrent(); f.requests[0].answer.complete("old"); runCurrent()
            f.balances.send(second); runCurrent()
            assertSame(second, f.events.last().source); assertNull(f.events.last().icon)
            assertSame(second.meta, f.requests[1].meta)
            f.requests[1].answer.complete("new"); runCurrent(); assertEquals("new", f.events.last().icon)
        }
    }

    @Test fun sameSymbolCurrencyReplacementKeepsTheExactNewSource() = runTest {
        withFixture { f ->
            val first = ready(currency = "usd"); val second = ready(currency = "cad")
            f.balances.send(first); runCurrent(); f.requests[0].answer.complete("usd"); runCurrent()
            f.balances.send(second); runCurrent()
            assertSame(second, f.events.last().source); assertNull(f.events.last().icon)
            f.requests[1].answer.complete("cad"); runCurrent()
            assertSame(second, f.events.last().source); assertEquals("cad", f.events.last().icon)
        }
    }

    @Test fun deliveredABAUsesIndependentPublisherGenerations() = runTest {
        withFixture { f ->
            val first = ready(); val second = ready(currency = "eur")
            f.balances.send(first); runCurrent(); f.requests[0].returnAfterCancellation = "late-a"
            f.balances.send(second); runCurrent(); f.requests[1].returnAfterCancellation = "late-b"
            f.balances.send(first); runCurrent()
            assertEquals(3, f.requests.size); assertTrue(f.events.all { it.icon == null })
            f.requests[2].answer.complete("current-a"); runCurrent()
            assertEquals(listOf("current-a"), f.events.mapNotNull { it.icon })
            assertSame(first, f.events.last().source)
        }
    }

    @Test fun loadingRemovalAndUnavailableRevokeTheLastIcon() = runTest {
        for (next in listOf(TotalBalance.Loading(7, "eur"), TotalBalance.AwaitingSelection, TotalBalance.Unavailable)) {
            withFixture { f ->
                f.balances.send(ready()); runCurrent(); f.requests.single().answer.complete("old"); runCurrent()
                f.balances.send(next); runCurrent()
                assertSame(next, f.events.last().source); assertNull(f.events.last().icon)
                assertEquals(1, f.requests.size)
            }
        }
    }

    @Test fun unknownMoneyDoesNotBlockCapturedAccountIcon() = runTest {
        withFixture { f ->
            val source = ready(amount = null); f.balances.send(source); runCurrent()
            f.requests.single().answer.complete("icon"); runCurrent()
            assertSame(source, f.events.last().source); assertEquals("icon", f.events.last().icon)
            assertNull(source.shown.amount)
        }
    }

    @Test fun replacementRevokesBeforeBlockedNonCancellableCleanup() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent()
            val old = f.requests.single(); val gate = CompletableDeferred<Unit>(); old.cleanupGate = gate
            val next = ready(currency = "eur"); f.balances.send(next); runCurrent()
            assertTrue(old.cancellationEntered.isCompleted); assertFalse(old.finished.isCompleted)
            assertSame(next, f.events.last().source); assertNull(f.events.last().icon)
            assertEquals(1, f.requests.size)
            gate.complete(Unit); runCurrent(); assertEquals(2, f.requests.size)
            f.requests[1].answer.complete("new"); runCurrent(); assertEquals("new", f.events.last().icon)
        }
    }

    @Test fun cancelledResolverReturningAnIconCannotPublishIt() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent()
            val old = f.requests.single(); val gate = CompletableDeferred<Unit>()
            old.cleanupGate = gate; old.returnAfterCancellation = "stale"
            f.balances.send(TotalBalance.Unavailable); runCurrent()
            assertSame(TotalBalance.Unavailable, f.events.last().source); assertNull(f.events.last().icon)
            gate.complete(Unit); runCurrent()
            assertTrue(old.finished.isCompleted); assertTrue(f.events.all { it.icon == null })
        }
    }

    @Test fun oldPublicIconSendIsCancelledWhileConsumerIsPaused() = runTest {
        withFixture { f ->
            val paused = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            f.pauseNext = paused to release
            f.balances.send(ready()); runCurrent(); assertTrue(paused.isCompleted)
            f.requests.single().answer.complete("queued-old"); runCurrent()
            val marker = f.events.size; val next = ready(currency = "eur")
            f.balances.send(next); runCurrent(); release.complete(Unit); runCurrent()
            assertTrue(f.events.drop(marker).none { it.icon == "queued-old" })
            assertSame(next, f.events.last().source); assertNull(f.events.last().icon)
            f.requests[1].answer.complete("new"); runCurrent()
            assertEquals(listOf("new"), f.events.mapNotNull { it.icon })
        }
    }

    @Test fun ordinaryResolverFailureKeepsBalanceAndAllowsNextSource() = runTest {
        withFixture { f ->
            val source = ready(); f.balances.send(source); runCurrent()
            f.requests.single().answer.completeExceptionally(IllegalArgumentException("bad-icon")); runCurrent()
            assertSame(source, f.events.last().source); assertNull(f.events.last().icon)
            assertFalse(f.failure.isCompleted)
            f.balances.send(ready(currency = "eur")); runCurrent()
            f.requests[1].answer.complete("next"); runCurrent(); assertEquals("next", f.events.last().icon)
        }
    }

    @Test fun resolverCancellationIsNotConvertedToACompletion() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent(); val marker = f.events.size
            f.requests.single().answer.completeExceptionally(CancellationException("icon-cancel")); runCurrent()
            assertEquals(marker, f.events.size); assertFalse(f.failure.isCompleted)
        }
    }

    @Test fun inputFailureRevokesBeforeBlockedCleanupAndThenPreservesFailureIdentity() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent()
            val old = f.requests.single(); val gate = CompletableDeferred<Unit>()
            old.cleanupGate = gate; old.returnAfterCancellation = "late"
            val failure = InputFailure(Any()); f.balances.close(failure); runCurrent()
            assertSame(TotalBalance.Unavailable, f.events.last().source); assertNull(f.events.last().icon)
            assertTrue(old.cancellationEntered.isCompleted); assertFalse(old.finished.isCompleted)
            assertFalse(f.failure.isCompleted)
            gate.complete(Unit); runCurrent()
            assertSame(failure, f.failure.await()); assertTrue(old.finished.isCompleted)
            assertTrue(f.events.all { it.icon == null })
        }
    }

    @Test fun failureAfterCompletedIconClearsItAndPropagatesUnchanged() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent(); f.requests.single().answer.complete("old"); runCurrent()
            val failure = InputFailure(Any()); f.balances.close(failure); runCurrent()
            assertSame(TotalBalance.Unavailable, f.events.last().source); assertNull(f.events.last().icon)
            assertSame(failure, f.failure.await())
        }
    }

    @Test fun collectorCancellationClosesInputAndChildWithoutUnavailableFallback() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent(); val before = f.events.toList()
            f.job.cancelAndJoin()
            assertTrue(f.closed.isCompleted); assertTrue(f.requests.single().finished.isCompleted)
            assertEquals(before, f.events); assertFalse(f.failure.isCompleted)
            assertTrue(f.events.none { it.source === TotalBalance.Unavailable })
        }
    }

    @Test fun finiteSourceWaitsForItsFinalAsynchronousIcon() = runTest {
        val source = ready(); val answer = CompletableDeferred<Any>(); val values = mutableListOf<CapturedProfileIcon>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            capturedProfileIconFlow(flowOf(source)) { answer.await() }.collect { values += it }
        }
        try {
            runCurrent(); assertFalse(job.isCompleted)
            assertSame(source, values.last().source); assertNull(values.last().icon)
            val icon = Any(); answer.complete(icon); runCurrent()
            assertTrue(job.isCompleted); assertSame(icon, values.last().icon)
        } finally { job.cancelAndJoin() }
    }

    private suspend fun TestScope.withFixture(body: suspend TestScope.(Fixture) -> Unit) {
        val f = Fixture()
        f.job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            try {
                capturedProfileIconFlow(f.balanceFlow()) { meta ->
                    Request(meta).also { f.requests += it }.resolve()
                }.collect { value ->
                    f.events += value
                    f.pauseNext?.let { (paused, release) ->
                        f.pauseNext = null; f.activePause = release
                        paused.complete(Unit); release.await(); f.activePause = null
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (failure: Throwable) { f.failure.complete(failure) }
        }
        try { runCurrent(); body(f) } finally {
            f.pauseNext?.second?.complete(Unit); f.activePause?.complete(Unit)
            f.requests.forEach { it.cleanupGate?.complete(Unit) }
            f.job.cancelAndJoin(); f.balances.close()
        }
    }

    private class Fixture {
        val balances = Channel<TotalBalance>(Channel.UNLIMITED)
        val closed = CompletableDeferred<Unit>()
        val requests = mutableListOf<Request>()
        val events = mutableListOf<CapturedProfileIcon>()
        val failure = CompletableDeferred<Throwable>()
        var pauseNext: Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>? = null
        var activePause: CompletableDeferred<Unit>? = null
        lateinit var job: kotlinx.coroutines.Job
        fun balanceFlow(): Flow<TotalBalance> = flow {
            try { emitAll(balances.receiveAsFlow()) } finally { closed.complete(Unit) }
        }
    }

    private class Request(val meta: MetaAccount) {
        val answer = CompletableDeferred<Any>()
        val cancellationEntered = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        var cleanupGate: CompletableDeferred<Unit>? = null
        var returnAfterCancellation: Any? = null
        suspend fun resolve(): Any = try { answer.await() }
        catch (cancelled: CancellationException) {
            cancellationEntered.complete(Unit)
            withContext(NonCancellable) { cleanupGate?.await() }
            returnAfterCancellation ?: throw cancelled
        } finally { finished.complete(Unit) }
    }

    private class InputFailure(val marker: Any) : RuntimeException("synthetic-icon-input-failure")

    private fun meta(ton: Byte = 1) = MetaAccount(
        7, emptyMap(), emptyMap(), null, CryptoType.SR25519, null, ByteArray(20) { 2 }, null, byteArrayOf(ton),
        true, true, null, "public-icon-fixture", true
    )

    private fun ready(meta: MetaAccount = meta(), currency: String = "cad", amount: BigDecimal? = BigDecimal.TEN): TotalBalance.Ready {
        val partition = PortfolioBalance(amount, amount, null, null, 1, if (amount != null) 1 else 0,
            if (amount == null) 1 else 0, 0)
        return TotalBalance.Ready(meta, emptyMap(), currency, "$", true, partition, partition, partition)
    }
}
