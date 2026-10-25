package jp.co.soramitsu.staking.impl.presentation.setup.pool.join

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.NominationPoolState
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolJoinFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.presentation.pools.SelectPoolViewModel
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Retained real setup/select handlers after an asynchronous confirmation consumes their draft. */
@OptIn(ExperimentalCoroutinesApi::class)
class PoolJoinRetainedDraftTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
        on { getString(any(), anyVararg()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
    }
    private val asset = mock<Asset>(defaultAnswer = Answers.RETURNS_DEEP_STUBS).also {
        whenever(it.transferable).thenReturn(BigDecimal.TEN)
        whenever(it.token.configuration.symbol).thenReturn("LOCAL")
        whenever(it.token.configuration.precision).thenReturn(10)
        whenever(it.token.fiatRate).thenReturn(null)
    }
    private val pool = PoolInfo(BigInteger.ONE, "Public fixture pool", BigInteger.TEN,
        NominationPoolState.Open, BigInteger.ONE, ByteArray(32), null, null, null)
    private val shared = StakingPoolSharedStateProvider().apply {
        mainState.set(StakingPoolState(chain = mock<Chain> { on { id } doReturn "local-chain" },
            asset = asset, address = "public-fixture-address", amount = BigDecimal.ONE))
        joinFlowState.set(StakingPoolJoinFlowState(BigDecimal.ONE, pool, mainState.capture()))
    }
    private var minimum = CompletableDeferred<BigInteger>()
    private var minimumRequested = CompletableDeferred<Unit>()
    private val poolInteractor = mock<StakingPoolInteractor> {
        onBlocking { estimateJoinFee(any(), any(), any(), any(), any()) } doReturn BigInteger.ONE
        onBlocking { getAllPools(any()) } doReturn listOf(pool)
        onBlocking { getMinToJoinPool(any()) } doSuspendableAnswer {
            minimumRequested.complete(Unit)
            minimum.await()
        }
    }
    private val stakingInteractor = mock<StakingInteractor> {
        onBlocking { getCurrentMetaAccount() } doSuspendableAnswer { awaitCancellation() }
    }
    private val quickInputs = mock<QuickInputsUseCase> {
        onBlocking { calculateStakingQuickInputs(any(), any(), any(), any()) } doReturn emptyMap()
    }
    private var existentialResult = CompletableDeferred<Result<BigInteger>>()
    private val existential = mock<ExistentialDepositUseCase> {
        onBlocking { invoke(anyOrNull()) } doSuspendableAnswer {
            existentialResult.await()
        }
    }
    private val router = mock<StakingRouter>()
    private val owned = mutableListOf<BaseViewModel>()
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }
    }

    @After fun teardown() {
        owned.forEach { vm ->
            val job = vm.viewModelScope.coroutineContext.job
            vm.viewModelScope.cancel()
            dispatcher.scheduler.runCurrent()
            runBlocking { withTimeout(10_000) { job.join() } }
        }
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        Dispatchers.resetMain()
        assertEquals(emptyList<Throwable>(), uncaught.toList())
    }

    @Test fun `setup fee retains its reviewed wallet after main state replacement`() {
        whenever(asset.metaId).thenReturn(42L)
        val vm = setupViewModel()
        waitForEstimate()
        val replacement = mock<Asset> { on { metaId } doReturn 99L }
        shared.mainState.set(shared.requireMainState.copy(asset = replacement))
        vm.onAmountEntered(BigDecimal.valueOf(2))
        val deadline = System.currentTimeMillis() + 10_000
        fun calls() = mockingDetails(poolInteractor).invocations.filter { it.method.name == "estimateJoinFee" }
        while (calls().size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(calls().size >= 2)
        assertTrue(calls().all { it.arguments[3] == 42L })
    }

    @Test fun `retained setup quick input callback uses its original wallet`() = runBlocking<Unit> {
        whenever(asset.metaId).thenReturn(42L)
        whenever(asset.token.configuration.id).thenReturn("native")
        var fee: (suspend (BigInteger) -> BigInteger)? = null
        whenever(quickInputs.calculateStakingQuickInputs(any(), any(), any(), any())).doSuspendableAnswer {
            fee = it.getArgument(3)
            emptyMap<Double, BigDecimal>()
        }
        setupViewModel()
        waitForEstimate()
        val replacement = mock<Asset> { on { metaId } doReturn 99L }
        shared.mainState.set(shared.requireMainState.copy(asset = replacement))
        val amount = BigInteger.valueOf(777)
        assertEquals(BigInteger.ONE, requireNotNull(fee).invoke(amount))
        val calls = mockingDetails(poolInteractor).invocations.filter { it.method.name == "estimateJoinFee" && it.arguments[2] == amount }
        assertEquals(1, calls.size)
        assertEquals(42L, calls.single().arguments[3])
    }

    @Test fun `retained setup next exits a consumed draft instead of crashing`() {
        val vm = setupViewModel()
        shared.joinFlowState.complete()
        vm.onNextClick()
        verify(router).returnToMain()
        verify(router, never()).openSelectPool()
    }

    @Test fun `retained pool selection next exits a consumed draft instead of crashing`() {
        val vm = SelectPoolViewModel(poolInteractor, shared, router, resources).also { owned += it }
        shared.joinFlowState.complete()
        vm.onNextClick()
        verify(router).returnToMain()
        verify(router, never()).openConfirmJoinPool()
    }

    @Test fun `late setup validation cannot restore a consumed draft`() {
        val vm = setupViewModel()
        beginValidation(vm)
        shared.joinFlowState.complete()
        minimum.complete(BigInteger.ONE)
        existentialResult.complete(Result.success(BigInteger.ZERO))
        dispatcher.scheduler.runCurrent()
        assertNull(shared.joinFlowState.get())
        verify(router, never()).openSelectPool()
    }

    @Test fun `late setup validation preserves a newer draft`() {
        val vm = setupViewModel()
        beginValidation(vm)
        val newer = StakingPoolJoinFlowState(BigDecimal.TEN, pool, shared.mainState.capture())
        shared.joinFlowState.set(newer)
        minimum.complete(BigInteger.ONE)
        existentialResult.complete(Result.success(BigInteger.ZERO))
        dispatcher.scheduler.runCurrent()
        assertSame(newer, shared.joinFlowState.get())
        verify(router, never()).openSelectPool()
    }

    @Test fun `late setup failure cannot present over a consumed draft`() {
        val vm = setupViewModel()
        beginValidation(vm)
        shared.joinFlowState.complete()
        minimum.completeExceptionally(IllegalStateException("old local failure"))
        dispatcher.scheduler.runCurrent()
        verify(router, never()).openAlert(any())
        assertNull(shared.joinFlowState.get())
    }

    @Test fun `back prevents late setup validation from opening pool selection`() {
        val draft = shared.requireJoinState
        val vm = setupViewModel()
        beginValidation(vm)
        vm.onNavigationClick()
        minimum.complete(BigInteger.ONE)
        existentialResult.complete(Result.success(BigInteger.ZERO))
        dispatcher.scheduler.runCurrent()
        assertSame(draft, shared.joinFlowState.get())
        verify(router, never()).openSelectPool()
    }

    @Test fun `current setup validation still publishes the entered amount and advances`() {
        val vm = setupViewModel()
        vm.onAmountEntered(BigDecimal("2"))
        beginValidation(vm)
        minimum.complete(BigInteger.ONE)
        existentialResult.complete(Result.success(BigInteger.ZERO))
        dispatcher.scheduler.runCurrent()
        assertEquals("Router events: ${mockingDetails(router).invocations.map { it.arguments.toList() }}", BigDecimal("2"), shared.requireJoinState.amount)
        verify(router).openSelectPool()
    }


    @Test fun `setup cannot start validation while its reviewed draft has an unresolved submission`() {
        val vm = setupViewModel()
        val draft = shared.requireJoinState
        val claim = requireNotNull(shared.joinFlowState.tryClaim(shared.joinFlowState.capture()))
        waitForEstimate()
        vm.onNextClick()
        dispatcher.scheduler.runCurrent()
        assertFalse(minimumRequested.isCompleted)
        assertSame(draft, shared.joinFlowState.get())
        verify(router, never()).openSelectPool()
        shared.joinFlowState.release(claim)
    }


    @Test fun `retained setup cannot adopt a different draft replaced before next`() {
        val vm = setupViewModel()
        val newer = StakingPoolJoinFlowState(BigDecimal.TEN, pool.copy(poolId = BigInteger.TWO), shared.mainState.capture())
        shared.joinFlowState.set(newer)
        waitForEstimate()
        // A corrected stale screen rejects admission without starting validation.
        vm.onNextClick()
        dispatcher.scheduler.runCurrent()
        if (minimumRequested.isCompleted) {
            minimum.complete(BigInteger.ONE)
            existentialResult.complete(Result.success(BigInteger.ZERO))
            dispatcher.scheduler.runCurrent()
        }
        assertSame(newer, shared.joinFlowState.get())
        runBlocking { verify(poolInteractor, never()).getMinToJoinPool(any()) }
        verify(router, never()).openSelectPool()
        verify(router, never()).returnToMain()
    }

    @Test fun `retained selection cannot adopt a different draft replaced before next`() {
        val vm = SelectPoolViewModel(poolInteractor, shared, router, resources).also { owned += it }
        val newer = StakingPoolJoinFlowState(BigDecimal.TEN, pool.copy(poolId = BigInteger.TWO), shared.mainState.capture())
        shared.joinFlowState.set(newer)
        vm.onNextClick()
        assertSame(newer, shared.joinFlowState.get())
        verify(router, never()).openConfirmJoinPool()
        verify(router, never()).returnToMain()
    }

    @Test fun `back editing remains usable after setup and selection advance the same flow`() {
        val setup = setupViewModel()
        beginValidation(setup)
        minimum.complete(BigInteger.ONE)
        existentialResult.complete(Result.success(BigInteger.ZERO))
        dispatcher.scheduler.runCurrent()
        verify(router).openSelectPool()
        val selection = SelectPoolViewModel(poolInteractor, shared, router, resources).also { owned += it }
        selection.onNextClick()
        verify(router).openConfirmJoinPool()
        minimum = CompletableDeferred()
        minimumRequested = CompletableDeferred()
        existentialResult = CompletableDeferred()
        setup.onAmountEntered(BigDecimal("2"))
        beginValidation(setup)
        minimum.complete(BigInteger.ONE)
        existentialResult.complete(Result.success(BigInteger.ZERO))
        dispatcher.scheduler.runCurrent()
        assertEquals(BigDecimal("2"), shared.requireJoinState.amount)
        verify(router, times(2)).openSelectPool()
    }

    @Test fun `selection can advance its same flow again after returning from confirmation`() {
        val vm = SelectPoolViewModel(poolInteractor, shared, router, resources).also { owned += it }
        vm.onNextClick()
        vm.onNextClick()
        verify(router, times(2)).openConfirmJoinPool()
        assertSame(pool, shared.requireJoinState.selectedPool)
    }


    @Test fun `late setup failure stays silent while a confirmation claims its draft`() {
        val vm = setupViewModel()
        beginValidation(vm)
        val claim = requireNotNull(shared.joinFlowState.tryClaim(shared.joinFlowState.capture()))
        minimum.completeExceptionally(IllegalStateException("old local failure"))
        dispatcher.scheduler.runCurrent()
        verify(router, never()).openAlert(any())
        shared.joinFlowState.release(claim)
    }

    @Test fun `a completed failed submission cannot revive earlier pending setup validation`() {
        val vm = setupViewModel()
        beginValidation(vm)
        val draft = shared.requireJoinState
        val claim = requireNotNull(shared.joinFlowState.tryClaim(shared.joinFlowState.capture()))
        shared.joinFlowState.release(claim)
        minimum.complete(BigInteger.ONE)
        existentialResult.complete(Result.success(BigInteger.ZERO))
        dispatcher.scheduler.runCurrent()
        assertSame(draft, shared.joinFlowState.get())
        verify(router, never()).openSelectPool()
    }

    @Test fun `a completed failed submission cannot revive an earlier setup error`() {
        val vm = setupViewModel()
        beginValidation(vm)
        val claim = requireNotNull(shared.joinFlowState.tryClaim(shared.joinFlowState.capture()))
        shared.joinFlowState.release(claim)
        minimum.completeExceptionally(IllegalStateException("old local failure"))
        dispatcher.scheduler.runCurrent()
        verify(router, never()).openAlert(any())
    }

    @Test fun `departed setup callbacks cannot navigate back twice`() {
        val vm = setupViewModel()
        vm.onNavigationClick()
        vm.onNavigationClick()
        vm.onNextClick()
        verify(router, times(1)).back()
        verify(router, never()).openSelectPool()
    }

    @Test fun `departed selection callbacks cannot navigate back twice or open confirmation`() {
        val vm = SelectPoolViewModel(poolInteractor, shared, router, resources).also { owned += it }
        vm.onBackClick()
        vm.onBackClick()
        vm.onNextClick()
        verify(router, times(1)).back()
        verify(router, never()).openConfirmJoinPool()
    }


    @Test fun `dismissed setup cannot finish validation or navigate from retained callbacks`() {
        val vm = setupViewModel()
        val draft = shared.requireJoinState
        beginValidation(vm)
        vm.onDismissed()
        minimum.complete(BigInteger.ONE)
        existentialResult.complete(Result.success(BigInteger.ZERO))
        dispatcher.scheduler.runCurrent()
        vm.onNextClick()
        vm.onNavigationClick()
        assertSame(draft, shared.joinFlowState.get())
        verify(router, never()).openSelectPool()
        verify(router, never()).back()
    }

    @Test fun `dismissed selection cannot open confirmation or pool information`() {
        val vm = SelectPoolViewModel(poolInteractor, shared, router, resources).also { owned += it }
        vm.onDismissed()
        vm.onNextClick()
        vm.onInfoClick(SelectableListItemState(1, "Public pool", isSelected = true))
        vm.onBackClick()
        verify(router, never()).openConfirmJoinPool()
        verify(router, never()).openPoolInfo(any())
        verify(router, never()).back()
    }

    @Test fun `current pool info remains usable but a superseded selector cannot reopen it`() {
        val vm = SelectPoolViewModel(poolInteractor, shared, router, resources).also { owned += it }
        val item = SelectableListItemState(1, "Public pool", isSelected = true)
        vm.onInfoClick(item)
        verify(router, times(1)).openPoolInfo(1)
        val newer = StakingPoolJoinFlowState(BigDecimal.TEN, pool.copy(poolId = BigInteger.TWO), shared.mainState.capture())
        shared.joinFlowState.set(newer)
        vm.onInfoClick(item)
        verify(router, times(1)).openPoolInfo(1)
        assertSame(newer, shared.joinFlowState.get())
    }

    @Test fun `setup refuses replaced main context before starting validation`() {
        val vm = setupViewModel()
        waitForEstimate()
        shared.mainState.set(shared.requireMainState.copy(address = "new-public-fixture-address"))
        vm.onNextClick()
        runBlocking { verify(poolInteractor, never()).getMinToJoinPool(any()) }
    }

    @Test fun `new setup cannot adopt replaced main context for an old join draft`() {
        shared.mainState.set(shared.requireMainState.copy(address = "new-public-fixture-address"))
        val vm = setupViewModel()
        waitForEstimate()
        vm.onNextClick()
        runBlocking { verify(poolInteractor, never()).getMinToJoinPool(any()) }
    }

    @Test fun `held setup validation cannot publish after main context changes`() {
        val vm = setupViewModel()
        val draft = shared.requireJoinState
        beginValidation(vm)
        shared.mainState.set(shared.requireMainState.copy(address = "new-public-fixture-address"))
        minimum.complete(BigInteger.ONE)
        existentialResult.complete(Result.success(BigInteger.ZERO))
        dispatcher.scheduler.runCurrent()
        assertSame(draft, shared.joinFlowState.get())
        verify(router, never()).openSelectPool()
    }

    @Test fun `held setup refusal stays silent after wallet changes`() {
        val vm = setupViewModel()
        beginValidation(vm)
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        minimum.completeExceptionally(IllegalStateException("old wallet refusal"))
        dispatcher.scheduler.runCurrent()
        verify(router, never()).openAlert(any())
    }

    @Test fun `selector refuses replaced main context for confirmation and info`() {
        val vm = SelectPoolViewModel(poolInteractor, shared, router, resources).also { owned += it }
        shared.mainState.set(shared.requireMainState.copy(address = "new-public-fixture-address"))
        vm.onNextClick()
        vm.onInfoClick(SelectableListItemState(1, "Public pool", isSelected = true))
        verify(router, never()).openConfirmJoinPool()
        verify(router, never()).openPoolInfo(any())
    }

    @Test fun `new selector cannot adopt a different main context for old pool selection`() {
        shared.mainState.set(shared.requireMainState.copy(address = "new-public-fixture-address"))
        val vm = SelectPoolViewModel(poolInteractor, shared, router, resources).also { owned += it }
        vm.onNextClick()
        verify(router, never()).openConfirmJoinPool()
    }

    @Test fun `wallet change and return invalidates setup and selector callbacks`() {
        val setup = setupViewModel()
        val select = SelectPoolViewModel(poolInteractor, shared, router, resources).also { owned += it }
        waitForEstimate()
        repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        setup.onNextClick()
        select.onNextClick()
        runBlocking { verify(poolInteractor, never()).getMinToJoinPool(any()) }
        verify(router, never()).openConfirmJoinPool()
    }

    private fun setupViewModel() = SetupStakingPoolViewModel(
        resources, stakingInteractor, mock(), poolInteractor, router, shared, existential, quickInputs
    ).also { owned += it }

    private fun waitForEstimate() {
        // Fee estimation runs on Default; wait for the local recorder and its state publication.
        val deadline = System.currentTimeMillis() + 10_000
        fun estimated() = mockingDetails(poolInteractor).invocations.any { it.method.name == "estimateJoinFee" }
        while (!estimated() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(estimated())
        Thread.sleep(100)
    }

    private fun beginValidation(vm: SetupStakingPoolViewModel) {
        waitForEstimate()
        vm.onNextClick()
        runBlocking { withTimeout(10_000) { minimumRequested.await() } }
    }
}
