package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.presentation.BaseConfirmViewModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

/** Shared production confirmation orchestration with local callbacks; no keys or RPC. */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseConfirmSubmissionOwnershipTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule(UnconfinedTestDispatcher())
    private val owned = mutableListOf<Harness>()

    @After fun tearDown() {
        owned.forEach { it.vm.viewModelScope.cancel() }
    }

    @Test fun `repeated confirm while validation waits starts one operation`() {
        val h = harness(holdValidation = true)
        h.vm.onConfirm()
        h.vm.onConfirm()
        assertEquals(1, h.validations)
        assertEquals(0, h.operations)
        h.validationResume.complete(Unit)
        assertEquals(1, h.operations)
        assertEquals(1, h.successes)
    }

    @Test fun `repeated confirm while operation waits cannot execute again`() {
        val h = harness(holdOperation = true)
        h.vm.onConfirm()
        h.vm.onConfirm()
        assertEquals(1, h.operations)
        h.operationResume.complete(Unit)
        assertEquals(1, h.successes)
    }

    @Test fun `observed success permanently consumes this confirmation`() {
        val h = harness()
        h.vm.onConfirm()
        h.vm.onConfirm()
        assertEquals(1, h.operations)
        assertEquals(1, h.successes)
    }

    @Test fun `reentrant success callback cannot reuse the confirmation`() {
        val h = harness(reenterOnSuccess = true)
        h.vm.onConfirm()
        assertEquals(1, h.operations)
        assertEquals(1, h.successes)
    }

    @Test fun `separate confirmation instances retain independent admission`() {
        val first = harness(holdOperation = true)
        val second = harness()
        first.vm.onConfirm()
        second.vm.onConfirm()
        assertEquals(1, first.operations)
        assertEquals(1, second.operations)
        assertEquals(1, second.successes)
        first.operationResume.complete(Unit)
        assertEquals(1, first.successes)
    }

    @Test fun `validation failure permits a later corrected confirmation`() {
        val h = harness()
        h.valid = false
        h.vm.onConfirm()
        assertEquals(0, h.operations)
        assertEquals(1, h.alerts.size)
        h.valid = true
        h.vm.onConfirm()
        assertEquals(1, h.operations)
        assertEquals(1, h.successes)
    }

    @Test fun `cleared view model cannot execute held validation`() {
        val h = harness(holdValidation = true)
        val store = ViewModelStore().also { it.put("actual-confirm", h.vm) }
        h.vm.onConfirm()
        store.clear()
        h.validationResume.complete(Unit)
        h.vm.onConfirm()
        assertEquals(0, h.operations)
        assertEquals(0, h.successes)
        assertTrue(h.alerts.isEmpty())
    }

    @Test fun `cancellation releases loading state without an alert`() {
        val h = harness(holdOperation = true)
        val state = h.vm.viewState
        val before = h.vm.viewModelScope.coroutineContext.job.children.toSet()
        h.vm.onConfirm()
        assertTrue(state.value.isLoading)
        (h.vm.viewModelScope.coroutineContext.job.children.toSet() - before).single().cancel()
        assertFalse(state.value.isLoading)
        assertTrue(h.alerts.isEmpty())
        assertEquals(0, h.successes)
    }

    @Test fun `ignored cancellation during validation cannot execute after clear`() {
        val h = harness(holdValidation = true, ignoreCancellation = true)
        val store = ViewModelStore().also { it.put("actual-confirm", h.vm) }
        h.vm.onConfirm()
        store.clear()
        h.validationResume.complete(Unit)
        assertEquals(0, h.operations)
        assertEquals(0, h.successes)
        assertTrue(h.alerts.isEmpty())
    }

    @Test fun `late observed success after clear cannot navigate`() {
        val h = harness(holdOperation = true, ignoreCancellation = true)
        val store = ViewModelStore().also { it.put("actual-confirm", h.vm) }
        h.vm.onConfirm()
        store.clear()
        h.operationResume.complete(Unit)
        assertEquals(1, h.operations)
        assertEquals(0, h.successes)
        assertTrue(h.alerts.isEmpty())
    }

    @Test fun `late failure after clear cannot display an alert`() {
        val h = harness(holdOperation = true, ignoreCancellation = true, failOperation = true)
        val store = ViewModelStore().also { it.put("actual-confirm", h.vm) }
        h.vm.onConfirm()
        store.clear()
        h.operationResume.complete(Unit)
        assertEquals(1, h.operations)
        assertEquals(0, h.successes)
        assertTrue(h.alerts.isEmpty())
    }

    @Test fun `cancellation returned by validation does not present an error`() {
        val h = harness(cancelValidation = true)
        val state = h.vm.viewState
        h.vm.onConfirm()
        assertEquals(1, h.validations)
        assertEquals(0, h.operations)
        assertEquals(0, h.successes)
        assertFalse(state.value.isLoading)
        assertTrue(h.alerts.isEmpty())
    }

    @Test fun `cancellation returned by operation does not present an error`() {
        val h = harness(cancelOperation = true)
        val state = h.vm.viewState
        h.vm.onConfirm()
        assertEquals(1, h.validations)
        assertEquals(1, h.operations)
        assertEquals(0, h.successes)
        assertFalse(state.value.isLoading)
        assertTrue(h.alerts.isEmpty())
    }

    @Test fun `thrown validation failure is contained and releases loading`() {
        val h = harness().also { it.throwValidation = true }
        val state = h.vm.viewState
        h.vm.onConfirm()
        assertEquals(1, h.validations)
        assertEquals(0, h.operations)
        assertFalse(state.value.isLoading)
        assertTrue(h.uncaught.isEmpty())
        assertEquals(1, h.alerts.size)
        h.throwValidation = false
        h.vm.onConfirm()
        assertEquals(1, h.operations)
    }

    @Test fun `thrown operation failure is contained and releases loading`() {
        val h = harness().also { it.throwOperation = true }
        val state = h.vm.viewState
        h.vm.onConfirm()
        assertEquals(1, h.operations)
        assertEquals(0, h.successes)
        assertFalse(state.value.isLoading)
        assertTrue(h.uncaught.isEmpty())
        assertEquals(1, h.alerts.size)
    }

    @Test fun `throwing success callback cannot escape or repeat observed output`() {
        val h = harness().also { it.throwSuccess = true }
        val state = h.vm.viewState
        h.vm.onConfirm()
        h.vm.onConfirm()
        assertEquals(1, h.operations)
        assertEquals(1, h.successes)
        assertFalse(state.value.isLoading)
        assertTrue(h.uncaught.isEmpty())
        assertEquals(1, h.alerts.size)
    }

    private fun harness(holdValidation: Boolean = false, holdOperation: Boolean = false, reenterOnSuccess: Boolean = false, ignoreCancellation: Boolean = false, failOperation: Boolean = false, cancelValidation: Boolean = false, cancelOperation: Boolean = false) =
        Harness(holdValidation, holdOperation, reenterOnSuccess, ignoreCancellation, failOperation, cancelValidation, cancelOperation).also(owned::add)

    private class Harness(holdValidation: Boolean, holdOperation: Boolean, reenterOnSuccess: Boolean, ignoreCancellation: Boolean, failOperation: Boolean, cancelValidation: Boolean, cancelOperation: Boolean) {
        var validations = 0
        var operations = 0
        var successes = 0
        var valid = true
        var throwValidation = false
        var throwOperation = false
        var throwSuccess = false
        val validationResume = CompletableDeferred<Unit>()
        val operationResume = CompletableDeferred<Unit>()
        val alerts = mutableListOf<AlertViewState>()
        val uncaught = mutableListOf<Throwable>()
        val resources = mockk<ResourceManager>(relaxed = true).also { r ->
            every { r.getString(any()) } answers { "fixture-${firstArg<Int>()}" }
        }
        val asset = mockk<Asset>(relaxed = true).also { a ->
            every { a.transferable } returns BigDecimal.ONE
            every { a.transferableInPlanks } returns BigInteger.TEN.pow(10)
            every { a.token.configuration.symbol } returns "DOT"
            every { a.token.configuration.precision } returns 10
            every { a.token.fiatRate } returns null
        }
        val vm: BaseConfirmViewModel
        init {
            vm = object : BaseConfirmViewModel(
                walletInteractor = mockk(relaxed = true) { every { assetFlow(any(), any()) } returns emptyFlow() },
                existentialDepositUseCase = mockk { coEvery { this@mockk.invoke(any()) } returns Result.success(BigInteger.ZERO) },
                resourceManager = resources,
                asset = asset,
                chain = mockk(relaxed = true),
                address = "public-fixture-address",
                titleRes = R.string.common_confirm,
                feeEstimator = { BigInteger.ONE },
                executeOperation = { _, _ ->
                    operations++
                    if (holdOperation) {
                        if (ignoreCancellation) withContext(NonCancellable) { operationResume.await() } else operationResume.await()
                    }
                    if (throwOperation) throw IllegalStateException("local operation threw")
                    when {
                        cancelOperation -> Result.failure(CancellationException("local operation cancelled"))
                        failOperation -> Result.failure(IllegalStateException("local operation failure"))
                        else -> Result.success("0xfixture")
                    }
                },
                accountNameProvider = { null },
                onOperationSuccess = {
                    successes++
                    if (throwSuccess) throw IllegalStateException("local navigation threw")
                    if (reenterOnSuccess && successes == 1) confirmAgain()
                },
                errorAlertPresenter = { alerts += it }
            ) {
                override val coroutineContext
                    get() = super.coroutineContext + CoroutineExceptionHandler { _, error -> uncaught += error }

                override suspend fun isValid(): Result<Any> {
                    validations++
                    if (holdValidation) {
                        if (ignoreCancellation) withContext(NonCancellable) { validationResume.await() } else validationResume.await()
                    }
                    if (throwValidation) throw IllegalStateException("local validation threw")
                    return when {
                        cancelValidation -> Result.failure(CancellationException("local validation cancelled"))
                        valid -> Result.success(Unit)
                        else -> Result.failure(IllegalArgumentException("fix input"))
                    }
                }
            }
        }
        private fun confirmAgain() = vm.onConfirm()
    }
}
