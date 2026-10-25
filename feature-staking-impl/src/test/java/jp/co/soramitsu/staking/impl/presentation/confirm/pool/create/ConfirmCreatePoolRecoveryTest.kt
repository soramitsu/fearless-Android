package jp.co.soramitsu.staking.impl.presentation.confirm.pool.create

import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal
import jp.co.soramitsu.staking.impl.data.repository.PoolJoinPreferencesFixture
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolApi
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolJoinFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual create VM, API and journal with an interactor adapter and local fee/finality fixtures; no keys or real network. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmCreatePoolRecoveryTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val chainId = "11".repeat(32)
    private val account = ByteArray(32) { (it + 1).toByte() }
    private val address = account.toAddress(42)
    private val assetConfig = mock<ChainAsset> {
        on { chainId } doReturn this@ConfirmCreatePoolRecoveryTest.chainId
        on { id } doReturn "native"
        on { symbol } doReturn "LOCAL"
        on { precision } doReturn 0
    }
    private val chain = mock<Chain> {
        on { id } doReturn chainId
        on { name } doReturn "Local original network"
        on { ecosystem } doReturn Ecosystem.Substrate
    }
    private val scope = PoolJoinJournal.Scope(7, chainId, account.toHexString(false))
    private val anchor = FinalizedExtrinsicReader.Anchor("0x$chainId", BigInteger.TEN, "0x" + "20".repeat(32))
    private val fixture = PoolJoinPreferencesFixture()
    private val journal = fixture.journal()
    private val extrinsics = mock<ExtrinsicService>()
    private val reader = mock<FinalizedExtrinsicReader>()
    private val revision = StakingSelectionRevision()
    private val selected = mock<StakingSharedState> { on { selectionRevision } doReturn revision }
    private val api = StakingPoolApi(extrinsics, selected, mock(), journal, reader)
    private val lookupJobs = CopyOnWriteArrayList<kotlinx.coroutines.Job>()
    private var failFee = false
    private var mutationCalls = 0
    private var expectedMutationCalls = 0
    private var createOperation: suspend (() -> Unit) -> Result<String> = { Result.failure(IllegalStateException("Unexpected creation")) }
    private var pendingLookup: suspend () -> PoolJoinJournal.Payment? = { api.pendingJoin(chain, address, 7) }
    private val interactor = mock<StakingPoolInteractor> {
        onBlocking { getAccountName(any()) } doReturn null
        onBlocking { estimateCreateFee(any(), any(), any(), any(), any(), any(), any(), any()) } doAnswer {
            if (failFee) throw IllegalStateException("Local fee unavailable")
            BigInteger.ONE
        }
        onBlocking { pendingJoin(any(), any(), any()) } doSuspendableAnswer {
            lookupJobs += kotlinx.coroutines.currentCoroutineContext().job
            pendingLookup()
        }
        onBlocking { reconcileJoin(any(), any(), any(), any(), anyOrNull(), any()) } doSuspendableAnswer {
            api.reconcileJoin(it.getArgument(0), it.getArgument(1), it.getArgument(2),
                it.getArgument(3), it.getArgument(4), it.getArgument(5))
        }
        onBlocking { createPool(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } doSuspendableAnswer {
            mutationCalls++
            createOperation(it.getArgument(9))
        }
    }
    private val shared = StakingPoolSharedStateProvider().apply {
        mainState.set(StakingPoolState(chain = chain, asset = Asset.createEmpty(assetConfig, 7, account, minSupportedVersion = null).copy(freeInPlanksState = AssetAmountState.Known(BigInteger.valueOf(1000))), address = address))
        createFlowState.set(jp.co.soramitsu.staking.impl.presentation.common.StakingPoolCreateFlowState(
            "New draft", BigInteger.ONE, 9, "new-nominator", "new-bouncer", mainState.capture()))
    }
    private val router = mock<StakingRouter>()
    private val resources = mock<ResourceManager> { on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" } }
    private var owned: ConfirmCreatePoolViewModel? = null
    private val failures = CopyOnWriteArrayList<Throwable>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Thread.setDefaultUncaughtExceptionHandler { _, e -> failures += e }

    }
    @After fun cleanup() {
        owned?.let { vm -> val job = vm.viewModelScope.coroutineContext.job; vm.viewModelScope.cancel(); runBlocking { withTimeout(10_000) { job.join() } } }
        verifyNoInteractions(extrinsics)
        assertEquals(expectedMutationCalls, mutationCalls)
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        Dispatchers.resetMain()
        assertEquals(emptyList<Throwable>(), failures.toList())
    }

    private val details = PoolJoinJournal.CreateDetails("Original\n池", "old-nominator", "old-bouncer")

    @Test fun `original create is visible and blocks a new confirmation without fee availability`() {
        val payment = retain(); failFee = true
        val vm = loaded()
        with(checkNotNull(vm.recoveryViewState.value)) {
            assertEquals(payment.transactionHash, transactionHash)
            assertEquals(details, create)
            assertEquals("37 LOCAL", amount)
            assertEquals("8", poolId)
            assertEquals(address, this.address)
            assertTrue(canCheck); assertFalse(canReturn)
        }
        vm.onConfirm(); vm.onConfirm()
        assertEquals(payment, journal.read(scope))
        assertEquals("New draft", shared.requireCreateState.poolName)
        verifyNoInteractions(reader, router)
    }

    @Test fun `empty local scope leaves ordinary confirmation available`() {
        val vm = viewModel()
        awaitFee(vm); awaitInitialLookup()
        assertNull(vm.recoveryViewState.value)
        verifyNoInteractions(reader, router)
    }

    @Test fun `failure after output loads original payment and avoids generic alert`(): Unit = runBlocking {
        expectedMutationCalls = 1
        val entered = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Result<String>>()
        createOperation = { retain(); entered.complete(Unit); response.await() }
        val vm = viewModel(); awaitFee(vm); awaitInitialLookup(); vm.onConfirm()
        withTimeout(10_000) { entered.await() }
        response.complete(Result.failure(IllegalStateException("Local response lost")))
        await { vm.recoveryViewState.value?.transactionHash != null }
        assertEquals(details, vm.recoveryViewState.value?.create)
        verifyNoInteractions(router, reader)
    }

    @Test fun `matching node acknowledgement shows retained review without claiming pool creation`(): Unit = runBlocking {
        expectedMutationCalls = 1
        val entered = CompletableDeferred<Unit>()
        val attempt = CompletableDeferred<kotlinx.coroutines.Job>()
        val response = CompletableDeferred<Result<String>>()
        createOperation = { onSubmitted ->
            attempt.complete(kotlinx.coroutines.currentCoroutineContext().job)
            val payment = retain()
            journal.acknowledge(payment)
            onSubmitted()
            entered.complete(Unit)
            response.await()
        }
        val vm = viewModel(); awaitFee(vm); awaitInitialLookup(); vm.onConfirm()
        withTimeout(10_000) { entered.await() }
        response.complete(Result.success(checkNotNull(journal.read(scope)).transactionHash))
        withTimeout(10_000) { attempt.await().join() }
        verify(router, never()).returnToMain()
        verify(router, never()).openStartSelectValidators()
        verify(router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        assertNull(shared.createFlowState.get())
        assertNull(shared.selectValidatorsState.get())
        await { vm.recoveryViewState.value?.transactionHash != null }
        assertFalse(checkNotNull(vm.recoveryViewState.value).finalized)
        assertFalse(checkNotNull(vm.recoveryViewState.value).canReturn)
    }

    @Test fun `matching acknowledgement survives cancelled dispatch and shows read only original`(): Unit = runBlocking {
        expectedMutationCalls = 1
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        createOperation = { onSubmitted ->
            val payment = retain()
            entered.complete(Unit); resume.await()
            kotlinx.coroutines.currentCoroutineContext().job.cancel()
            journal.acknowledge(payment); onSubmitted()
            throw kotlinx.coroutines.CancellationException("Local cancelled dispatch back")
        }
        val vm = viewModel(); awaitFee(vm); awaitInitialLookup(); vm.onConfirm()
        withTimeout(10_000) { entered.await() }; resume.complete(Unit)
        await { vm.recoveryViewState.value?.transactionHash != null }
        assertNull(shared.createFlowState.get())
        assertTrue(checkNotNull(journal.read(scope)).acknowledged)
        assertFalse(checkNotNull(vm.recoveryViewState.value).finalized)
        assertFalse(checkNotNull(vm.recoveryViewState.value).canReturn)
        verifyNoInteractions(router, reader)
    }

    @Test fun `acknowledgement after Back cannot resurrect its departed review`(): Unit = runBlocking {
        checkDepartedAcknowledgement { it.onBackClick() }
        verify(router).back()
    }

    @Test fun `acknowledgement after draft replacement preserves replacement and has no stale recovery`(): Unit = runBlocking {
        checkDepartedAcknowledgement { shared.createFlowState.set(shared.requireCreateState.copy(poolName = "Replacement")) }
        assertEquals("Replacement", shared.requireCreateState.poolName)
        verifyNoInteractions(router)
    }

    private suspend fun checkDepartedAcknowledgement(depart: (ConfirmCreatePoolViewModel) -> Unit) {
        expectedMutationCalls = 1
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        createOperation = { onSubmitted ->
            val payment = retain()
            entered.complete(Unit); resume.await()
            journal.acknowledge(payment); onSubmitted()
            Result.success(payment.transactionHash)
        }
        val vm = viewModel(); awaitFee(vm); awaitInitialLookup(); vm.onConfirm()
        withTimeout(10_000) { entered.await() }
        depart(vm); resume.complete(Unit)
        withTimeout(10_000) { lookupJobs.last().join() }
        assertTrue(checkNotNull(journal.read(scope)).acknowledged)
        assertNull(vm.recoveryViewState.value)
        assertNull(shared.selectValidatorsState.get())
        verify(router, never()).returnToMain()
        verify(router, never()).openStartSelectValidators()
        verify(router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        verifyNoInteractions(reader)
    }

    @Test fun `unreadable original blocks confirmation without changing bytes`() {
        fixture.values[scope.key] = "unknown-record"
        val vm = viewModel()
        await { vm.recoveryViewState.value != null }
        vm.onConfirm(); vm.onCheckPending(); vm.onReturnToPool()
        assertFalse(checkNotNull(vm.recoveryViewState.value).canCheck)
        assertEquals("unknown-record", fixture.values[scope.key])
        verifyNoInteractions(reader, router)
    }

    @Test fun `historical join in shared scope is readable and blocks creation`() {
        val original = journal.retain(scope, BigInteger.TEN, BigInteger.ONE, "0x01020304", "12".repeat(32))
        val vm = loaded(); vm.onConfirm(); vm.onCheckPending()
        assertNull(vm.recoveryViewState.value?.create)
        assertFalse(checkNotNull(vm.recoveryViewState.value).canCheck)
        assertEquals(original, journal.read(scope))
        verifyNoInteractions(reader, router)
    }
    @Test fun `recent and older cursors are exact and recent resets to latest head`(): Unit = runBlocking {
        val payment = retain()
        val calls = CopyOnWriteArrayList<BigInteger?>()
        whenever(reader.search(any(), any(), anyOrNull(), any(), anyOrNull())).doAnswer {
            assertEquals(chainId, it.getArgument<String>(0))
            assertEquals(payment.signedExtrinsic, it.getArgument<String>(1))
            assertEquals(anchor, it.getArgument<FinalizedExtrinsicReader.Anchor>(4))
            calls += it.getArgument<BigInteger?>(2)
            FinalizedExtrinsicReader.SearchResult(null, BigInteger.valueOf(50), 32)
        }
        val vm = loaded()
        vm.onCheckPending(true)
        assertEquals(0, calls.size)
        vm.onCheckPending(); await { checkNotNull(vm.recoveryViewState.value).canCheckOlder }
        vm.onCheckPending(true); await { calls.size == 2 && checkNotNull(vm.recoveryViewState.value).canCheck }
        vm.onCheckPending(); await { calls.size == 3 && checkNotNull(vm.recoveryViewState.value).canCheck }
        assertEquals(listOf(null, BigInteger.valueOf(50), null), calls.toList())
        assertEquals(payment, journal.read(scope))
    }

    @Test fun `double tap makes one read while original is held`(): Unit = runBlocking {
        retain()
        val held = CompletableDeferred<FinalizedExtrinsicReader.SearchResult>()
        val entered = CompletableDeferred<Unit>()
        whenever(reader.search(any(), any(), anyOrNull(), any(), anyOrNull())).doSuspendableAnswer {
            entered.complete(Unit); held.await()
        }
        val vm = loaded()
        vm.onCheckPending(); vm.onCheckPending(); withTimeout(10_000) { entered.await() }
        assertFalse(checkNotNull(vm.recoveryViewState.value).canCheck)
        held.complete(FinalizedExtrinsicReader.SearchResult(null, null, 1))
        await { checkNotNull(vm.recoveryViewState.value).canCheck }
        verify(reader, times(1)).search(any(), any(), anyOrNull(), any(), anyOrNull())
    }

    @Test fun `final inclusion archives original and never consumes a different join draft`(): Unit = runBlocking {
        val payment = retain()
        val draft = StakingPoolJoinFlowState(BigDecimal.TEN, mock(), shared.mainState.capture())
        shared.joinFlowState.set(draft)
        whenever(reader.search(any(), any(), anyOrNull(), any(), anyOrNull())).thenReturn(included(payment))
        val vm = loaded(); vm.onCheckPending()
        await { checkNotNull(vm.recoveryViewState.value).finalized }
        assertEquals(R.string.pool_recovery_return_earn, vm.recoveryViewState.value?.returnLabelRes)
        assertNull(journal.read(scope))
        assertEquals(payment, journal.readFinalized(scope, payment.attemptId)?.payment)
        assertSame(draft, shared.joinFlowState.get())
        assertEquals("New draft", shared.requireCreateState.poolName)
        vm.onReturnToPool(); vm.onReturnToPool()
        verify(router, times(1)).returnToMain()
        verify(router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `archive acknowledged immediately after cancellation preserves explicit return`(): Unit = runBlocking {
        val payment = retain()
        whenever(reader.search(any(), any(), anyOrNull(), any(), anyOrNull())).thenReturn(included(payment))
        val vm = loaded()
        fixture.beforeCommit = { vm.viewModelScope.cancel() }
        vm.onCheckPending()
        await { checkNotNull(vm.recoveryViewState.value).finalized }
        assertEquals(R.string.pool_recovery_return_earn, vm.recoveryViewState.value?.returnLabelRes)
        assertEquals(payment, journal.readFinalized(scope, payment.attemptId)?.payment)
        vm.onReturnToPool()
        verify(router).returnToMain()
    }

    @Test fun `failed archive leaves unresolved action with no return`(): Unit = runBlocking {
        val payment = retain()
        whenever(reader.search(any(), any(), anyOrNull(), any(), anyOrNull())).thenReturn(included(payment))
        fixture.failCommit = true
        val vm = loaded(); vm.onCheckPending()
        await { checkNotNull(vm.recoveryViewState.value).message == "text-${R.string.pool_join_recovery_unavailable}" }
        assertFalse(checkNotNull(vm.recoveryViewState.value).finalized)
        assertFalse(checkNotNull(vm.recoveryViewState.value).canReturn)
        vm.onReturnToPool()
        verifyNoInteractions(router)
    }

    @Test fun `leaving during pending lookup cannot publish its late result`() {
        retain()
        val gate = CountDownLatch(1); val entered = CountDownLatch(1)
        whenever(fixture.preferences.getString(scope.key)).thenAnswer {
            entered.countDown(); check(gate.await(10, TimeUnit.SECONDS)); fixture.values[scope.key]
        }
        val finished = CountDownLatch(1)
        pendingLookup = {
            try { api.pendingJoin(chain, address, 7) } finally { finished.countDown() }
        }
        val vm = viewModel()
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        vm.onBackClick(); vm.onDismissed(); gate.countDown()
        assertTrue(finished.await(10, TimeUnit.SECONDS))
        awaitInitialLookup()
        assertNull(vm.recoveryViewState.value)
        verify(router).back()
        verifyNoInteractions(reader)
    }

    @Test fun `wallet change prevents fresh check and return`() {
        retain(); val vm = loaded()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        vm.onCheckPending(); vm.onReturnToPool()
        verifyNoInteractions(reader, router)
    }

    @Test fun `main replacement cannot revive recovery`() {
        retain(); val vm = loaded()
        shared.mainState.set(shared.requireMainState.copy())
        vm.onCheckPending(); vm.onReturnToPool()
        verifyNoInteractions(reader, router)
    }

    @Test fun `dismissal rejects future check and repeated back`() {
        retain(); val vm = loaded()
        vm.onDismissed(); vm.onCheckPending(); vm.onBackClick()
        verifyNoInteractions(reader, router)
    }

    @Test fun `cleared view model revokes callbacks`() {
        retain(); val vm = loaded()
        ViewModelStore().apply { put("recovery", vm); clear() }
        vm.onCheckPending(); vm.onBackClick()
        verifyNoInteractions(reader, router)
    }

    @Test fun `late finality after departure still archives without stale success or navigation`(): Unit = runBlocking {
        val payment = retain()
        val held = CompletableDeferred<FinalizedExtrinsicReader.SearchResult>(); val entered = CompletableDeferred<Unit>()
        whenever(reader.search(any(), any(), anyOrNull(), any(), anyOrNull())).doSuspendableAnswer { entered.complete(Unit); held.await() }
        val vm = loaded(); vm.onCheckPending(); withTimeout(10_000) { entered.await() }
        vm.onBackClick(); held.complete(included(payment))
        await { journal.readFinalized(scope, payment.attemptId) != null }
        assertFalse(checkNotNull(vm.recoveryViewState.value).finalized)
        vm.onReturnToPool()
        verify(router).back()
        verify(router, never()).returnToMain()
    }

    private fun retain() = journal.retain(scope, BigInteger.valueOf(37), BigInteger.valueOf(8),
        "0x01020304", "12".repeat(32), anchor, details)
    private fun included(payment: PoolJoinJournal.Payment) = FinalizedExtrinsicReader.SearchResult(
        FinalizedExtrinsicReader.Inclusion("0x$chainId", payment.transactionHash, "0x" + "30".repeat(32),
            BigInteger.valueOf(11), 0, "0x" + "40".repeat(32), BigInteger.valueOf(12)), null, 2)
    private fun viewModel() = ConfirmCreatePoolViewModel(
        mock<jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor> {
            on { assetFlow(any(), any()) } doReturn kotlinx.coroutines.flow.emptyFlow()
        }, object : jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase {
            override suspend fun invoke(chainAsset: ChainAsset) = Result.success(BigInteger.ZERO)
        }, shared, interactor, resources, router, interactor
    ).also { owned = it }
    private fun loaded() = viewModel().also { vm -> await { vm.recoveryViewState.value?.transactionHash != null } }
    private fun awaitInitialLookup() = runBlocking {
        withTimeout(10_000) {
            while (lookupJobs.isEmpty()) yield()
            lookupJobs.first().join()
        }
    }
    private fun awaitFee(vm: ConfirmCreatePoolViewModel) = await { vm.feeViewStateFlow.value.value != null }
    private fun await(condition: () -> Boolean) = runBlocking { withTimeout(10_000) { while (!condition()) yield() } }
}
