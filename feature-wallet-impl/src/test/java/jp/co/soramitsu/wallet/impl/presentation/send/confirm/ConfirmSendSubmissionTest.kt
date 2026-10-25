package jp.co.soramitsu.wallet.impl.presentation.send.confirm

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Real confirmation entry points with local held validation/submission; never signs or sends. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmSendSubmissionTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private var validation = CompletableDeferred<Result<TransferValidationResult>>()
    private var submission = CompletableDeferred<Result<String>>()
    private val validations = AtomicInteger()
    private val submissions = AtomicInteger()
    private val estimates = AtomicInteger()
    private val chainAsset = mockk<ChainAsset>(relaxed = true).also {
        every { it.chainId } returns CHAIN
        every { it.id } returns "native"
        every { it.symbol } returns "LOCAL"
        every { it.precision } returns 12
        every { it.currencyId } returns "local-native"
        every { it.isUtility } returns true
    }
    private val chain = mockk<Chain>(relaxed = true).also {
        every { it.id } returns CHAIN
        every { it.name } returns "Local fixture"
        every { it.ecosystem } returns Ecosystem.Substrate
        every { it.assets } returns listOf(chainAsset)
    }
    private val asset = Asset.createEmpty(chainAsset, 7, ByteArray(32), minSupportedVersion = null)
        .copy(freeInPlanksState = AssetAmountState.Known(BigInteger.TEN.pow(14)))
    private val assets = MutableStateFlow(asset)
    private val interactor = mockk<WalletInteractor>(relaxed = true).also {
        coEvery { it.getChain(CHAIN) } returns chain
        every { it.assetFlow(CHAIN, "native") } returns assets
        every { it.observeAddressBook(CHAIN) } returns flowOf(emptyList())
        coEvery { it.getSelectedMetaAccount() } returns mockk<MetaAccount>(relaxed = true)
        coEvery { it.observeTransferFee(any()) } coAnswers {
            estimates.incrementAndGet()
            flowOf(BigDecimal.ONE)
        }
        coEvery { it.performTransfer(any()) } coAnswers {
            submissions.incrementAndGet()
            yield()
            submission.await()
        }
    }
    private val validator = mockk<ValidateTransferUseCase>(relaxed = true).also {
        coEvery { it.validateExistentialDeposit(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            validations.incrementAndGet()
            yield()
            validation.await()
        }
    }
    private val currentAddress = mockk<CurrentAccountAddressUseCase>().also {
        coEvery { it.invoke(CHAIN) } returns "0x1111111111111111111111111111111111111111"
    }
    private val router = mockk<WalletRouter>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true).also {
        every { it.getString(any()) } answers { "text-${firstArg<Int>()}" }
    }
    private var owned: ConfirmSendViewModel? = null
    private val uncaught = mutableListOf<Throwable>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Thread.setDefaultUncaughtExceptionHandler { _, error -> synchronized(uncaught) { uncaught += error } }
    }

    @After fun teardown() {
        owned?.let { vm ->
            val job = vm.viewModelScope.coroutineContext.job
            vm.viewModelScope.cancel()
            runBlocking { withTimeout(10_000) { job.join() } }
        }
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        Dispatchers.resetMain()
        assertEquals("No uncaught ViewModel failures", emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
    }

    @Test fun `held validation admits one confirmation before loading reaches the UI`() {
        val vm = viewModel()
        vm.onNextClick()
        vm.onNextClick()
        assertEquals(1, validations.get())
        await { vm.state.value.isLoading }
        assertFalse(vm.state.value.buttonState.enabled)
        assertEquals(0, submissions.get())
    }

    @Test fun `held transaction rejects repeated confirmation`() {
        val vm = viewModel()
        validation.complete(Result.success(TransferValidationResult.Valid))
        vm.onNextClick()
        await { submissions.get() == 1 }
        vm.onNextClick()
        assertEquals(1, validations.get())
        assertEquals(1, submissions.get())
    }

    @Test fun `successful review cannot submit again after navigation`() {
        val vm = viewModel()
        validation.complete(Result.success(TransferValidationResult.Valid))
        vm.onNextClick()
        await { submissions.get() == 1 }
        submission.complete(Result.success("local-fixture-hash"))
        await { !vm.state.value.isLoading }
        verify(exactly = 1, timeout = 10_000) { router.finishSendFlow() }
        vm.onNextClick()
        // The validation callback runs synchronously until its deliberate yield.
        assertEquals(1, validations.get())
        assertEquals(1, submissions.get())
    }

    @Test fun `successful review navigates with the single exact local result`() {
        val vm = viewModel()
        validation.complete(Result.success(TransferValidationResult.Valid))
        vm.onNextClick()
        await { submissions.get() == 1 }
        submission.complete(Result.success("local-fixture-hash"))
        await { !vm.state.value.isLoading }
        verify(exactly = 1, timeout = 10_000) { router.finishSendFlow() }
        verify(exactly = 1, timeout = 10_000) { router.openOperationSuccess("local-fixture-hash", CHAIN) }
        coVerify(exactly = 1) { interactor.performTransfer(match { it.recipient == RECIPIENT && it.amount == BigDecimal.TEN }) }
    }

    @Test fun `validation refusal releases the confirmation for explicit retry`() {
        val vm = viewModel()
        vm.onNextClick()
        validation.complete(Result.failure(IllegalStateException("local validation refusal")))
        await { vm.errorLiveData.value?.peekContent() == "local validation refusal" }
        assertEquals(0, submissions.get())
        await { !vm.state.value.isLoading }
        validation = CompletableDeferred(Result.success(TransferValidationResult.Valid))
        vm.onNextClick()
        await { submissions.get() == 1 }
        assertEquals(2, validations.get())
    }

    @Test fun `validation warning never sends until explicit acknowledgement`() {
        val vm = viewModel()
        val warning = TransferValidationResult.ExistentialDepositWarning("1 LOCAL")
        validation.complete(Result.success(warning))
        vm.onNextClick()
        await { vm.openValidationWarningEvent.value != null }
        assertEquals(0, submissions.get())
        await { !vm.state.value.isLoading }
        assertNotNull(vm.openValidationWarningEvent.value?.peekContent()?.second)
        validation = CompletableDeferred(Result.success(TransferValidationResult.Valid))
        vm.warningConfirmed(warning)
        await { submissions.get() == 1 }
        assertEquals(2, validations.get())
    }

    @Test fun `thrown validation failure is shown and permits a new explicit attempt`() {
        val vm = viewModel()
        vm.onNextClick()
        validation.completeExceptionally(IllegalStateException("local preparation failure"))
        await { vm.errorLiveData.value != null || synchronized(uncaught) { uncaught.isNotEmpty() } }
        assertNotNull(vm.errorLiveData.value)
        await { !vm.state.value.isLoading }
        validation = CompletableDeferred(Result.success(TransferValidationResult.Valid))
        vm.onNextClick()
        await { submissions.get() == 1 }
    }

    @Test fun `cancelled validation releases loading without a user error`() {
        val vm = viewModel()
        vm.onNextClick()
        assertTrue(vm.state.value.isLoading)
        validation.cancel(CancellationException("local cancellation"))
        await { !vm.state.value.isLoading }
        assertEquals(null, vm.errorLiveData.value)
        validation = CompletableDeferred(Result.success(TransferValidationResult.Valid))
        vm.onNextClick()
        await { submissions.get() == 1 }
    }

    @Test fun `navigation failure after success cannot submit the same review again`() {
        every { router.finishSendFlow() } throws IllegalStateException("local navigation failure")
        val vm = viewModel()
        validation.complete(Result.success(TransferValidationResult.Valid))
        vm.onNextClick()
        await { submissions.get() == 1 }
        submission.complete(Result.success("local-fixture-hash"))
        await { vm.errorLiveData.value != null && !vm.state.value.isLoading }
        vm.onNextClick()
        assertEquals(1, validations.get())
        assertEquals(1, submissions.get())
    }

    @Test fun `failed submission permits a deliberate retry without overlap`() {
        val vm = viewModel()
        validation.complete(Result.success(TransferValidationResult.Valid))
        vm.onNextClick()
        await { submissions.get() == 1 }
        submission.complete(Result.failure(IllegalStateException("local submission refusal")))
        await { vm.errorLiveData.value != null && !vm.state.value.isLoading }
        submission = CompletableDeferred()
        vm.onNextClick()
        await { submissions.get() == 2 }
        vm.onNextClick()
        assertEquals(2, validations.get())
        assertEquals(2, submissions.get())
    }

    @Test fun `known successful transfer stays spent when return dispatch is cancelled`() {
        coEvery { interactor.performTransfer(any()) } coAnswers {
            submissions.incrementAndGet()
            yield()
            submission.await().also {
                currentCoroutineContext().cancel(CancellationException("local return dispatch cancellation"))
            }
        }
        val vm = viewModel()
        validation.complete(Result.success(TransferValidationResult.Valid))
        vm.onNextClick()
        await { submissions.get() == 1 }
        submission.complete(Result.success("local-fixture-hash"))
        await { !vm.state.value.isLoading }
        assertEquals(null, vm.errorLiveData.value)
        vm.onNextClick()
        assertEquals(1, validations.get())
        assertEquals(1, submissions.get())
        verify(exactly = 0) { router.finishSendFlow() }
    }

    @Test fun `result valued validation cancellation is silent and permits explicit retry`() {
        val vm = viewModel()
        vm.onNextClick()
        validation.complete(Result.failure(CancellationException("local validation cancellation")))
        await { !vm.state.value.isLoading }
        assertEquals(null, vm.errorLiveData.value)
        validation = CompletableDeferred(Result.success(TransferValidationResult.Valid))
        vm.onNextClick()
        await { submissions.get() == 1 }
    }

    @Test fun `result valued submission cancellation is silent and permits explicit retry`() {
        val vm = viewModel()
        validation.complete(Result.success(TransferValidationResult.Valid))
        vm.onNextClick()
        await { submissions.get() == 1 }
        submission.complete(Result.failure(CancellationException("local submission cancellation")))
        await { !vm.state.value.isLoading }
        assertEquals(null, vm.errorLiveData.value)
        submission = CompletableDeferred()
        vm.onNextClick()
        await { submissions.get() == 2 }
    }

    @Test fun `unknown initial balance clears fee and never estimates validates or submits`() {
        assets.value = asset.copy(freeInPlanksState = AssetAmountState.Missing)
        val vm = viewModel(waitForReady = false)
        await { vm.state.value.feeInfoItem?.value == unavailable }
        assertFalse(vm.state.value.buttonState.enabled)
        vm.onNextClick()
        assertEquals(0, estimates.get())
        assertEquals(0, validations.get())
        assertEquals(0, submissions.get())
        assertFalse(vm.state.value.isLoading)
        coVerify(exactly = 0) { interactor.observeTransferFee(any()) }
        coVerify(exactly = 0) { interactor.performTransfer(any()) }
    }

    @Test fun `unknown fee asset blocks a funded transfer asset until fee balance recovers`() {
        every { chainAsset.isUtility } returns false
        val utilityConfig = mockk<ChainAsset>(relaxed = true) {
            every { chainId } returns CHAIN
            every { id } returns "utility"
            every { symbol } returns "FEE"
            every { precision } returns 12
            every { isUtility } returns true
        }
        val utility = asset.copy(token = asset.token.copy(configuration = utilityConfig))
        val utilityRows = MutableStateFlow(utility.copy(freeInPlanksState = AssetAmountState.Missing))
        every { chain.assets } returns listOf(chainAsset, utilityConfig)
        every { interactor.assetFlow(CHAIN, "utility") } returns utilityRows
        val vm = viewModel(waitForReady = false)
        await { vm.state.value.feeInfoItem?.value == unavailable }
        assertFalse(vm.state.value.buttonState.enabled)
        vm.onNextClick()
        assertEquals(0, estimates.get())
        assertEquals(0, validations.get())
        assertEquals(0, submissions.get())
        utilityRows.value = utility
        await { vm.state.value.buttonState.enabled && estimates.get() > 0 }
        assertTrue(vm.state.value.feeInfoItem?.value != unavailable)
    }

    @Test fun `lost balance revokes fee and action until a fresh known balance recovers`() {
        val vm = viewModel()
        val firstEstimateCount = estimates.get()
        assertTrue(firstEstimateCount > 0)
        assertNotNull(vm.state.value.feeInfoItem?.value)
        assets.value = asset.copy(freeInPlanksState = AssetAmountState.Unavailable("malformed-quantity"))
        await { !vm.state.value.buttonState.enabled && vm.state.value.feeInfoItem?.value == unavailable }
        vm.onNextClick()
        assertEquals(0, validations.get())
        assertEquals(0, submissions.get())
        assertEquals(firstEstimateCount, estimates.get())
        assets.value = asset
        await { vm.state.value.buttonState.enabled && estimates.get() > firstEstimateCount }
        assertTrue(vm.state.value.feeInfoItem?.value != unavailable)
        validation.complete(Result.success(TransferValidationResult.Valid))
        vm.onNextClick()
        await { submissions.get() == 1 }
        assertEquals(1, validations.get())
    }

    @Test fun `balance revocation while validation is held prevents submission after approval`() {
        val vm = viewModel()
        vm.onNextClick()
        assertEquals(1, validations.get())
        assets.value = asset.copy(freeInPlanksState = AssetAmountState.Missing)
        await { vm.state.value.feeInfoItem?.value == unavailable && !vm.state.value.buttonState.enabled }
        validation.complete(Result.success(TransferValidationResult.Valid))
        await { !vm.state.value.isLoading }
        assertEquals(0, submissions.get())
        coVerify(exactly = 0) { interactor.performTransfer(any()) }
        verify(exactly = 0) { router.finishSendFlow() }
    }

    private val unavailable get() = "text-${jp.co.soramitsu.common.R.string.portfolio_balance_unavailable}"

    private fun viewModel(waitForReady: Boolean = true): ConfirmSendViewModel {
        val draft = TransferDraft(BigDecimal.TEN, BigDecimal.ONE, AssetPayload(CHAIN, "native"), RECIPIENT, null, null)
        return ConfirmSendViewModel(interactor, mockk(relaxed = true), router, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            SavedStateHandle(mapOf(ConfirmSendFragment.KEY_DRAFT to draft)), resources, currentAddress, validator)
            .also { owned = it; if (waitForReady) await { it.state.value.buttonState.enabled } }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("Expected local ViewModel state before timeout", condition())
    }

    private companion object {
        const val CHAIN = "local-confirmation-fixture"
        const val RECIPIENT = "0x2222222222222222222222222222222222222222"
    }
}
