package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.validators.current.search.BlockedValidatorException
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchBlockProducersState
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProducerSearchScreenFailureReviewTest {
    @get:Rule val environment: TestRule = RuleChain.outerRule(SearchContractEnvironment()).around(ProducerScreenArchitectureRule())

    @Test fun thrownSelectionFailureIsShownAndLeavesTheScreenOpen() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            whenever(f.producer.blockProducerSelected(any(), any(), any())).doSuspendableAnswer {
                throw IllegalStateException("Candidate no longer exists")
            }
            f.model.blockProducerClicked(f.row())
            assertEquals("Candidate no longer exists", f.model.errorLiveData.value?.peekContent())
            verify(f.router, never()).back()
        } finally {
            f.close()
        }
    }

    @Test fun thrownBlockedSelectionUsesTheSameLocalizedWarningAsReturnedFailure() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            whenever(f.producer.blockProducerSelected(any(), any(), any())).doSuspendableAnswer {
                throw BlockedValidatorException
            }
            f.model.blockProducerClicked(f.row())
            assertEquals("Validator blocked", f.model.errorLiveData.value?.peekContent())
        } finally {
            f.close()
        }
    }

    @Test fun messageLessSelectionFailureUsesTheLocalizedFallback() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            whenever(f.resources.getString(R.string.common_error_general_message)).thenReturn("Please try again later")
            whenever(f.producer.blockProducerSelected(any(), any(), any())).thenReturn(Result.failure(IllegalStateException()))
            f.model.blockProducerClicked(f.row())
            assertEquals("Please try again later", f.model.errorLiveData.value?.peekContent())
        } finally {
            f.close()
        }
    }

    @Test fun returnedCancellationCancelsOnlyItsActionWithoutAnError() = runTest {
        val f = ProducerSearchScreenFixture()
        var action: Job? = null
        try {
            f.start()
            whenever(f.producer.blockProducerSelected(any(), any(), any())).doSuspendableAnswer {
                action = currentCoroutineContext()[Job]
                Result.failure(CancellationException("Screen action cancelled"))
            }
            f.model.blockProducerClicked(f.row())
            assertTrue(action?.isCancelled == true)
            assertTrue(f.model.coroutineContext[Job]?.isActive == true)
            assertNull(f.model.errorLiveData.value)
        } finally {
            f.close()
        }
    }

    @Test fun thrownCancellationDoesNotBecomeASelectionError() = runTest {
        val f = ProducerSearchScreenFixture()
        var action: Job? = null
        try {
            f.start()
            whenever(f.producer.blockProducerSelected(any(), any(), any())).doSuspendableAnswer {
                action = currentCoroutineContext()[Job]
                throw CancellationException("Selection cancelled")
            }
            f.model.blockProducerClicked(f.row())
            assertTrue(action?.isCancelled == true)
            assertNull(f.model.errorLiveData.value)
        } finally {
            f.close()
        }
    }

    @Test fun failedRemoteSearchShowsItsCauseAndTheNextLocalQueryStillWorks() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            f.remoteHandler = { throw IllegalStateException("Validator lookup unavailable") }
            f.model.enteredQuery.value = "remote-missing"
            f.await { it is SearchBlockProducersState.NoResults }
            assertEquals("Validator lookup unavailable", f.model.errorLiveData.value?.peekContent())
            val result = f.search("Alice")
            assertEquals(f.addresses[0], result.blockProducers.single().address)
            assertEquals(listOf("remote-missing"), f.lookups.toList())
        } finally {
            f.close()
        }
    }

    @Test fun clearingInputCancelsRemoteSearchAndRestoresThePrompt() = runTest {
        val f = ProducerSearchScreenFixture()
        val reply = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        try {
            f.start()
            f.remoteHandler = {
                try {
                    reply.await()
                    null
                } catch (error: CancellationException) {
                    cancelled.complete(Unit)
                    throw error
                }
            }
            f.model.enteredQuery.value = "remote"
            withContext(Dispatchers.Default) { withTimeout(3_000) { f.lookupStarted.receive() } }
            f.model.enteredQuery.value = ""
            f.await { it is SearchBlockProducersState.NoInput }
            withContext(Dispatchers.Default) { withTimeout(3_000) { cancelled.await() } }
            assertFalse(reply.isCompleted)
            assertNull(f.model.errorLiveData.value)
        } finally {
            f.close()
        }
    }

    @Test fun detailsFailurePreservesTheCacheAndShowsItsCauseWithoutNavigation() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            val retained = mock<ValidatorDetailsParcelModel>()
            f.details.value = mapOf("retained-account" to retained)
            val before = f.details.value
            whenever(f.producer.navigateBlockProducerInfo(any(), any(), any(), any())).doSuspendableAnswer {
                throw IllegalArgumentException("Validator details unavailable")
            }
            f.model.blockProducerInfoClicked(f.row())
            assertEquals("Validator details unavailable", f.model.errorLiveData.value?.peekContent())
            assertSame(before, f.details.value)
            assertSame(retained, f.details.value["retained-account"])
            verify(f.router, never()).openValidatorDetails(any())
            verify(f.router, never()).openCollatorDetails(any())
        } finally {
            f.close()
        }
    }

    @Test fun detailsCancellationPreservesTheCacheWithoutDisplayingAnError() = runTest {
        val f = ProducerSearchScreenFixture()
        var action: Job? = null
        try {
            f.start()
            val retained = mock<ValidatorDetailsParcelModel>()
            f.details.value = mapOf("retained-account" to retained)
            val before = f.details.value
            whenever(f.producer.navigateBlockProducerInfo(any(), any(), any(), any())).doSuspendableAnswer {
                action = currentCoroutineContext()[Job]
                throw CancellationException("Details cancelled")
            }
            f.model.blockProducerInfoClicked(f.row())
            assertTrue(action?.isCancelled == true)
            assertSame(before, f.details.value)
            assertSame(retained, f.details.value["retained-account"])
            assertNull(f.model.errorLiveData.value)
            verify(f.router, never()).openValidatorDetails(any())
        } finally {
            f.close()
        }
    }
}
