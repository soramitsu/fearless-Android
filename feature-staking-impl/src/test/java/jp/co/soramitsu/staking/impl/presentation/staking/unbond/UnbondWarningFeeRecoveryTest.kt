package jp.co.soramitsu.staking.impl.presentation.staking.unbond

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.validation.*
import jp.co.soramitsu.staking.impl.domain.validations.unbond.*
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.select.SelectUnbondViewModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*

/** Actual warning callback, fresh fee constructor, route and submit boundaries. */
@OptIn(ExperimentalCoroutinesApi::class)
class UnbondWarningFeeRecoveryTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<UnbondAuthorityFixture>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        owned.forEach { it.releaseAndCancel() }
        try { runBlocking { owned.forEach { it.close() } } } finally { Dispatchers.resetMain() }
    }
    private suspend fun fixture() = UnbondAuthorityFixture(dispatcher, trustedFiat = true)
        .also { owned += it; it.start() }
    private fun warning(f: UnbondAuthorityFixture) {
        val validator = object : Validation<UnbondValidationPayload, UnbondValidationFailure> {
            override suspend fun validate(value: UnbondValidationPayload): ValidationStatus<UnbondValidationFailure> {
                f.validated += value
                return if (value.amount.compareTo(BigDecimal.TEN) == 0) ValidationStatus.Valid()
                else ValidationStatus.NotValid(DefaultFailureLevel.WARNING,
                    UnbondValidationFailure.BondedWillCrossExistential(BigDecimal.TEN))
            }
        }
        val system = ValidationSystem(validator)
        whenever(f.scenario.getUnbondValidationSystem()).thenReturn(system)
    }
    private suspend fun accept(f: UnbondAuthorityFixture, caller: Validatable) {
        f.await { caller.validationFailureEvent.value != null }
        requireNotNull(caller.validationFailureEvent.value).peekContent().confirmWarning()
        dispatcher.scheduler.runCurrent()
    }
    private suspend fun renew(f: UnbondAuthorityFixture) {
        dispatcher.scheduler.advanceTimeBy(501); dispatcher.scheduler.runCurrent()
        f.completeFees()
        f.await { f.fees.feeLiveData.value is FeeStatus.Loaded }
    }

    @Test fun changedSelectAmountRequiresNewFeeAndAnotherActionForRouteAndSubmit() = runBlocking<Unit> {
        for (submit in listOf(false, true)) {
            val f = fixture(); val vm = f.vm as SelectUnbondViewModel
            warning(f); val oldConstructor = f.feeRequests.single()
            f.click(submit); accept(f, vm); f.finishActions()
            assertEquals(0, BigDecimal.TEN.compareTo(vm.enteredAmountFlow.value.toBigDecimal()))
            assertSame(FeeStatus.Loading, f.fees.feeLiveData.value)
            assertFalse(vm.showNextProgress.value == true)
            assertTrue(f.calls.isEmpty()); assertTrue(f.routed.isEmpty()); assertTrue(f.submitted.isEmpty())
            assertNotNull(runCatching { oldConstructor(f.initial.token) }.exceptionOrNull())
            assertTrue(f.calls.isEmpty())
            f.click(submit); f.finishActions()
            assertTrue(f.routed.isEmpty()); assertTrue(f.submitted.isEmpty())
            renew(f)
            assertEquals(2, f.feeRequests.size)
            assertEquals(BigInteger.valueOf(1_000), f.calls.single().amount)
            // Quote completion itself cannot continue the abandoned warning action.
            assertTrue(f.routed.isEmpty()); assertTrue(f.submitted.isEmpty())
            f.click(submit); f.finishActions()
            if (submit) {
                assertEquals(listOf("fixture-hash"), f.submitted)
                assertEquals(2, f.calls.size)
                assertTrue(f.calls.all { it.amount == BigInteger.valueOf(1_000) })
            } else {
                assertEquals(0, BigDecimal.TEN.compareTo(f.routed.single().amount))
                assertTrue(f.submitted.isEmpty())
            }
        }
    }

    @Test fun confirmationWarningReturnsToSelectAndRequiresANewBoundReview() = runBlocking<Unit> {
        val f = fixture(); val select = f.vm as SelectUnbondViewModel
        f.click(submit = false); f.finishActions()
        val oldPayload = f.routed.single(); val confirm = f.confirm(oldPayload)
        f.await { confirm.feeStatusLiveData.value is FeeStatus.Loaded }
        warning(f); confirm.confirmClicked(); accept(f, confirm)
        f.await { select.enteredAmountFlow.value == "10" && confirm.feeStatusLiveData.value === FeeStatus.Error }
        verify(f.router).back()
        assertFalse(confirm.showNextProgress.value == true)
        assertFalse(requireNotNull(oldPayload.review).context.isCurrent())
        assertEquals(1, f.routed.size); assertTrue(f.submitted.isEmpty()); assertTrue(f.calls.isEmpty())
        confirm.confirmClicked()
        f.await { confirm.errorDialogStateLiveData.value != null }
        assertTrue(f.submitted.isEmpty())
        renew(f)
        assertEquals(1, f.routed.size); assertTrue(f.submitted.isEmpty())
        f.click(submit = false); f.finishActions()
        assertEquals(2, f.routed.size)
        val fresh = f.confirm(f.routed.last())
        f.await { fresh.feeStatusLiveData.value is FeeStatus.Loaded }
        fresh.confirmClicked(); f.await { f.submitted.size == 1 }
        assertEquals(listOf("fixture-hash"), f.submitted)
        assertEquals(2, f.calls.size)
        assertTrue(f.calls.all { it.amount == BigInteger.valueOf(1_000) })
    }

    @Test fun staleWarningCannotOverwriteANewerUserAmount() = runBlocking<Unit> {
        val f = fixture(); val select = f.vm as SelectUnbondViewModel
        warning(f); f.click()
        f.await { select.validationFailureEvent.value != null }
        val callback = requireNotNull(select.validationFailureEvent.value).peekContent().confirmWarning
        select.enteredAmountFlow.value = "2"
        dispatcher.scheduler.runCurrent()
        callback(); dispatcher.scheduler.runCurrent(); f.finishActions()
        assertEquals("2", select.enteredAmountFlow.value)
        assertTrue(f.routed.isEmpty()); assertTrue(f.submitted.isEmpty()); assertTrue(f.calls.isEmpty())
    }

    @Test fun invalidAmountClearsActualFiatAndCannotStartFeeOrActionThenRecovers() = runBlocking<Unit> {
        val f = fixture(); val select = f.vm as SelectUnbondViewModel
        f.await { select.enteredFiatAmountFlow.value != null }
        val requests = f.feeRequests.size
        for (input in listOf("0.001", "1.239", "1E-2147483647", "1E+2147483647", "1".repeat(4097))) {
            select.enteredAmountFlow.value = input
            dispatcher.scheduler.advanceTimeBy(501); dispatcher.scheduler.runCurrent()
            f.await { select.enteredFiatAmountFlow.value == null }
            f.click(); f.finishActions()
            assertEquals(requests, f.feeRequests.size)
            assertTrue(f.routed.isEmpty()); assertTrue(f.submitted.isEmpty()); assertTrue(f.calls.isEmpty())
        }
        select.enteredAmountFlow.value = "1.23"
        renew(f)
        f.await { select.enteredFiatAmountFlow.value != null }
        f.click(); f.finishActions()
        assertEquals(listOf("fixture-hash"), f.submitted)
        assertTrue(f.calls.all { it.amount == BigInteger.valueOf(123) })
    }
}
