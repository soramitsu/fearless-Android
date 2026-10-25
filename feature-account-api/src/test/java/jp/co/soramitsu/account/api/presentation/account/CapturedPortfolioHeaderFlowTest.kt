package jp.co.soramitsu.account.api.presentation.account

import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapturedPortfolioHeaderFlowTest {
    @Test fun initialAndLoadingNeverResolveAnAddress() = runTest {
        withFixture { f ->
            assertSame(TotalBalance.AwaitingSelection, f.events.last().source)
            f.balances.send(TotalBalance.Loading(7, "cad")); runCurrent()
            assertEquals(TotalBalance.Loading(7, "cad"), f.events.last().source)
            assertTrue(f.events.all { it.address == null })
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun completedBalanceIsVisibleWhileItsCapturedAddressIsPending() = runTest {
        withFixture { f ->
            val chain = chain("a"); val meta = meta(); val source = ready(meta, mapOf("a" to chain))
            f.balances.send(source); runCurrent()
            assertSame(source, f.events.last().source); assertNull(f.events.last().address)
            val request = f.requests.single()
            assertSame(meta, request.meta); assertSame(chain, request.chain)
            request.answer.complete("address-a"); runCurrent()
            assertEquals("address-a", f.events.last().address)
            assertSame(source, f.events.last().source)
        }
    }

    @Test fun defaultResolverUsesActualCapturedAccountAndChainWithoutRepository() = runTest {
        val captured = ready(meta(ethereum = ByteArray(20) { 1 }), mapOf("a" to chain("a")))
        assertEquals("0x" + "01".repeat(20), captured.meta.address(captured.chainsById.getValue("a")))
        val values = capturedPortfolioHeaderFlow(flowOf(captured), flowOf("a")).toList()
        assertEquals("0x" + "01".repeat(20), values.last().address)
        assertSame(captured, values.last().source)
    }

    @Test fun allNetworksHasNoAddressOrResolverRequest() = runTest {
        withFixture { f ->
            f.chainIds.send(null); f.balances.send(ready()); runCurrent()
            assertNull(f.events.last().selectedChainId); assertNull(f.events.last().address)
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun missingCapturedChainCannotUseAnotherCatalogOrGlobalFallback() = runTest {
        withFixture { f ->
            val captured = ready(chains = mapOf("b" to chain("b")))
            f.balances.send(captured); runCurrent()
            assertSame(captured, f.events.last().source)
            assertEquals("a", f.events.last().selectedChainId); assertNull(f.events.last().address)
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun equalWalletIdWithTonOnlyReplacementRevokesPriorAddress() = runTest {
        withFixture { f ->
            val first = ready(meta(ton = byteArrayOf(1))); val second = ready(meta(ton = byteArrayOf(2)))
            assertEquals(first.meta, second.meta)
            f.balances.send(first); runCurrent(); f.requests[0].answer.complete("old"); runCurrent()
            f.balances.send(second); runCurrent()
            assertNull(f.events.last().address); assertSame(second, f.events.last().source)
            assertSame(second.meta, f.requests[1].meta)
            f.requests[1].answer.complete("new"); runCurrent()
            assertEquals("new", f.events.last().address)
        }
    }

    @Test fun sameSymbolCurrencyReplacementRetainsTheNewReadyInstance() = runTest {
        withFixture { f ->
            val first = ready(currency = "usd"); val second = ready(currency = "cad")
            f.balances.send(first); runCurrent(); f.requests[0].answer.complete("old"); runCurrent()
            f.balances.send(second); runCurrent()
            assertNull(f.events.last().address); assertSame(second, f.events.last().source)
            f.requests[1].answer.complete("new"); runCurrent()
            assertSame(second, f.events.last().source); assertEquals("cad", second.currencyId)
        }
    }

    @Test fun sameChainIdCatalogReplacementUsesOnlyTheNewCapturedChain() = runTest {
        withFixture { f ->
            val before = chain("a"); val after = chain("a")
            val first = ready(chains = mapOf("a" to before)); val second = ready(chains = mapOf("a" to after))
            f.balances.send(first); runCurrent(); f.requests[0].answer.complete("old"); runCurrent()
            f.balances.send(second); runCurrent()
            assertSame(before, f.requests[0].chain); assertSame(after, f.requests[1].chain)
            assertNull(f.events.last().address); assertSame(second, f.events.last().source)
            f.requests[1].answer.complete("new"); runCurrent()
            assertEquals("new", f.events.last().address)
        }
    }

    @Test fun networkReplacementRevokesWithinTheSameReadyInstance() = runTest {
        withFixture { f ->
            val source = ready(chains = mapOf("a" to chain("a"), "b" to chain("b")))
            f.balances.send(source); runCurrent(); f.requests[0].answer.complete("a"); runCurrent()
            f.chainIds.send("b"); runCurrent()
            assertNull(f.events.last().address); assertSame(source, f.events.last().source)
            assertEquals("b", f.events.last().selectedChainId)
            assertSame(source.chainsById["b"], f.requests[1].chain)
        }
    }

    @Test fun deliveredNetworkABAUsesThreeIndependentPublishers() = runTest {
        withFixture { f ->
            val source = ready(chains = mapOf("a" to chain("a"), "b" to chain("b")))
            f.balances.send(source); runCurrent()
            val first = f.requests.single(); first.returnAfterCancellation = "late-a"
            f.chainIds.send("b"); runCurrent()
            assertEquals(2, f.requests.size); assertTrue(first.finished.isCompleted)
            val second = f.requests[1]; second.returnAfterCancellation = "late-b"
            f.chainIds.send("a"); runCurrent()
            assertEquals(3, f.requests.size); assertTrue(second.finished.isCompleted)
            assertTrue(f.events.all { it.address == null })
            f.requests[2].answer.complete("current-a"); runCurrent()
            assertEquals(listOf("current-a"), f.events.mapNotNull { it.address })
            assertSame(source, f.events.last().source)
        }
    }

    @Test fun loadingRevokesBothOldAddressAndOldReadySource() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent(); f.requests.single().answer.complete("old"); runCurrent()
            val loading = TotalBalance.Loading(7, "eur")
            f.balances.send(loading); runCurrent()
            assertSame(loading, f.events.last().source); assertNull(f.events.last().address)
            assertEquals(1, f.requests.size)
        }
    }

    @Test fun removalAndUnavailableStatesNeverResolveOrRetainAnAddress() = runTest {
        for (state in listOf(TotalBalance.AwaitingSelection, TotalBalance.Unavailable)) {
            withFixture { f ->
                f.balances.send(ready()); runCurrent(); f.requests.single().answer.complete("old"); runCurrent()
                f.balances.send(state); runCurrent()
                assertSame(state, f.events.last().source); assertNull(f.events.last().address)
                assertEquals(1, f.requests.size)
            }
        }
    }

    @Test fun pendingOrUnavailableQuotesDoNotInventOrBlockAccountAddressState() = runTest {
        for (quotesReady in listOf(false, true)) {
            withFixture { f ->
                val source = ready(quotesReady = quotesReady, amount = null)
                f.balances.send(source); runCurrent()
                assertSame(source, f.events.last().source); assertNull(source.shown.amount)
                f.requests.single().answer.complete("captured"); runCurrent()
                assertEquals("captured", f.events.last().address)
                assertSame(source, f.events.last().source)
            }
        }
    }

    @Test fun revocationPrecedesBlockedNonCancellableResolverCleanup() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent()
            val old = f.requests.single(); val gate = CompletableDeferred<Unit>(); old.cleanupGate = gate
            val next = ready(currency = "eur")
            f.balances.send(next); runCurrent()
            assertTrue(old.cancellationEntered.isCompleted); assertFalse(old.finished.isCompleted)
            assertSame(next, f.events.last().source); assertNull(f.events.last().address)
            assertEquals(1, f.requests.size)
            gate.complete(Unit); runCurrent()
            assertTrue(old.finished.isCompleted); assertEquals(2, f.requests.size)
            f.requests[1].answer.complete("new"); runCurrent()
            assertEquals(listOf("new"), f.events.mapNotNull { it.address })
        }
    }

    @Test fun resolverReturningAfterNonCancellableCancellationCannotPublishLateAddress() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent()
            val old = f.requests.single(); val gate = CompletableDeferred<Unit>()
            old.cleanupGate = gate; old.returnAfterCancellation = "stale"
            f.balances.send(TotalBalance.Unavailable); runCurrent()
            assertSame(TotalBalance.Unavailable, f.events.last().source); assertNull(f.events.last().address)
            assertTrue(old.cancellationEntered.isCompleted)
            gate.complete(Unit); runCurrent()
            assertTrue(old.finished.isCompleted); assertTrue(f.events.all { it.address == null })
            assertEquals(1, f.requests.size)
        }
    }

    @Test fun replacementCancelsAnOldPublicAddressSendWhileConsumerIsPaused() = runTest {
        withFixture { f ->
            val paused = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            f.pauseNext = paused to release
            f.balances.send(ready()); runCurrent()
            assertTrue(paused.isCompleted)
            val old = f.requests.single(); old.answer.complete("queued-old"); runCurrent()
            assertTrue(old.finished.isCompleted)
            val marker = f.events.size; val replacement = ready(currency = "eur")
            f.balances.send(replacement); runCurrent()
            release.complete(Unit); runCurrent()
            assertTrue(f.events.drop(marker).none { it.address == "queued-old" })
            assertSame(replacement, f.events.last().source); assertNull(f.events.last().address)
            f.requests[1].answer.complete("new"); runCurrent()
            assertEquals(listOf("new"), f.events.mapNotNull { it.address })
        }
    }

    @Test fun ordinaryAddressFailureKeepsBalanceAndAllowsTheNextSelection() = runTest {
        withFixture { f ->
            val source = ready(chains = mapOf("a" to chain("a"), "b" to chain("b")))
            f.balances.send(source); runCurrent()
            f.requests[0].answer.completeExceptionally(IllegalArgumentException("bad-address")); runCurrent()
            assertFalse(f.failure.isCompleted); assertNull(f.events.last().address)
            assertSame(source, f.events.last().source)
            f.chainIds.send("b"); runCurrent(); f.requests[1].answer.complete("b"); runCurrent()
            assertEquals("b", f.events.last().address)
        }
    }

    @Test fun resolverCancellationIsNotConvertedToAnAddressCompletion() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent(); val marker = f.events.size
            f.requests.single().answer.completeExceptionally(CancellationException("resolver-cancel")); runCurrent()
            assertEquals(marker, f.events.size); assertFalse(f.failure.isCompleted)
            assertTrue(f.requests.single().cancellationEntered.isCompleted)
        }
    }

    @Test fun nullResolverResultNeverBecomesAnEmptyOrForeignAddress() = runTest {
        withFixture { f ->
            val source = ready(); f.balances.send(source); runCurrent()
            f.requests.single().answer.complete(null); runCurrent()
            assertSame(source, f.events.last().source); assertNull(f.events.last().address)
            assertFalse(f.failure.isCompleted)
        }
    }

    @Test fun collectorCancellationClosesBothInputsAndAddressPublisher() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent(); val before = f.events.toList()
            f.job.cancelAndJoin()
            assertTrue(f.balanceClosed.isCompleted); assertTrue(f.chainClosed.isCompleted)
            assertTrue(f.requests.single().finished.isCompleted)
            assertEquals(before, f.events); assertFalse(f.failure.isCompleted)
            assertTrue(f.events.none { it.source === TotalBalance.Unavailable })
        }
    }

    @Test fun balanceInputFailureCancelsResolverAndPropagatesWithoutLateCompletion() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent()
            val old = f.requests.single(); val gate = CompletableDeferred<Unit>()
            old.cleanupGate = gate; old.returnAfterCancellation = "late"
            val failure = InputFailure(Any()); f.balances.close(failure); runCurrent()
            assertTrue(old.cancellationEntered.isCompleted); assertFalse(old.finished.isCompleted)
            assertSame(TotalBalance.Unavailable, f.events.last().source)
            assertNull(f.events.last().selectedChainId); assertNull(f.events.last().address)
            assertFalse(f.failure.isCompleted)
            gate.complete(Unit); runCurrent()
            assertSame(failure, f.failure.await()); assertTrue(old.finished.isCompleted)
            assertTrue(f.chainClosed.isCompleted); assertTrue(f.events.all { it.address == null })
        }
    }

    @Test fun chainInputFailureCancelsResolverAndPropagatesItsIdentity() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent()
            val old = f.requests.single(); val gate = CompletableDeferred<Unit>()
            old.cleanupGate = gate; old.returnAfterCancellation = "late"
            val failure = InputFailure(Any()); f.chainIds.close(failure); runCurrent()
            assertSame(TotalBalance.Unavailable, f.events.last().source)
            assertNull(f.events.last().address); assertFalse(f.failure.isCompleted)
            assertTrue(old.cancellationEntered.isCompleted); assertFalse(old.finished.isCompleted)
            gate.complete(Unit); runCurrent()
            assertSame(failure, f.failure.await())
            assertTrue(old.finished.isCompleted); assertTrue(f.balanceClosed.isCompleted)
            assertTrue(f.events.all { it.address == null })
        }
    }

    @Test fun inputFailureAfterCompletedAddressClearsItAndPreservesFailure() = runTest {
        withFixture { f ->
            f.balances.send(ready()); runCurrent(); f.requests.single().answer.complete("old"); runCurrent()
            val failure = InputFailure(Any()); f.balances.close(failure); runCurrent()
            assertSame(TotalBalance.Unavailable, f.events.last().source)
            assertNull(f.events.last().selectedChainId); assertNull(f.events.last().address)
            assertSame(failure, f.failure.await())
        }
    }

    @Test fun finiteInputsWaitForTheFinalAsynchronousAddress() = runTest {
        val source = ready(); val answer = CompletableDeferred<String?>()
        val values = mutableListOf<CapturedPortfolioHeader>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            capturedPortfolioHeaderFlow(flowOf(source), flowOf("a")) { _, _ -> answer.await() }
                .collect { values += it }
        }
        try {
            runCurrent(); assertFalse(job.isCompleted)
            assertSame(source, values.last().source); assertNull(values.last().address)
            answer.complete("final"); runCurrent()
            assertTrue(job.isCompleted); assertEquals("final", values.last().address)
        } finally { job.cancelAndJoin() }
    }

    private suspend fun TestScope.withFixture(body: suspend TestScope.(Fixture) -> Unit) {
        val f = Fixture()
        f.job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            try {
                capturedPortfolioHeaderFlow(f.balanceFlow(), f.chainFlow()) { meta, chain ->
                    Request(meta, chain).also { f.requests += it }.resolve()
                }.collect { value ->
                    f.events += value
                    f.pauseNext?.let { (paused, release) ->
                        f.pauseNext = null; f.activePause = release
                        paused.complete(Unit); release.await(); f.activePause = null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                f.failure.complete(failure)
            }
        }
        try { runCurrent(); body(f) } finally {
            f.pauseNext?.second?.complete(Unit); f.activePause?.complete(Unit)
            f.requests.forEach { it.cleanupGate?.complete(Unit) }
            f.job.cancelAndJoin(); f.balances.close(); f.chainIds.close()
        }
    }

    private class Fixture {
        val balances = Channel<TotalBalance>(Channel.UNLIMITED)
        val chainIds = Channel<ChainId?>(Channel.UNLIMITED)
        val balanceClosed = CompletableDeferred<Unit>()
        val chainClosed = CompletableDeferred<Unit>()
        val requests = mutableListOf<Request>()
        val events = mutableListOf<CapturedPortfolioHeader>()
        val failure = CompletableDeferred<Throwable>()
        var pauseNext: Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>? = null
        var activePause: CompletableDeferred<Unit>? = null
        lateinit var job: kotlinx.coroutines.Job
        init { balances.trySend(TotalBalance.AwaitingSelection); chainIds.trySend("a") }
        fun balanceFlow(): Flow<TotalBalance> = flow {
            try { emitAll(balances.receiveAsFlow()) } finally { balanceClosed.complete(Unit) }
        }
        fun chainFlow(): Flow<ChainId?> = flow {
            try { emitAll(chainIds.receiveAsFlow()) } finally { chainClosed.complete(Unit) }
        }
    }

    private class Request(val meta: MetaAccount, val chain: Chain) {
        val answer = CompletableDeferred<String?>()
        val cancellationEntered = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        var cleanupGate: CompletableDeferred<Unit>? = null
        var returnAfterCancellation: String? = null
        suspend fun resolve(): String? = try {
            answer.await()
        } catch (cancelled: CancellationException) {
            cancellationEntered.complete(Unit)
            withContext(NonCancellable) { cleanupGate?.await() }
            returnAfterCancellation ?: throw cancelled
        } finally { finished.complete(Unit) }
    }

    private class InputFailure(val marker: Any) : RuntimeException("synthetic-header-input-failure")

    private fun chain(name: String): Chain = mockk {
        every { id } returns name
        every { ecosystem } returns Ecosystem.Ethereum
        every { externalApi } returns null
    }

    private fun meta(ton: ByteArray = byteArrayOf(1), ethereum: ByteArray = ByteArray(20) { 2 }) = MetaAccount(
        7, emptyMap(), emptyMap(), null, CryptoType.SR25519, null, ethereum, null, ton,
        true, true, null, "public-header-fixture", true
    )

    private fun ready(meta: MetaAccount = meta(), chains: Map<ChainId, Chain> = mapOf("a" to chain("a")),
                      currency: String = "cad", quotesReady: Boolean = true, amount: BigDecimal? = BigDecimal.TEN): TotalBalance.Ready {
        val partition = PortfolioBalance(amount, amount, null, null, 1, if (amount != null) 1 else 0,
            if (amount == null) 1 else 0, 0)
        return TotalBalance.Ready(meta, chains, currency, "$", quotesReady, partition, partition, partition)
    }
}
