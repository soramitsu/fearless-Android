package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchBlockProducersState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

class ProducerCatalogueContextInvalidationTest {
    @get:Rule val environment: TestRule = RuleChain.outerRule(SearchContractEnvironment()).around(ProducerScreenArchitectureRule())

    @Test fun activeContextInvalidationOffersRefreshWithoutWaitingForASelectionChange() = runTest {
        val f = ProducerCatalogueRetryFixture()
        try {
            f.load = { throw CancellationException("The suspended request belongs to the old wallet") }
            f.start()
            f.screen.await { it is SearchBlockProducersState.RefreshRequired }
            val previousSelection = f.screen.data.process.setupStakingProcess.value
            val attempts = f.calls.get()
            assertNull(f.screen.model.errorDialogStateLiveData.value)
            assertNull(f.screen.model.errorLiveData.value)
            assertTrue(f.screen.model.coroutineContext[Job]?.isActive == true)

            f.load = { f.screen.catalogue }
            f.screen.model.retryCatalogue()
            val state = f.screen.await { it is SearchBlockProducersState.Success } as SearchBlockProducersState.Success
            assertEquals(attempts + 1, f.calls.get())
            assertEquals(previousSelection, f.screen.data.process.setupStakingProcess.value)
            assertEquals("al", f.screen.model.enteredQuery.value)
            assertEquals(f.screen.addresses.take(2), state.blockProducers.map { it.address })
            assertEquals(listOf(true, false), state.blockProducers.map { it.selected })
            assertNull(f.screen.model.errorDialogStateLiveData.value)
        } finally {
            f.close()
        }
    }
}
