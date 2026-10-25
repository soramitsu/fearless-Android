package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolJoinFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Actual ViewModel entry points with a held local submission; no signing or network calls. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmJoinPoolSubmissionTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private var submission = CompletableDeferred<Result<String>>()
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
    }
    private val asset = mock<Asset>(defaultAnswer = Answers.RETURNS_DEEP_STUBS).also {
        whenever(it.token.configuration.symbol).thenReturn("DOT")
        whenever(it.token.configuration.precision).thenReturn(10)
        whenever(it.token.fiatRate).thenReturn(null)
    }
    private val pool = mock<PoolInfo> {
        on { poolId } doReturn BigInteger.ONE
        on { name } doReturn "Public fixture pool"
    }
    private val shared = StakingPoolSharedStateProvider().apply {
        mainState.set(StakingPoolState(chain = mock<Chain> { on { id } doReturn "local-fixture" },
            asset = asset, address = "public-fixture-address"))
        joinFlowState.set(StakingPoolJoinFlowState(BigDecimal.ONE, pool, mainState.capture()))
    }
    private val interactor = mock<StakingPoolInteractor> {
        onBlocking { estimateJoinFee(any(), any(), any(), any(), any()) } doReturn BigInteger.ONE
        onBlocking { getValidatorsIds(any(), any()) } doReturn emptyList()
        onBlocking { joinPool(any(), any(), any(), any(), any(), any(), any()) } doSuspendableAnswer {
            // Mockito must resume suspend Result<T>; an immediate boxed Result is not the Kotlin ABI.
            yield()
            submission.await().onSuccess { _ -> it.getArgument<() -> Unit>(6).invoke() }
        }
    }
    private val router = mock<StakingRouter>()
    private val owned = mutableListOf<ConfirmJoinPoolViewModel>()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        owned.forEach { it.viewModelScope.cancel() }
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun `repeated confirmations before the coroutine runs submit once`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        vm.onConfirm()
        runCurrent()
        verify(interactor, times(1)).joinPool(any(), any(), any(), any(), any(), any(), any())
        assertTrue(vm.viewState.value.isLoading)
    }

    @Test fun `repeated confirmations while submission is held submit once`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        vm.onConfirm()
        runCurrent()
        verify(interactor, times(1)).joinPool(any(), any(), any(), any(), any(), any(), any())
        assertTrue(vm.viewState.value.isLoading)
    }

    @Test fun `successful confirmation cannot submit the same review again`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        submission.complete(Result.success("local-fixture-hash"))
        runCurrent()
        vm.onConfirm()
        runCurrent()
        verify(interactor, times(1)).joinPool(any(), any(), any(), any(), any(), any(), any())
        verify(router, times(1)).returnToMain()
        verify(router, times(1)).openOperationSuccess("local-fixture-hash", "local-fixture")
        assertNull(shared.joinFlowState.get())
    }

    @Test fun `failed result releases the review for an explicit retry`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        submission.complete(Result.failure(IllegalStateException("local refusal")))
        runCurrent()
        assertFalse(vm.viewState.value.isLoading)
        assertEquals("local refusal", vm.errorLiveData.value?.peekContent())
        submission = CompletableDeferred(Result.success("retry-hash"))
        vm.onConfirm()
        runCurrent()
        verify(interactor, times(2)).joinPool(any(), any(), any(), any(), any(), any(), any())
        verify(router, times(1)).openOperationSuccess("retry-hash", "local-fixture")
    }

    @Test fun `thrown preparation failure clears loading and supports retry`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        submission.completeExceptionally(IllegalStateException("local preparation failure"))
        runCurrent()
        assertFalse(vm.viewState.value.isLoading)
        assertEquals("local preparation failure", vm.errorLiveData.value?.peekContent())
        submission = CompletableDeferred(Result.success("retry-hash"))
        vm.onConfirm()
        runCurrent()
        verify(interactor, times(2)).joinPool(any(), any(), any(), any(), any(), any(), any())
    }

    @Test fun `cancelled preparation clears loading without displaying a failure`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        submission.cancel(CancellationException("local cancellation"))
        runCurrent()
        assertFalse(vm.viewState.value.isLoading)
        assertNull(vm.errorLiveData.value)
    }

    @Test fun `navigation failure after success cannot resubmit the review`() = runTest(dispatcher) {
        doThrow(IllegalStateException("local navigation failure")).whenever(router).returnToMain()
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        submission.complete(Result.success("local-fixture-hash"))
        runCurrent()
        assertFalse(vm.viewState.value.isLoading)
        assertEquals("local navigation failure", vm.errorLiveData.value?.peekContent())
        vm.onConfirm()
        runCurrent()
        verify(interactor, times(1)).joinPool(any(), any(), any(), any(), any(), any(), any())
        assertNull(shared.joinFlowState.get())
    }

    @Test fun `result valued cancellation clears loading silently and permits explicit retry`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        submission.complete(Result.failure(CancellationException("local cancellation result")))
        runCurrent()
        assertFalse(vm.viewState.value.isLoading)
        assertNull(vm.errorLiveData.value)
        submission = CompletableDeferred(Result.success("retry-hash"))
        vm.onConfirm()
        runCurrent()
        verify(interactor, times(2)).joinPool(any(), any(), any(), any(), any(), any(), any())
    }

    @Test fun `superseded confirmation cannot submit an older pool review`() = runTest(dispatcher) {
        val vm = viewModel()
        shared.joinFlowState.set(StakingPoolJoinFlowState(BigDecimal.TEN, pool, shared.mainState.capture()))
        vm.onConfirm()
        runCurrent()
        verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
    }

    @Test fun `late pool success preserves a newer review and its navigation`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        val newer = StakingPoolJoinFlowState(BigDecimal.TEN, pool, shared.mainState.capture())
        shared.joinFlowState.set(newer)
        submission.complete(Result.success("local-fixture-hash"))
        runCurrent()
        assertSame(newer, shared.joinFlowState.get())
        verify(router, never()).returnToMain()
        verify(router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `restoring an equal pool review does not revive an older confirmation`() = runTest(dispatcher) {
        val original = shared.requireJoinState
        val vm = viewModel()
        shared.joinFlowState.set(StakingPoolJoinFlowState(BigDecimal.TEN, pool, shared.mainState.capture()))
        shared.joinFlowState.set(original)
        vm.onConfirm()
        runCurrent()
        verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
    }

    @Test fun `queued confirmation cannot submit after its review is replaced`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        shared.joinFlowState.set(StakingPoolJoinFlowState(BigDecimal.TEN, pool, shared.mainState.capture()))
        runCurrent()
        verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
        assertFalse(vm.viewState.value.isLoading)
    }


    @Test fun `two confirmation instances cannot queue the same shared review twice`() = runTest(dispatcher) {
        val first = viewModel()
        val second = viewModel()
        first.onConfirm()
        second.onConfirm()
        runCurrent()
        verify(interactor, times(1)).joinPool(any(), any(), any(), any(), any(), any(), any())
        assertTrue(second.viewState.value.isLoading)
    }

    @Test fun `a second confirmation cannot submit the same review while the first is held`() = runTest(dispatcher) {
        val first = viewModel()
        first.onConfirm()
        runCurrent()
        val second = viewModel()
        second.onConfirm()
        runCurrent()
        verify(interactor, times(1)).joinPool(any(), any(), any(), any(), any(), any(), any())
        assertTrue(second.viewState.value.isLoading)
    }

    @Test fun `back revokes callbacks from the departed confirmation`() = runTest(dispatcher) {
        val draft = shared.requireJoinState
        val vm = viewModel()
        vm.onBackClick()
        vm.onConfirm()
        runCurrent()
        verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
        verify(router).back()
        assertSame(draft, shared.joinFlowState.get())
    }

    @Test fun `back revokes a queued confirmation before its submission starts`() = runTest(dispatcher) {
        val draft = shared.requireJoinState
        val vm = viewModel()
        vm.onConfirm()
        vm.onBackClick()
        runCurrent()
        verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
        assertSame(draft, shared.joinFlowState.get())
        assertFalse(vm.viewState.value.isLoading)
    }

    @Test fun `known success after back spends the old review without taking over navigation`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        vm.onBackClick()
        submission.complete(Result.success("local-fixture-hash"))
        runCurrent()
        assertNull(shared.joinFlowState.get())
        verify(router, never()).returnToMain()
        verify(router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        assertNull(vm.errorLiveData.value)
    }

    @Test fun `late refusal cannot display an error over a newer shared review`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        val newer = StakingPoolJoinFlowState(BigDecimal.TEN, pool, shared.mainState.capture())
        shared.joinFlowState.set(newer)
        submission.complete(Result.failure(IllegalStateException("old local refusal")))
        runCurrent()
        assertNull(vm.errorLiveData.value)
        assertSame(newer, shared.joinFlowState.get())
    }

    @Test fun `late thrown failure cannot display an error over a newer shared review`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        val newer = StakingPoolJoinFlowState(BigDecimal.TEN, pool, shared.mainState.capture())
        shared.joinFlowState.set(newer)
        submission.completeExceptionally(IllegalStateException("old local exception"))
        runCurrent()
        assertNull(vm.errorLiveData.value)
        assertSame(newer, shared.joinFlowState.get())
    }

    @Test fun `refusal after back stays silent and leaves the draft available for explicit retry`() = runTest(dispatcher) {
        val draft = shared.requireJoinState
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        vm.onBackClick()
        submission.complete(Result.failure(IllegalStateException("old local refusal")))
        runCurrent()
        assertNull(vm.errorLiveData.value)
        assertSame(draft, shared.joinFlowState.get())
        submission = CompletableDeferred(Result.success("retry-hash"))
        val retry = viewModel()
        retry.onConfirm()
        runCurrent()
        verify(interactor, times(2)).joinPool(any(), any(), any(), any(), any(), any(), any())
        verify(router).openOperationSuccess("retry-hash", "local-fixture")
    }

    @Test fun `back before submission keeps a healthy replacement confirmation usable`() = runTest(dispatcher) {
        val first = viewModel()
        first.onBackClick()
        val second = viewModel()
        second.onConfirm()
        runCurrent()
        verify(interactor, times(1)).joinPool(any(), any(), any(), any(), any(), any(), any())
    }


    @Test fun `dismissal revokes queued callbacks without navigating again`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        vm.onDismissed()
        runCurrent()
        verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
        verify(router, never()).back()
        assertFalse(vm.viewState.value.isLoading)
    }

    @Test fun `dismissal allows known success recording but prevents late presentation`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        vm.onDismissed()
        submission.complete(Result.success("local-fixture-hash"))
        runCurrent()
        assertNull(shared.joinFlowState.get())
        verify(router, never()).returnToMain()
        verify(router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `success navigation cannot open over a reentrant newer review`() = runTest(dispatcher) {
        val newer = StakingPoolJoinFlowState(BigDecimal.TEN, pool, shared.mainState.capture())
        doAnswer { shared.joinFlowState.set(newer); Unit }.whenever(router).returnToMain()
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        submission.complete(Result.success("local-fixture-hash"))
        runCurrent()
        assertSame(newer, shared.joinFlowState.get())
        verify(router).returnToMain()
        verify(router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }


    @Test fun `departed confirmation callbacks cannot navigate back repeatedly`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onBackClick()
        vm.onBackClick()
        vm.onDismissed()
        vm.onBackClick()
        verify(router, times(1)).back()
    }

    @Test fun `replaced main context cannot submit an older join review`() = runTest(dispatcher) {
        val vm = viewModel()
        shared.mainState.set(shared.requireMainState.copy(address = "new-public-fixture-address"))
        vm.onConfirm()
        runCurrent()
        verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
    }

    @Test fun `restored equal main context cannot revive an older join review`() = runTest(dispatcher) {
        val original = shared.requireMainState
        val vm = viewModel()
        shared.mainState.set(original.copy(address = "new-public-fixture-address"))
        shared.mainState.set(original)
        vm.onConfirm()
        runCurrent()
        verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
    }

    @Test fun `queued review stops when main context is replaced`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        shared.mainState.set(shared.requireMainState.copy(address = "new-public-fixture-address"))
        runCurrent()
        verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
        assertFalse(vm.viewState.value.isLoading)
    }

    @Test fun `wallet change and return cannot revive the reviewed confirmation`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        vm.onConfirm()
        runCurrent()
        verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
    }

    @Test fun `wallet change before confirmation construction does not authorize retained main state`() = runTest(dispatcher) {
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
    }

    @Test fun `ongoing wallet selection refuses confirmation without consuming the draft`() = runTest(dispatcher) {
        val draft = shared.requireJoinState
        val vm = viewModel()
        WalletSelectionEpoch.beginChange()
        try {
            vm.onConfirm()
            runCurrent()
            verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
            assertSame(draft, shared.joinFlowState.get())
        } finally { WalletSelectionEpoch.endChange() }
    }

    @Test fun `known success after wallet change consumes its claim without presenting over the new wallet`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        submission.complete(Result.success("local-fixture-hash"))
        runCurrent()
        assertNull(shared.joinFlowState.get())
        verify(router, never()).returnToMain()
        verify(router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `late refusal after wallet change stays silent`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        submission.complete(Result.failure(IllegalStateException("old wallet refusal")))
        runCurrent()
        assertNull(vm.errorLiveData.value)
    }

    @Test fun `new confirmation cannot combine old join draft with replaced main context`() = runTest(dispatcher) {
        shared.mainState.set(shared.requireMainState.copy(address = "new-public-fixture-address"))
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        verify(interactor, never()).joinPool(any(), any(), any(), any(), any(), any(), any())
    }

    @Test fun `known success after replaced main context remains consumed without late navigation`() = runTest(dispatcher) {
        val original = shared.requireMainState
        val vm = viewModel()
        vm.onConfirm()
        runCurrent()
        shared.mainState.set(original.copy(address = "new-public-fixture-address"))
        shared.mainState.set(original)
        submission.complete(Result.success("local-fixture-hash"))
        runCurrent()
        assertNull(shared.joinFlowState.get())
        verify(router, never()).returnToMain()
        verify(router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    private fun viewModel() = ConfirmJoinPoolViewModel(resources, shared, interactor, router).also { owned += it }
}
