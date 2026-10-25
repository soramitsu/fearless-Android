package jp.co.soramitsu.staking.impl.presentation.staking.unbond

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.validation.*
import jp.co.soramitsu.staking.impl.domain.validations.unbond.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*

/** Test-only baseline: actual warning callback must not reuse a fee for a different encoded amount. */
@OptIn(ExperimentalCoroutinesApi::class)
class UnbondWarningFeeReviewTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<UnbondAuthorityFixture>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        owned.forEach { it.releaseAndCancel() }
        try { runBlocking { owned.forEach { it.close() } } } finally { Dispatchers.resetMain() }
    }
    private suspend fun fixture(confirm: Boolean = false, warning: Boolean = true): UnbondAuthorityFixture {
        val f = UnbondAuthorityFixture(dispatcher, confirmScreen = confirm).also { owned += it; it.start() }
        if (warning) {
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
        return f
    }
    private suspend fun acceptWarning(f: UnbondAuthorityFixture, submit: Boolean = true) {
        f.click(submit)
        val validatable = f.vm as Validatable
        f.await { validatable.validationFailureEvent.value != null }
        val original = f.validated.single()
        assertEquals(0, BigDecimal.ONE.compareTo(original.amount))
        assertEquals(0, BigDecimal.ONE.compareTo(original.fee))
        assertTrue(f.submitted.isEmpty()); assertTrue(f.routed.isEmpty())
        requireNotNull(validatable.validationFailureEvent.value).peekContent().confirmWarning()
        dispatcher.scheduler.runCurrent()
        f.finishActions()
    }

    @Test fun largerAmountWarningCannotOpenConfirmationWithThePriorAmountFee() = runBlocking<Unit> {
        val f = fixture()
        acceptWarning(f, submit = false)
        assertTrue("A ten-unit review cannot inherit the one-unit fee without a fresh completed quote: ${f.routed.map { it.amount to it.fee }}",
            f.routed.isEmpty())
        assertTrue(f.submitted.isEmpty())
    }

    @Test fun largerAmountWarningCannotSubmitFromSelectWithThePriorAmountFee() = runBlocking<Unit> {
        val f = fixture()
        acceptWarning(f)
        assertTrue("Changed unbond amount reached submission without another completed fee: ${f.calls.map { it.amount }}",
            f.submitted.isEmpty())
        assertTrue(f.routed.isEmpty())
    }

    @Test fun largerAmountWarningCannotSubmitFromConfirmationWithThePriorAmountFee() = runBlocking<Unit> {
        val f = fixture(confirm = true)
        acceptWarning(f)
        assertTrue("Confirmation changed the encoded amount without renewing its bound fee: ${f.calls.map { it.amount }}",
            f.submitted.isEmpty())
        assertTrue(f.routed.isEmpty())
    }

    @Test fun unchangedReviewedAmountsStillReachBothActualCallersWithTheCompletedFee() = runBlocking<Unit> {
        for (confirm in listOf(false, true)) {
            val f = fixture(confirm = confirm, warning = false)
            f.click(); f.finishActions()
            assertEquals(listOf("fixture-hash"), f.submitted)
            assertEquals(BigInteger.valueOf(100), f.calls.single().amount)
            assertEquals(0, BigDecimal.ONE.compareTo(f.validated.single().fee))
        }
    }
}
