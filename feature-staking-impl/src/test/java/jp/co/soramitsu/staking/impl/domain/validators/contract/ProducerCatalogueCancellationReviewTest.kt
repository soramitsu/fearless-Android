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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger

class ProducerCatalogueCancellationReviewTest {
    @get:Rule val environment: TestRule = RuleChain.outerRule(SearchContractEnvironment()).around(ProducerScreenArchitectureRule())

    @Test fun newerSelectionCancelsPendingCatalogueRefreshAndPublishesCurrentSelectionFlags() = runTest {
        val f = ProducerSearchScreenFixture()
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        try {
            f.start()
            f.search("al")
            whenever(f.producer.getBlockProducers(any())).doSuspendableAnswer {
                if (calls.incrementAndGet() == 1) {
                    started.complete(Unit)
                    try {
                        reply.await()
                    } catch (error: CancellationException) {
                        cancelled.complete(Unit)
                        throw error
                    }
                }
                f.catalogue
            }
            f.data.readyRelay(listOf(f.data.validators[1]))
            withContext(Dispatchers.Default) { withTimeout(3_000) { started.await() } }
            f.data.readyRelay(f.data.validators)
            val state = f.await {
                it is SearchBlockProducersState.Success && it.blockProducers.count { row -> row.selected } == 2
            } as SearchBlockProducersState.Success
            withContext(Dispatchers.Default) { withTimeout(3_000) { cancelled.await() } }
            assertEquals(f.addresses.take(2), state.blockProducers.map { it.address })
            assertEquals("al", f.model.enteredQuery.value)
            assertFalse(reply.isCompleted)
            assertNull(f.model.errorLiveData.value)
        } finally {
            f.close()
        }
    }

    @Test fun contextCancellationDoesNotPoisonTheFollowingCatalogueRefresh() = runTest {
        val f = ProducerSearchScreenFixture()
        val cancelledRequest = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        try {
            f.start()
            f.search("al")
            whenever(f.producer.getBlockProducers(any())).doSuspendableAnswer {
                if (calls.incrementAndGet() == 1) {
                    cancelledRequest.complete(Unit)
                    throw CancellationException("Staking selection context changed")
                }
                f.catalogue
            }
            f.data.readyRelay(listOf(f.data.validators[1]))
            withContext(Dispatchers.Default) { withTimeout(3_000) { cancelledRequest.await() } }
            f.data.readyRelay(f.data.validators)
            val state = f.await {
                it is SearchBlockProducersState.Success && it.blockProducers.count { row -> row.selected } == 2
            } as SearchBlockProducersState.Success
            assertEquals(f.addresses.take(2), state.blockProducers.map { it.address })
            assertTrue(calls.get() >= 2)
            assertTrue(f.model.coroutineContext[Job]?.isActive == true)
            assertNull(f.model.errorLiveData.value)
        } finally {
            f.close()
        }
    }
}
