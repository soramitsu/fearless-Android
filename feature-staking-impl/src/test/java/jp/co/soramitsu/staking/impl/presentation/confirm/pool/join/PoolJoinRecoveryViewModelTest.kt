package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

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

/** Actual direct VM, interactor, API and journal. Only finality reads and in-memory preferences are modeled. */
@OptIn(ExperimentalCoroutinesApi::class)
class PoolJoinRecoveryViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val chainId = "11".repeat(32)
    private val account = ByteArray(32) { (it + 1).toByte() }
    private val address = account.toAddress(42)
    private val assetConfig = mock<ChainAsset> {
        on { chainId } doReturn this@PoolJoinRecoveryViewModelTest.chainId
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
    private val interactor = StakingPoolInteractor(api, mock(), mock(), mock(), mock(), mock(), mock(), mock())
    private val shared = StakingPoolSharedStateProvider().apply {
        mainState.set(StakingPoolState(chain = chain, asset = Asset.createEmpty(assetConfig, 7, account, minSupportedVersion = null), address = address))
    }
    private val router = mock<StakingRouter>()
    private val resources = mock<ResourceManager> { on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" } }
    private var owned: PoolJoinRecoveryViewModel? = null
    private val failures = CopyOnWriteArrayList<Throwable>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Thread.setDefaultUncaughtExceptionHandler { _, e -> failures += e }
    }
    @After fun cleanup() {
        owned?.let { vm -> val job = vm.viewModelScope.coroutineContext.job; vm.viewModelScope.cancel(); runBlocking { withTimeout(10_000) { job.join() } } }
        verifyNoInteractions(extrinsics)
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        Dispatchers.resetMain()
        assertEquals(emptyList<Throwable>(), failures.toList())
    }

    @Test fun `local pending lookup retains exact original details without any fresh draft or network read`() {
        val payment = retain()
        val vm = viewModel()
        await { vm.viewState.value.transactionHash != null }
        with(vm.viewState.value) {
            assertEquals(payment.transactionHash, transactionHash)
            assertEquals("37 LOCAL", amount)
            assertEquals("8", poolId)
            assertEquals(address, this.address)
            assertEquals("Local original network", network)
            assertTrue(canCheck)
            assertFalse(canReturn)
        }
        assertNull(shared.joinFlowState.get())
        verifyNoInteractions(reader)
    }

    @Test fun `standalone recovery preserves original creation fields without creating a draft`() {
        val details = PoolJoinJournal.CreateDetails("Original\n池", "public-nominator", "public-bouncer")
        val payment = journal.retain(scope, BigInteger.valueOf(37), BigInteger.valueOf(8),
            "0x01020304", "12".repeat(32), anchor, details)
        val vm = loaded()
        assertEquals(details, vm.viewState.value.create)
        assertEquals(payment.transactionHash, vm.viewState.value.transactionHash)
        assertNull(shared.createFlowState.get())
        assertNull(shared.joinFlowState.get())
        verifyNoInteractions(reader)
    }

    @Test fun `no pending is explicit return state without finalized claim`() {
        val vm = viewModel()
        await { vm.viewState.value.canReturn }
        assertEquals("text-${R.string.pool_join_recovery_empty}", vm.viewState.value.message)
        assertFalse(vm.viewState.value.finalized)
        assertFalse(vm.viewState.value.canCheck)
        vm.onReturnToPool(); vm.onReturnToPool()
        verify(router, times(1)).back()
        verifyNoInteractions(reader)
    }

    @Test fun `restored fragment with missing context remains usable and never throws`() {
        shared.mainState.complete()
        val vm = viewModel()
        assertEquals("text-${R.string.pool_join_recovery_unavailable}", vm.viewState.value.message)
        assertFalse(vm.viewState.value.canCheck)
        assertFalse(vm.viewState.value.canReturn)
        vm.onBackClick(); vm.onBackClick()
        verify(router, times(1)).back()
        verifyNoInteractions(reader)
    }

    @Test fun `unreadable pending record remains unresolved`() {
        fixture.values[scope.key] = "unknown-record"
        val vm = viewModel()
        await { vm.viewState.value.message == "text-${R.string.pool_join_recovery_unavailable}" }
        vm.onCheckPending(); vm.onReturnToPool()
        assertEquals("unknown-record", fixture.values[scope.key])
        verifyNoInteractions(reader, router)
    }

    @Test fun `legacy original without anchor stays readable and cannot be checked or replaced`() {
        val payment = retain(null)
        val vm = loaded()
        assertEquals("text-${R.string.pool_join_recovery_legacy}", vm.viewState.value.message)
        vm.onCheckPending(); vm.onReturnToPool()
        assertEquals(payment, journal.read(scope))
        assertFalse(vm.viewState.value.canCheck)
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
        vm.onCheckPending(); await { vm.viewState.value.canCheckOlder }
        vm.onCheckPending(true); await { calls.size == 2 && vm.viewState.value.canCheck }
        vm.onCheckPending(); await { calls.size == 3 && vm.viewState.value.canCheck }
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
        assertFalse(vm.viewState.value.canCheck)
        held.complete(FinalizedExtrinsicReader.SearchResult(null, null, 1))
        await { vm.viewState.value.canCheck }
        verify(reader, times(1)).search(any(), any(), anyOrNull(), any(), anyOrNull())
    }

    @Test fun `final inclusion archives original and never consumes a different join draft`(): Unit = runBlocking {
        val payment = retain()
        val draft = StakingPoolJoinFlowState(BigDecimal.TEN, mock(), shared.mainState.capture())
        shared.joinFlowState.set(draft)
        whenever(reader.search(any(), any(), anyOrNull(), any(), anyOrNull())).thenReturn(included(payment))
        val vm = loaded(); vm.onCheckPending()
        await { vm.viewState.value.finalized }
        assertEquals(R.string.pool_join_recovery_return, vm.viewState.value.returnLabelRes)
        assertNull(journal.read(scope))
        assertEquals(payment, journal.readFinalized(scope, payment.attemptId)?.payment)
        assertSame(draft, shared.joinFlowState.get())
        vm.onReturnToPool(); vm.onReturnToPool()
        verify(router, times(1)).back()
        verify(router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun `archive acknowledged immediately after cancellation preserves explicit return`(): Unit = runBlocking {
        val payment = retain()
        whenever(reader.search(any(), any(), anyOrNull(), any(), anyOrNull())).thenReturn(included(payment))
        val vm = loaded()
        fixture.beforeCommit = { vm.viewModelScope.cancel() }
        vm.onCheckPending()
        await { vm.viewState.value.finalized }
        assertEquals(R.string.pool_join_recovery_return, vm.viewState.value.returnLabelRes)
        assertEquals(payment, journal.readFinalized(scope, payment.attemptId)?.payment)
        vm.onReturnToPool()
        verify(router).back()
    }

    @Test fun `failed archive leaves unresolved action with no return`(): Unit = runBlocking {
        val payment = retain()
        whenever(reader.search(any(), any(), anyOrNull(), any(), anyOrNull())).thenReturn(included(payment))
        fixture.failCommit = true
        val vm = loaded(); vm.onCheckPending()
        await { vm.viewState.value.message == "text-${R.string.pool_join_recovery_unavailable}" }
        assertFalse(vm.viewState.value.finalized)
        assertFalse(vm.viewState.value.canReturn)
        vm.onReturnToPool()
        verifyNoInteractions(router)
    }

    @Test fun `leaving during pending lookup cannot publish its late result`() {
        retain()
        val gate = CountDownLatch(1); val entered = CountDownLatch(1)
        whenever(fixture.preferences.getString(scope.key)).thenAnswer {
            entered.countDown(); check(gate.await(10, TimeUnit.SECONDS)); fixture.values[scope.key]
        }
        val vm = viewModel()
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        vm.onBackClick(); vm.onDismissed(); gate.countDown()
        await { vm.viewModelScope.coroutineContext.job.children.none() }
        assertNull(vm.viewState.value.transactionHash)
        verify(router).back()
        verifyNoInteractions(reader)
    }

    @Test fun `wallet change prevents fresh check and return`() {
        retain(); val vm = loaded()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        vm.onCheckPending(); vm.onReturnToPool()
        verifyNoInteractions(reader, router)
    }

    @Test fun `network change and restoration cannot revive recovery`() {
        retain(); val vm = loaded()
        revision.changed(StakingAssetSelection.Pool("other", "native"))
        revision.changed(StakingAssetSelection.Pool(chainId, "native"))
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
        assertFalse(vm.viewState.value.finalized)
        vm.onReturnToPool()
        verify(router).back()
        verify(router, never()).returnToMain()
    }

    private fun retain(originalAnchor: FinalizedExtrinsicReader.Anchor? = anchor) = journal.retain(scope,
        BigInteger.valueOf(37), BigInteger.valueOf(8), "0x01020304", "12".repeat(32), originalAnchor)
    private fun included(payment: PoolJoinJournal.Payment) = FinalizedExtrinsicReader.SearchResult(
        FinalizedExtrinsicReader.Inclusion("0x$chainId", payment.transactionHash, "0x" + "30".repeat(32),
            BigInteger.valueOf(11), 0, "0x" + "40".repeat(32), BigInteger.valueOf(12)), null, 2)
    private fun viewModel() = PoolJoinRecoveryViewModel(resources, shared, interactor, router, selected).also { owned = it }
    private fun loaded() = viewModel().also { vm -> await { vm.viewState.value.transactionHash != null } }
    private fun await(condition: () -> Boolean) = runBlocking { withTimeout(10_000) { while (!condition()) yield() } }
}
