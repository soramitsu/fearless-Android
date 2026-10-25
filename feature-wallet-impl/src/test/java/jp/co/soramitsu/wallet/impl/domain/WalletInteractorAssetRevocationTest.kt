package jp.co.soramitsu.wallet.impl.domain

import io.mockk.*
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletInteractorAssetRevocationTest {
    // A marker prevents coroutine stack-trace recovery from cloning the injected identity.
    private class ReadCancelled(message: String, val marker: Any) : CancellationException(message)
    private class ReadFailure(val marker: Any) : RuntimeException("public read failure")
    private fun wallet(id: Long): MetaAccount = mockk { every { this@mockk.id } returns id }
    private val old = mockk<AssetWithStatus>(name = "old public holding")
    private val replacement = mockk<AssetWithStatus>(name = "replacement public holding")

    private class Fixture {
        val accounts = mockk<AccountRepository>()
        val repository = mockk<WalletRepository>()
        val interactor = WalletInteractorImpl(
            walletRepository = repository, addressBookRepository = mockk(), accountRepository = accounts,
            historyRepository = mockk(), chainRegistry = mockk(), fileProvider = mockk(), preferences = mockk(),
            selectedFiat = mockk(), xcmEntitiesFetcher = mockk(), chainsRepository = mockk(),
            networkStateService = mockk(), tokenRepository = mockk(), coroutineContext = Dispatchers.Unconfined
        )
    }
    private fun values(f: Fixture, withIdentity: Boolean): Flow<List<AssetWithStatus>> =
        if (withIdentity) f.interactor.assetsFlowAndAccount().map { it.second } else f.interactor.assetsFlow()

    @Test fun bothPublicObserversPreserveEmptyRevocationAndReplacement() = runTest {
        for (withIdentity in listOf(false, true)) {
            val f = Fixture(); val selected = wallet(7)
            every { f.accounts.selectedMetaAccountFlow() } returns flowOf(selected)
            every { f.repository.assetsFlow(selected) } returns flowOf(listOf(old), emptyList(), listOf(replacement))
            assertEquals(listOf(emptyList(), listOf(old), emptyList(), listOf(replacement)), values(f, withIdentity).toList())
        }
    }

    @Test fun deletionAndCompletedEmptyWalletDoNotRetainLastHolding() = runTest {
        for (withIdentity in listOf(false, true)) {
            val f = Fixture(); val selected = wallet(7)
            every { f.accounts.selectedMetaAccountFlow() } returns flowOf(selected)
            every { f.repository.assetsFlow(selected) } returns flowOf(listOf(old), emptyList())
            val actual = values(f, withIdentity).toList()
            assertEquals(listOf(old), actual[1]); assertTrue(actual.last().isEmpty())
        }
    }

    @Test fun ordinaryWalletFailureRevokesAndRetainsExactCause() = runTest {
        for (withIdentity in listOf(false, true)) {
            val f = Fixture(); val selected = wallet(7); val injected = ReadFailure(Any())
            every { f.accounts.selectedMetaAccountFlow() } returns flowOf(selected)
            every { f.repository.assetsFlow(selected) } returns flow { emit(listOf(old)); throw injected }
            val seen = mutableListOf<List<AssetWithStatus>>()
            val failure = runCatching { values(f, withIdentity).collect { seen += it } }.exceptionOrNull()
            assertSame(injected, failure); assertTrue(seen.last().isEmpty())
        }
    }

    @Test fun synchronousWalletFactoryFailureAlsoClearsCurrentWallet() = runTest {
        val f = Fixture(); val selected = wallet(7); val injected = ReadFailure(Any())
        every { f.accounts.selectedMetaAccountFlow() } returns flowOf(selected)
        every { f.repository.assetsFlow(selected) } throws injected
        val seen = mutableListOf<Pair<Long, List<AssetWithStatus>>>()
        val failure = runCatching { f.interactor.assetsFlowAndAccount().collect { seen += it } }.exceptionOrNull()
        assertSame(injected, failure); assertEquals(7L, seen.last().first); assertTrue(seen.last().second.isEmpty())
    }

    @Test fun independentWalletCancellationTerminatesBothObserversWithExactReason() = runTest {
        for (withIdentity in listOf(false, true)) {
            val f = Fixture(); val selected = wallet(7); val injected = ReadCancelled("wallet read cancelled", Any())
            var cleaned = false
            every { f.accounts.selectedMetaAccountFlow() } returns flowOf(selected)
            every { f.repository.assetsFlow(selected) } returns flow {
                try { emit(listOf(old)); throw injected } finally { cleaned = true }
            }
            val seen = mutableListOf<List<AssetWithStatus>>()
            val result = withTimeout(1_000) {
                runCatching { values(f, withIdentity).collect { seen += it } }.exceptionOrNull()
            }
            assertSame(injected, result); assertFalse(result is TimeoutCancellationException)
            assertTrue(cleaned); assertTrue(seen.last().isEmpty())
        }
    }

    @Test fun selfCancelledWalletJobTerminatesBothObserversAndRevokesLastValue() = runTest {
        for (withIdentity in listOf(false, true)) {
            val f = Fixture(); val selected = wallet(7); val injected = ReadCancelled("wallet job cancelled itself", Any())
            var cleaned = false
            every { f.accounts.selectedMetaAccountFlow() } returns flowOf(selected)
            every { f.repository.assetsFlow(selected) } returns flow {
                try {
                    emit(listOf(old))
                    currentCoroutineContext().cancel(injected)
                    awaitCancellation()
                } finally { cleaned = true }
            }
            val seen = mutableListOf<List<AssetWithStatus>>()
            val result = withTimeout(1_000) {
                runCatching { values(f, withIdentity).collect { seen += it } }.exceptionOrNull()
            }
            assertSame(injected, result)
            assertTrue(cleaned)
            assertTrue(seen.last().isEmpty())
        }
    }

    @Test fun walletReplacementRevokesBeforeBlockedOldCleanupCompletes() = runTest {
        val f = Fixture(); val first = wallet(7); val second = wallet(8)
        val selections = MutableSharedFlow<MetaAccount>(replay = 1)
        val cleanupStarted = CompletableDeferred<Unit>(); val releaseCleanup = CompletableDeferred<Unit>()
        var replacementStarted = false
        every { f.accounts.selectedMetaAccountFlow() } returns selections
        every { f.repository.assetsFlow(first) } returns flow {
            emit(listOf(old))
            try { awaitCancellation() } finally { withContext(NonCancellable) { cleanupStarted.complete(Unit); releaseCleanup.await() } }
        }
        every { f.repository.assetsFlow(second) } returns flow { replacementStarted = true; emit(listOf(replacement)) }
        val seen = mutableListOf<Pair<Long, List<AssetWithStatus>>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            f.interactor.assetsFlowAndAccount().collect { seen += it }
        }
        selections.emit(first); runCurrent()
        assertEquals(listOf(old), seen.last().second)
        selections.emit(second); runCurrent()
        assertTrue(cleanupStarted.isCompleted)
        assertEquals(8L to emptyList<AssetWithStatus>(), seen.last())
        assertFalse(replacementStarted)
        releaseCleanup.complete(Unit); runCurrent()
        assertEquals(8L to listOf(replacement), seen.last())
        job.cancelAndJoin()
    }

    @Test fun sameIdWalletRevisionStillRevokesBeforeReplacement() = runTest {
        val f = Fixture(); val first = wallet(7); val second = wallet(7)
        val selections = MutableSharedFlow<MetaAccount>(replay = 1)
        every { f.accounts.selectedMetaAccountFlow() } returns selections
        every { f.repository.assetsFlow(first) } returns flow { emit(listOf(old)); awaitCancellation() }
        every { f.repository.assetsFlow(second) } returns flowOf(listOf(replacement))
        val seen = mutableListOf<List<AssetWithStatus>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.interactor.assetsFlow().collect { seen += it } }
        selections.emit(first); runCurrent(); selections.emit(second); runCurrent()
        assertEquals(listOf(emptyList(), listOf(old), emptyList(), listOf(replacement)), seen)
        job.cancelAndJoin()
    }

    @Test fun sourceFailureRevokesBeforeBlockedWalletCleanupAndThenPropagates() = runTest {
        val f = Fixture(); val selected = wallet(7); val injected = ReadFailure(Any())
        val failSelection = CompletableDeferred<Unit>(); val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        every { f.accounts.selectedMetaAccountFlow() } returns flow { emit(selected); failSelection.await(); throw injected }
        every { f.repository.assetsFlow(selected) } returns flow {
            emit(listOf(old))
            try { awaitCancellation() } finally { withContext(NonCancellable) { cleanupStarted.complete(Unit); releaseCleanup.await() } }
        }
        val seen = mutableListOf<List<AssetWithStatus>>()
        val result = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            runCatching { f.interactor.assetsFlow().collect { seen += it } }.exceptionOrNull()
        }
        runCurrent(); assertEquals(listOf(old), seen.last())
        failSelection.complete(Unit); runCurrent()
        assertTrue(cleanupStarted.isCompleted); assertTrue(seen.last().isEmpty()); assertFalse(result.isCompleted)
        releaseCleanup.complete(Unit); runCurrent()
        assertSame(injected, result.await()); assertTrue(seen.last().isEmpty())
    }

    @Test fun normalCompletionWaitsForLastWalletPublication() = runTest {
        val f = Fixture(); val selected = wallet(7); val release = CompletableDeferred<Unit>()
        every { f.accounts.selectedMetaAccountFlow() } returns flowOf(selected)
        every { f.repository.assetsFlow(selected) } returns flow { release.await(); emit(listOf(replacement)) }
        val result = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { f.interactor.assetsFlow().toList() }
        runCurrent(); assertFalse(result.isCompleted)
        release.complete(Unit); runCurrent()
        assertEquals(listOf(emptyList(), listOf(replacement)), result.await())
    }

    @Test fun collectorCancellationRetainsReasonAndCleansWalletWithoutAnotherEmission() = runTest {
        val f = Fixture(); val selected = wallet(7); var cleaned = false
        val injected = CancellationException("caller teardown")
        every { f.accounts.selectedMetaAccountFlow() } returns flowOf(selected)
        every { f.repository.assetsFlow(selected) } returns flow {
            emit(listOf(old)); try { awaitCancellation() } finally { cleaned = true }
        }
        val seen = mutableListOf<List<AssetWithStatus>>()
        var completion: Throwable? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.interactor.assetsFlow().collect { seen += it } }
        job.invokeOnCompletion { completion = it }
        runCurrent(); val size = seen.size; job.cancel(injected); job.join()
        assertSame(injected, completion); assertTrue(cleaned); assertEquals(size, seen.size)
    }

    @Test fun downstreamFailureIsNotCaughtOrFollowedByARevocationEmission() = runTest {
        val f = Fixture(); val selected = wallet(7); val injected = ReadFailure(Any())
        every { f.accounts.selectedMetaAccountFlow() } returns flowOf(selected)
        every { f.repository.assetsFlow(selected) } returns flowOf(listOf(old))
        var calls = 0
        val result = runCatching { f.interactor.assetsFlow().collect { calls++; throw injected } }.exceptionOrNull()
        assertSame(injected, result); assertEquals(1, calls)
    }

    @Test fun separateCollectorsKeepSeparateWalletFailureIdentity() = runTest {
        val f = Fixture(); val first = wallet(7); val second = wallet(8)
        var subscription = 0
        every { f.accounts.selectedMetaAccountFlow() } answers { flowOf(if (subscription++ == 0) first else second) }
        every { f.repository.assetsFlow(first) } returns flowOf(listOf(old))
        every { f.repository.assetsFlow(second) } returns flowOf(listOf(replacement))
        val flow = f.interactor.assetsFlowAndAccount()
        assertEquals(listOf(7L, 7L), flow.toList().map { it.first })
        assertEquals(listOf(8L, 8L), flow.toList().map { it.first })
    }
}
