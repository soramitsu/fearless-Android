package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.staking.impl.domain.validators.current.search.SearchCustomBlockProducerInteractor.BlockProducer
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchBlockProducersState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

class ProducerSearchScreenQueryTest {
    @get:Rule val environment: TestRule = RuleChain.outerRule(SearchContractEnvironment()).around(ProducerScreenArchitectureRule())

    @Test fun emptyInputShowsThePromptWithoutRemoteLookup() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            assertSame(SearchBlockProducersState.NoInput, f.model.screenState.value)
            assertTrue(f.lookups.isEmpty())
        } finally {
            f.close()
        }
    }

    @Test fun nameSearchReturnsTheMatchingAddressAndReward() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            val state = f.search("Alice")
            assertEquals(listOf(f.addresses[0]), state.blockProducers.map { it.address })
            assertEquals("12.5%", state.blockProducers.single().rewardsPercent)
            assertTrue(f.lookups.isEmpty())
        } finally {
            f.close()
        }
    }

    @Test fun nameSearchIgnoresLetterCase() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            assertEquals("Alice", f.search("aLiCe").blockProducers.single().name)
        } finally {
            f.close()
        }
    }

    @Test fun partialNameKeepsBothResultsAndTheirOrder() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            val rows = f.search("al").blockProducers
            assertEquals(listOf("Alice", "ALPINE"), rows.map { it.name })
            assertEquals(f.addresses.take(2), rows.map { it.address })
        } finally {
            f.close()
        }
    }

    @Test fun headerReportsTheVisibleResultCount() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            assertEquals("Results: 2", f.search("al").headerTitle)
        } finally {
            f.close()
        }
    }

    @Test fun completeLocalAddressDoesNotStartRemoteSearch() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            assertEquals(f.addresses[1], f.search(f.addresses[1]).blockProducers.single().address)
            assertTrue(f.lookups.isEmpty())
        } finally {
            f.close()
        }
    }

    @Test fun clearingAQueryRestoresTheEmptyInputPrompt() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            f.search("Alice")
            f.model.enteredQuery.value = ""
            assertSame(SearchBlockProducersState.NoInput, f.await { it is SearchBlockProducersState.NoInput })
        } finally {
            f.close()
        }
    }

    @Test fun unknownNameShowsNoResultsAfterLookupMiss() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            f.model.enteredQuery.value = "missing"
            assertEquals("missing", f.lookup())
            assertSame(SearchBlockProducersState.NoResults, f.await { it is SearchBlockProducersState.NoResults })
        } finally {
            f.close()
        }
    }

    @Test fun remoteSs58LookupPreservesTheExactCaseOfTheEnteredAddress() = runTest {
        val f = ProducerSearchScreenFixture()
        val address = ByteArray(32) { 4 }.toAddress(42)
        try {
            f.start()
            f.model.enteredQuery.value = address
            assertEquals(address, f.lookup())
        } finally {
            f.close()
        }
    }

    @Test fun remoteResultKeepsItsReturnedAddressAndReward() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            f.remoteResult = BlockProducer("Remote", "remote-address", "4%")
            val result = f.search("remote").blockProducers.single()
            assertEquals("remote-address", result.address)
            assertEquals("Remote", result.name)
            assertEquals("4%", result.rewardsPercent)
            assertFalse(result.selected)
        } finally {
            f.close()
        }
    }

    @Test fun renderedRowsKeepTheExistingSelectedAddresses() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            val rows = f.search("al").blockProducers
            assertTrue(rows[0].selected)
            assertFalse(rows[1].selected)
        } finally {
            f.close()
        }
    }

    @Test fun renderedRowsKeepTheGeneratedIcon() = runTest {
        val f = ProducerSearchScreenFixture(true)
        try {
            f.start()
            assertSame(f.icon, f.search("Alice").blockProducers.single().image)
        } finally {
            f.close()
        }
    }
}
