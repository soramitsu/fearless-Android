package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.staking.impl.domain.validators.current.search.SearchCustomBlockProducerInteractor.BlockProducer
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchBlockProducersState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

class ProducerSearchScreenOwnershipTest {
    @get:Rule val environment: TestRule = RuleChain.outerRule(SearchContractEnvironment()).around(ProducerScreenArchitectureRule())

    @Test fun selectionUpdatesRefreshRowsWithoutLosingTheCurrentQuery() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            f.search("al")
            f.data.readyRelay(listOf(f.data.validators[1]))
            val state = f.await {
                it is SearchBlockProducersState.Success && it.blockProducers.any { row -> row.address == f.addresses[1] && row.selected }
            } as SearchBlockProducersState.Success
            assertEquals("al", f.model.enteredQuery.value)
            assertFalse(state.blockProducers[0].selected)
            assertTrue(state.blockProducers[1].selected)
        } finally {
            f.close()
        }
    }

    @Test fun addingASelectionKeepsTheOtherSelectedResult() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            f.search("al")
            f.data.readyRelay(f.data.validators)
            val state = f.await {
                it is SearchBlockProducersState.Success && it.blockProducers.count { row -> row.selected } == 2
            } as SearchBlockProducersState.Success
            assertEquals(f.addresses.take(2), state.blockProducers.map { it.address })
        } finally {
            f.close()
        }
    }

    @Test fun aSlowRemoteLookupCannotDelayANewerLocalSearch() = runTest {
        val f = ProducerSearchScreenFixture()
        val reply = CompletableDeferred<BlockProducer?>()
        try {
            f.start()
            f.remoteHandler = { reply.await() }
            f.model.enteredQuery.value = "remote"
            withContext(Dispatchers.Default) { withTimeout(3_000) { f.lookupStarted.receive() } }
            f.model.enteredQuery.value = "Alice"
            val state = f.await {
                it is SearchBlockProducersState.Success && it.blockProducers.singleOrNull()?.address == f.addresses[0]
            } as SearchBlockProducersState.Success
            assertEquals("Alice", state.blockProducers.single().name)
            assertFalse(reply.isCompleted)
        } finally {
            f.close()
        }
    }

    @Test fun closingTheScreenCancelsItsPendingRemoteLookup() = runTest {
        val f = ProducerSearchScreenFixture()
        val reply = CompletableDeferred<BlockProducer?>()
        val cancelled = CompletableDeferred<Unit>()
        try {
            f.start()
            f.remoteHandler = {
                try {
                    reply.await()
                } catch (error: CancellationException) {
                    cancelled.complete(Unit)
                    throw error
                }
            }
            f.model.enteredQuery.value = "remote"
            withContext(Dispatchers.Default) { withTimeout(3_000) { f.lookupStarted.receive() } }
            f.close()
            withContext(Dispatchers.Default) { withTimeout(3_000) { cancelled.await() } }
            assertFalse(reply.isCompleted)
        } finally {
            f.close()
        }
    }
}
