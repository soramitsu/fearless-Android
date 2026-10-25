package jp.co.soramitsu.staking.impl.presentation.confirm.pool.create

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolCreateFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.never
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.BigInteger

/** Actual create confirmation with local callbacks; no key provider, RPC, signing or broadcast. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmCreatePoolContextTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<Harness>()
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.flatMap { it.vms }.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }

    @Test fun `unchanged displayed create review executes its original fields once`() {
        val h = harness()
        h.vm.onConfirm()
        assertEquals(listOf(Call(BigInteger.valueOf(8), "Original pool", BigInteger.ONE)), h.calls)
        assertEquals(null, h.shared.selectValidatorsState.get())
        verify(h.router, never()).openStartSelectValidators()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `replaced create draft cannot be submitted by the previous confirmation`() {
        val h = harness()
        h.replaceDraft()
        h.vm.onConfirm()
        assertTrue(h.calls.isEmpty())
    }

    @Test fun `replaced draft during minimum lookup cannot change the reviewed payload`() {
        val h = harness(held = true)
        h.vm.onConfirm()
        assertTrue(h.minimumEntered.isCompleted)
        h.replaceDraft()
        h.minimumResume.complete(Unit)
        assertTrue(h.calls.isEmpty())
    }

    @Test fun `replaced main context invalidates the old create review`() {
        val h = harness()
        h.shared.mainState.set(h.shared.requireMainState.copy())
        h.vm.onConfirm()
        assertTrue(h.calls.isEmpty())
    }

    @Test fun `wallet selection round trip cannot revive the old create review`() {
        val h = harness()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        h.vm.onConfirm()
        assertTrue(h.calls.isEmpty())
    }

    @Test fun `Back revokes the retained create confirmation`() {
        val h = harness()
        h.vm.onBackClick()
        h.vm.onConfirm()
        assertTrue(h.calls.isEmpty())
    }

    @Test fun `Back during minimum lookup prevents create execution`() {
        val h = harness(held = true)
        h.vm.onConfirm()
        assertTrue(h.minimumEntered.isCompleted)
        h.vm.onBackClick()
        h.minimumResume.complete(Unit)
        assertTrue(h.calls.isEmpty())
    }

    @Test fun `cleared view model cannot complete held minimum lookup`() {
        val h = harness(held = true)
        h.vm.onConfirm()
        assertTrue(h.minimumEntered.isCompleted)
        ViewModelStore().also { it.put("create-review", h.vm); it.clear() }
        h.minimumResume.complete(Unit)
        assertTrue(h.calls.isEmpty())
    }

    @Test fun `old confirmation displays original pool id after draft replacement`() {
        val h = harness()
        h.replaceDraft()
        runBlocking { withTimeout(5_000) { h.vm.tableItemsFlow.first { it.size == 8 } } }
        assertEquals("8", h.vm.tableItemsFlow.value[2].value)
    }

    @Test fun `successful output after Back consumes original draft without navigation`() {
        val h = harness(heldOperation = true)
        h.vm.onConfirm()
        assertTrue(h.operationEntered.isCompleted)
        h.vm.onBackClick()
        h.submission.complete(Result.success("0xfixture"))
        assertEquals(null, h.shared.createFlowState.get())
        org.mockito.kotlin.verify(h.router, never()).returnToMain()
        org.mockito.kotlin.verify(h.router, never()).openStartSelectValidators()
        org.mockito.kotlin.verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `successful old output preserves newer draft and avoids navigation`() {
        val h = harness(heldOperation = true)
        h.vm.onConfirm()
        h.replaceDraft()
        h.submission.complete(Result.success("0xfixture"))
        assertEquals("Replacement pool", h.shared.requireCreateState.poolName)
        assertEquals(null, h.shared.selectValidatorsState.get())
        verify(h.router, never()).returnToMain()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `failed old output does not alert over replacement context`() {
        val h = harness(heldOperation = true)
        h.vm.onConfirm()
        h.replaceDraft()
        h.submission.complete(Result.failure(IllegalStateException("old local refusal")))
        verify(h.router, never()).openAlert(any())
    }

    @Test fun `two confirmation instances share one submission claim`() {
        val h = harness(heldOperation = true)
        val second = h.newVm()
        awaitFee(second)
        h.vm.onConfirm()
        second.onConfirm()
        assertEquals(1, h.calls.size)
        h.submission.complete(Result.success("0xfixture"))
    }

    @Test fun `separate confirmation cannot reuse observed successful draft`() {
        val h = harness()
        val second = h.newVm()
        awaitFee(second)
        h.vm.onConfirm()
        second.onConfirm()
        assertEquals(1, h.calls.size)
    }

    @Test fun `Back remains idempotent on retained confirmation`() {
        val h = harness()
        h.vm.onBackClick()
        h.vm.onBackClick()
        verify(h.router).back()
    }

    @Test fun `dismissal revokes retained confirmation without navigating Back`() {
        val h = harness()
        h.vm.onDismissed()
        h.vm.onConfirm()
        h.vm.onBackClick()
        assertTrue(h.calls.isEmpty())
        verify(h.router, never()).back()
    }

    @Test fun `dismissal during minimum lookup prevents execution`() {
        val h = harness(held = true)
        h.vm.onConfirm()
        h.vm.onDismissed()
        h.minimumResume.complete(Unit)
        assertTrue(h.calls.isEmpty())
        verify(h.router, never()).openAlert(any())
    }

    @Test fun `successful output after dismissal consumes draft without navigation`() {
        val h = harness(heldOperation = true)
        h.vm.onConfirm()
        h.vm.onDismissed()
        h.submission.complete(Result.success("0xfixture"))
        assertEquals(null, h.shared.createFlowState.get())
        verify(h.router, never()).returnToMain()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `wallet change during minimum lookup prevents execution`() {
        val h = harness(held = true)
        h.vm.onConfirm()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        h.minimumResume.complete(Unit)
        assertTrue(h.calls.isEmpty())
    }

    @Test fun `wallet change after output suppresses success navigation`() {
        val h = harness(heldOperation = true)
        h.vm.onConfirm()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        h.submission.complete(Result.success("0xfixture"))
        assertEquals(null, h.shared.createFlowState.get())
        verify(h.router, never()).returnToMain()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `node acknowledgement never invokes an automatic main return callback`() {
        val h = harness()
        org.mockito.kotlin.doAnswer {
            h.shared.mainState.set(h.shared.requireMainState.copy())
            Unit
        }.`when`(h.router).returnToMain()
        h.vm.onConfirm()
        verify(h.router, never()).returnToMain()
        verify(h.router, never()).openStartSelectValidators()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `reentrant claim observer replacing main context prevents execution`() {
        val h = harness()
        val review = h.shared.createConfirmationReview()
        var observerRan = false
        val observer = h.vm.viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            h.shared.createFlowState.submissionInProgress.collect { pending ->
                if (pending) {
                    observerRan = true
                    h.shared.mainState.set(h.shared.requireMainState.copy())
                }
            }
        }
        var executions = 0
        runBlocking {
            runCatching { review.execute { executions++; Result.success("0xfixture") } }
        }
        observer.cancel()
        assertTrue(observerRan)
        assertEquals(0, executions)
    }

    @Test fun `reentrant claim observer replacing draft preserves replacement`() {
        val h = harness()
        val review = h.shared.createConfirmationReview()
        var observerRan = false
        val observer = h.vm.viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            h.shared.createFlowState.submissionInProgress.collect { pending ->
                if (pending) { observerRan = true; h.replaceDraft() }
            }
        }
        var executions = 0
        runBlocking { runCatching { review.execute { executions++; Result.success("0xfixture") } } }
        observer.cancel()
        assertTrue(observerRan)
        assertEquals(0, executions)
        assertEquals("Replacement pool", h.shared.requireCreateState.poolName)
    }

    @Test fun `new review cannot adopt unrelated main context for an older draft`() {
        val h = harness()
        val original = h.shared.requireMainState
        h.shared.mainState.set(original.copy(address = "another-public-root"))
        val review = h.shared.createConfirmationReview()
        org.junit.Assert.assertSame(original, review.main)
        assertTrue(!review.isCurrent())
    }

    @Test fun `reentrant claim observer revoking review prevents execution and releases claim`() {
        assertReentrantRevocation { _, review -> review.revoke() }
    }

    @Test fun `reentrant claim observer changing wallet prevents execution and releases claim`() {
        assertReentrantRevocation { _, _ -> WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
    }

    private fun assertReentrantRevocation(change: (Harness, jp.co.soramitsu.staking.impl.presentation.common.CreatePoolConfirmationReview) -> Unit) {
        val h = harness()
        val review = h.shared.createConfirmationReview()
        var observed = false
        val observer = h.vm.viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            h.shared.createFlowState.submissionInProgress.collect { pending ->
                if (pending) { observed = true; change(h, review) }
            }
        }
        var executions = 0
        runBlocking {
            val result = runCatching { review.execute { executions++; Result.success("0xfixture") } }
            assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
            assertTrue(!h.shared.createFlowState.submissionInProgress.first())
        }
        observer.cancel()
        assertTrue(observed)
        assertEquals(0, executions)
    }

    @Test fun `success presentation lease is once only and cannot escape its synchronous scope`() {
        val h = harness()
        val review = h.shared.createConfirmationReview()
        runBlocking { review.execute { Result.success("0xfixture") } }
        var steps = 0
        review.withSuccessPresentation {
            review.revoke()
            review.present { steps++ }
            review.withSuccessPresentation { steps++ }
        }
        review.present { steps++ }
        review.withSuccessPresentation { steps++ }
        assertEquals(1, steps)
    }

    @Test fun `node acknowledgement never invokes a pop that could change wallet`() {
        val h = harness()
        doAnswer {
            ViewModelStore().also { it.put("create", h.vm); it.clear() }
            WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
            Unit
        }.`when`(h.router).returnToMain()
        h.vm.onConfirm()
        verify(h.router, never()).returnToMain()
        verify(h.router, never()).openStartSelectValidators()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    private fun harness(held: Boolean = false, heldOperation: Boolean = false) = Harness(held, heldOperation).also {
        owned += it
        awaitFee(it.vm)
    }

    private fun awaitFee(vm: ConfirmCreatePoolViewModel) = runBlocking {
        withTimeout(5_000) { vm.feeViewStateFlow.first { fee -> fee.value != null } }
    }

    private data class Call(val poolId: BigInteger, val name: String, val amount: BigInteger)
    private class Harness(private val held: Boolean, heldOperation: Boolean) {
        val vms = mutableListOf<ConfirmCreatePoolViewModel>()
        val router = mock<StakingRouter>()
        val operationEntered = CompletableDeferred<Unit>()
        val submission = CompletableDeferred<Result<String>>()
        val calls = mutableListOf<Call>()
        val minimumEntered = CompletableDeferred<Unit>()
        val minimumResume = CompletableDeferred<Unit>()
        val resources = mock<ResourceManager> {
            on { getString(any()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" }
            on { getString(any(), anyVararg()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" }
        }
        val asset = mock<Asset>(defaultAnswer = Answers.RETURNS_DEEP_STUBS).also { a ->
            whenever(a.transferable).thenReturn(BigDecimal.ONE)
            whenever(a.transferableInPlanks).thenReturn(BigInteger.TEN.pow(10))
            whenever(a.token.configuration.id).thenReturn("native")
            whenever(a.token.configuration.chainId).thenReturn("public-fixture-chain")
            whenever(a.token.configuration.symbol).thenReturn("DOT")
            whenever(a.token.configuration.precision).thenReturn(10)
            whenever(a.token.fiatRate).thenReturn(null)
        }
        val chain = mock<Chain> { on { id } doReturn "public-fixture-chain" }
        val shared = StakingPoolSharedStateProvider().also {
            it.mainState.set(StakingPoolState(chain = chain, asset = asset, address = "public-root"))
            it.createFlowState.set(StakingPoolCreateFlowState("Original pool", BigInteger.ONE, 8, "public-nominator", "public-bouncer", it.mainState.capture()))
        }
        val pool = mock<StakingPoolInteractor> {
            onBlocking { estimateCreateFee(any(), any(), any(), any(), any(), any(), any(), any()) } doReturn BigInteger.ONE
            onBlocking { getAccountName(any()) } doReturn null
        }
        val vm: ConfirmCreatePoolViewModel
        init {
            runBlocking {
                org.mockito.Mockito.doAnswer {
                    calls += Call(it.getArgument(2), it.getArgument(3), it.getArgument(4))
                    "0xfixture"
                }.`when`(pool).createPool(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
                if (heldOperation) runBlocking {
                    whenever(pool.createPool(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).doSuspendableAnswer {
                        calls += Call(it.getArgument(2), it.getArgument(3), it.getArgument(4))
                        operationEntered.complete(Unit)
                        withContext(NonCancellable) { submission.await() }
                    }
                }
            vm = newVm()
        }
        fun newVm(): ConfirmCreatePoolViewModel = ConfirmCreatePoolViewModel(
                walletInteractor = mock<WalletInteractor> { on { assetFlow(any(), any()) } doReturn emptyFlow() },
                existentialDepositUseCase = object : ExistentialDepositUseCase {
                    override suspend fun invoke(chainAsset: jp.co.soramitsu.core.models.Asset): Result<BigInteger> {
                        minimumEntered.complete(Unit)
                        if (held) minimumResume.await()
                        return Result.success(BigInteger.ZERO)
                    }
                },
                poolSharedStateProvider = shared,
                stakingPoolInteractor = pool,
                resourceManager = resources,
                router = router,
                poolInteractor = pool
            ).also { vms += it }
        fun replaceDraft() = shared.createFlowState.set(shared.requireCreateState.copy(poolName = "Replacement pool", poolId = 99, amountInPlanks = BigInteger.valueOf(99)))
    }
}
