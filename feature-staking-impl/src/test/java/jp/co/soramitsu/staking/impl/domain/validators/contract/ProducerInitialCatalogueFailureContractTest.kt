package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchBlockProducersState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ProducerInitialCatalogueFailureContractTest {
    @get:Rule val environment: TestRule = RuleChain.outerRule(SearchContractEnvironment()).around(ProducerScreenArchitectureRule())

    @Test fun initialCatalogueFailureShowsItsCauseAndStopsTheSpinner() = runTest {
        val f = ProducerCatalogueRetryFixture()
        try {
            f.load = { error("Initial catalogue unavailable") }
            f.start()
            val dialog = f.dialog()
            assertEquals("Initial catalogue unavailable", dialog.message)
            assertEquals("Error", dialog.title)
            assertEquals("Retry", dialog.positiveButtonText)
            val state = f.screen.await { it !is SearchBlockProducersState.Loading }
            assertFalse(state is SearchBlockProducersState.NoResults)
            assertFalse(state is SearchBlockProducersState.NoInput)
            assertFalse(state is SearchBlockProducersState.Success)
            assertTrue(f.screen.model.coroutineContext[Job]?.isActive == true)
        } finally {
            f.close()
        }
    }

    @Test fun messageLessCatalogueFailureUsesTheLocalizedFallback() = runTest {
        val f = ProducerCatalogueRetryFixture()
        val failure = IllegalStateException()
        try {
            f.load = { throw failure }
            f.start()
            assertEquals("Please try again later", f.dialog().message)
        } finally {
            f.close()
        }
    }

    @Test fun queryChangesCannotRetryOrPerformRemoteSearchAgainstAFailedCatalogue() = runTest {
        val f = ProducerCatalogueRetryFixture()
        try {
            f.load = { error("Catalogue unavailable") }
            f.start()
            f.dialog()
            val failureState = f.screen.await { it !is SearchBlockProducersState.Loading }
            val failures = f.calls.get()
            f.screen.model.enteredQuery.value = "Charlie"
            val changedQuery = f.screen.await { it !== failureState }
            assertFalse(changedQuery is SearchBlockProducersState.Success)
            assertEquals(failures, f.calls.get())
            assertEquals("Charlie", f.screen.model.enteredQuery.value)
            verify(f.screen.producer, never()).getBlockProducerByAddress(any())
        } finally {
            f.close()
        }
    }

    @Test fun aCancelledCatalogueRequestDoesNotDisplayAnErrorDialog() = runTest {
        val f = ProducerCatalogueRetryFixture()
        val attempted = CompletableDeferred<Unit>()
        try {
            f.load = {
                attempted.complete(Unit)
                throw CancellationException("Selection changed")
            }
            f.start()
            withContext(Dispatchers.Default) { withTimeout(3_000) { attempted.await() } }
            f.load = { f.screen.catalogue }
            f.screen.data.readyRelay(f.screen.data.validators)
            f.screen.await { it is SearchBlockProducersState.Success }
            assertNull(f.screen.model.errorDialogStateLiveData.value)
            assertNull(f.screen.model.errorLiveData.value)
        } finally {
            f.close()
        }
    }
}
