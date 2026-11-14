package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchBlockProducersState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.concurrent.atomic.AtomicInteger

class ProducerCatalogueRetryContractTest {
    @get:Rule val environment: TestRule = RuleChain.outerRule(SearchContractEnvironment()).around(ProducerScreenArchitectureRule())

    @Test fun retryReloadsTheCatalogueAndPreservesQueryAndSelection() = runTest {
        val f = ProducerCatalogueRetryFixture()
        try {
            f.load = { error("Offline") }
            f.start()
            val dialog = f.dialog()
            val attempts = f.calls.get()
            f.load = { f.screen.catalogue }
            dialog.positiveClick()
            val state = f.screen.await { it is SearchBlockProducersState.Success } as SearchBlockProducersState.Success
            assertEquals(attempts + 1, f.calls.get())
            assertEquals("al", f.screen.model.enteredQuery.value)
            assertEquals(f.screen.addresses.take(2), state.blockProducers.map { it.address })
            assertEquals(listOf(true, false), state.blockProducers.map { it.selected })
            verify(f.screen.router, never()).back()
        } finally {
            f.close()
        }
    }

    @Test fun retryCanRecoverWhenTheFirstFailureCameFromTheSharedComputationalCache() = runTest {
        val f = ProducerCatalogueRetryFixture()
        val cache = ComputationalCache()
        val providerCalls = AtomicInteger()
        try {
            f.load = {
                cache.useCache("catalogue", f.screen.data.lifecycle) {
                    if (providerCalls.incrementAndGet() == 1) error("Provider temporarily unavailable")
                    f.screen.catalogue
                }
            }
            f.start()
            assertEquals("Provider temporarily unavailable", f.dialog().also { it.positiveClick() }.message)
            val state = f.screen.await { it is SearchBlockProducersState.Success } as SearchBlockProducersState.Success
            assertEquals(2, providerCalls.get())
            assertEquals(f.screen.addresses.take(2), state.blockProducers.map { it.address })
        } finally {
            f.close()
        }
    }

    @Test fun aFailedRetryOffersAnotherRetryThatCanSucceed() = runTest {
        val f = ProducerCatalogueRetryFixture()
        try {
            f.load = { error("First failure") }
            f.start()
            val first = f.dialog()
            f.load = { error("Retry still offline") }
            first.positiveClick()
            val second = f.dialog()
            assertEquals("Retry still offline", second.message)
            assertEquals("Retry", second.positiveButtonText)
            f.load = { f.screen.catalogue }
            second.positiveClick()
            f.screen.await { it is SearchBlockProducersState.Success }
        } finally {
            f.close()
        }
    }

    @Test fun pendingRetryShowsLoadingWithoutSearchingAnEmptyCatalogue() = runTest {
        val f = ProducerCatalogueRetryFixture()
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        try {
            f.load = { error("Offline") }
            f.start()
            val dialog = f.dialog()
            val attempts = f.calls.get()
            f.load = {
                started.complete(Unit)
                release.await()
                f.screen.catalogue
            }
            dialog.positiveClick()
            withContext(Dispatchers.Default) { withTimeout(3_000) { started.await() } }
            assertEquals(attempts + 1, f.calls.get())
            f.screen.await { it is SearchBlockProducersState.Loading }
            assertFalse(release.isCompleted)
            verify(f.screen.producer, never()).getBlockProducerByAddress(any())
            release.complete(Unit)
            f.screen.await { it is SearchBlockProducersState.Success }
        } finally {
            release.complete(Unit)
            f.close()
        }
    }

    @Test fun closingTheScreenCancelsItsPendingRetryWithoutAnotherError() = runTest {
        val f = ProducerCatalogueRetryFixture()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        try {
            f.load = { error("Offline") }
            f.start()
            val dialog = f.dialog()
            f.load = {
                started.complete(Unit)
                try {
                    release.await()
                } catch (error: CancellationException) {
                    cancelled.complete(Unit)
                    throw error
                }
                f.screen.catalogue
            }
            dialog.positiveClick()
            withContext(Dispatchers.Default) { withTimeout(3_000) { started.await() } }
            f.close()
            withContext(Dispatchers.Default) { withTimeout(3_000) { cancelled.await() } }
            assertFalse(release.isCompleted)
            assertEquals(dialog, f.screen.model.errorDialogStateLiveData.value?.peekContent())
            assertNull(f.screen.model.errorLiveData.value)
        } finally {
            release.complete(Unit)
            f.close()
        }
    }
}
