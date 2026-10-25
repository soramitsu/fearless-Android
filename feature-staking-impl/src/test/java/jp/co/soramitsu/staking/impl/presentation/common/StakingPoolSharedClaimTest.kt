package jp.co.soramitsu.staking.impl.presentation.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StakingPoolSharedClaimTest {
    @Test fun `only one attempt owns a publication and its failure permits explicit retry`() {
        val shared = StakingPoolSharedState<String>().apply { set("draft") }
        val review = shared.capture()
        val first = requireNotNull(shared.tryClaim(review))
        assertNull(shared.tryClaim(review))
        shared.release(first)
        val retry = requireNotNull(shared.tryClaim(review))
        shared.release(first)
        assertNull(shared.tryClaim(review))
        shared.release(retry)
        assertNotNull(shared.tryClaim(review))
    }

    @Test fun `old release and success preserve a newer publication and its claim`() {
        val shared = StakingPoolSharedState<String>().apply { set("first") }
        val first = requireNotNull(shared.tryClaim(shared.capture()))
        shared.set("second")
        val secondReview = shared.capture()
        val second = requireNotNull(shared.tryClaim(secondReview))
        shared.release(first)
        assertNull(shared.consume(first))
        assertEquals("second", shared.get())
        assertNull(shared.tryClaim(secondReview))
        val receipt = requireNotNull(shared.consume(second))
        assertTrue(shared.isCurrent(receipt))
        assertNull(shared.get())
        assertNull(shared.tryClaim(receipt))
    }

    @Test fun `validated edits wait for the pending claim and reject old revisions`() {
        val shared = StakingPoolSharedState<String>().apply { set("draft") }
        val review = shared.capture()
        val claim = requireNotNull(shared.tryClaim(review))
        assertFalse(shared.setIfCurrent(review, "edited"))
        assertEquals("draft", shared.get())
        shared.release(claim)
        assertFalse(shared.isEditable(review))
        assertFalse(shared.setIfCurrent(review, "stale pre-submission edit"))
        val freshReview = shared.capture()
        assertTrue(shared.isEditable(freshReview))
        assertTrue(shared.setIfCurrent(freshReview, "edited"))
        assertFalse(shared.setIfCurrent(review, "stale"))
        assertEquals("edited", shared.get())
    }

    @Test fun `completion receipt cannot take ownership of a reentrant observer draft`() = runTest {
        val shared = StakingPoolSharedState<String>().apply { set("draft") }
        val claim = requireNotNull(shared.tryClaim(shared.capture()))
        var newer: StakingPoolSharedState.Claim<String>? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            shared.process.collect { value ->
                if (value == null) {
                    shared.set("observer draft")
                    newer = shared.tryClaim(shared.capture())
                }
            }
        }
        val receipt = requireNotNull(shared.consume(claim))
        assertFalse(shared.isCurrent(receipt))
        assertEquals("observer draft", shared.get())
        assertNotNull(newer)
        shared.release(claim)
        assertNull(shared.tryClaim(shared.capture()))
    }

    @Test fun `reentrant replacement prevents validation from reporting publication ownership`() = runTest {
        val shared = StakingPoolSharedState<String>().apply { set("draft") }
        val review = shared.capture()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            shared.process.collect { value ->
                if (value == "edited") shared.set("observer draft")
            }
        }
        assertFalse(shared.setIfCurrent(review, "edited"))
        assertEquals("observer draft", shared.get())
    }
    @Test fun `completed flow cannot be edited and a new flow has a distinct lineage`() {
        val shared = StakingPoolSharedState<String>().apply { set("draft") }
        val original = shared.capture()
        assertTrue(shared.setIfCurrent(original, "edited"))
        val edited = shared.capture()
        assertTrue(edited.isSameFlowAs(original))
        val receipt = requireNotNull(shared.consume(requireNotNull(shared.tryClaim(edited))))
        assertFalse(shared.isEditable(receipt))
        assertFalse(shared.setIfCurrent(receipt, "resurrected"))
        shared.set("draft")
        assertFalse(shared.capture().isSameFlowAs(original))
    }

}
