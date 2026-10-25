package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

import jp.co.soramitsu.staking.impl.data.repository.PoolJoinPreferencesFixture
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.core.extrinsic.keypair_provider.GuardedKeypairProvider
import jp.co.soramitsu.core.extrinsic.keypair_provider.WalletBoundKeypairProvider
import jp.co.soramitsu.core.extrinsic.mortality.Mortality
import jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.never
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger

import jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.doSuspendableAnswer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolApi
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolJoinFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Real caller, interactor, API, extrinsic service and SCALE builder; public keys and local RPC only. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmJoinPoolExecutionOwnershipTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<Harness>()
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }
    }
    @After fun teardown() {
        for (h in owned) {
            h.resume.complete(Unit)
            h.outputResume.countDown()
            h.vm.viewModelScope.cancel()
            runBlocking { withTimeout(10_000) { h.vm.viewModelScope.coroutineContext.job.join() } }
        }
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        Dispatchers.resetMain()
        assertEquals(emptyList<Throwable>(), uncaught.toList())
    }

    @Test fun `held validator query cannot hide local recovery or its Back action`() {
        val h = recoveryHarness(heldValidators = true)
        await { h.entered.isCompleted }
        runBlocking { withTimeout(5_000) { h.vm.viewState.first { it.recovery != null } } }
        assertFalse(h.vm.viewState.value.isLoading)
        h.vm.onBackClick()
        verify(h.router).back()
        assertEquals(0, h.keys.get())
    }

    @Test fun `Back during failure recovery lookup suppresses stale error`() { checkStaleRecoveryError { vm.onBackClick() } }
    @Test fun `wallet change during failure recovery lookup suppresses stale error`() { checkStaleRecoveryError { switchWallet() } }
    @Test fun `draft replacement during thrown failure lookup suppresses stale error`() { checkStaleRecoveryError(thrown = true) {
        shared.joinFlowState.set(shared.requireJoinState.copy(amount = BigDecimal.ONE))
    } }

    @Test fun `cancellation after durable archive preserves a usable explicit return`() {
        val h = recoveryHarness()
        val original = h.pending()
        h.found = true
        val writing = java.util.concurrent.CountDownLatch(1)
        val resumeWrite = java.util.concurrent.CountDownLatch(1)
        h.storage.beforeCommit = {
            h.storage.beforeCommit = null
            writing.countDown()
            check(resumeWrite.await(5, java.util.concurrent.TimeUnit.SECONDS))
        }
        val before = h.vm.viewModelScope.coroutineContext.job.children.toSet()
        h.vm.onCheckPending()
        assertTrue(writing.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val checkJob = (h.vm.viewModelScope.coroutineContext.job.children.toSet() - before).single()
        checkJob.cancel()
        resumeWrite.countDown()
        runBlocking { withTimeout(10_000) { checkJob.join() } }
        assertEquals(original, h.storage.journal().readFinalized(original.scope, original.attemptId)?.payment)
        assertEquals(null, h.shared.joinFlowState.get())
        // Completing the check job does not join the separate combine/stateIn collector.
        runBlocking { withTimeout(5_000) { h.vm.viewState.first { it.recovery?.finalized == true } } }
        assertTrue(requireNotNull(h.vm.viewState.value.recovery).finalized)
        assertEquals(jp.co.soramitsu.feature_staking_impl.R.string.pool_recovery_return_earn, h.vm.viewState.value.recovery?.returnLabelRes)
        h.vm.onReturnToPool()
        verify(h.router).returnToMain()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    private fun checkStaleRecoveryError(thrown: Boolean = false, depart: Harness.() -> Unit) {
        val h = Harness().also { owned += it }
        await { h.estimated.get() == 1 && h.completedPendingReads.get() == 1 }
        h.boundary = "pending-lookup"
        if (thrown) runBlocking {
            org.mockito.kotlin.doThrow(IllegalStateException("local direct failure"))
                .whenever(h.interactor).joinPool(any(), any(), any(), any(), any(), any(), any())
        } else {
            h.failBeforeAdmission = true
            h.transportFailure = IllegalStateException("local pre-admission failure")
        }
        h.vm.onConfirm()
        await { h.entered.isCompleted }
        h.depart()
        h.resume.complete(Unit)
        runBlocking { withTimeout(10_000) { h.shared.joinFlowState.submissionInProgress.first { !it } } }
        assertEquals(null, h.vm.errorLiveData.value)
        assertEquals(null, h.vm.viewState.value.recovery)
        assertEquals(0, h.sent.get())
    }

    @Test fun `recovery shows original amount pool and hash instead of current draft`() {
        val h = recoveryHarness()
        val state = requireNotNull(h.vm.viewState.value.recovery)
        assertEquals("37 LOCAL", state.amount)
        assertEquals("8", state.poolId)
        assertEquals(h.pending().transactionHash, state.transactionHash)
        assertEquals(h.account.toAddress(42), state.address)
        assertEquals(h.chain.name, state.network)
        assertTrue(state.canCheck)
        assertFalse(state.canCheckOlder)
        h.vm.onConfirm()
        h.vm.onCheckPending(true)
        assertEquals(0, h.keys.get())
        assertEquals(0, h.sent.get())
        assertTrue(h.checkedCursors.isEmpty())
    }

    @Test fun `misses preserve original bytes and recent check resets older cursor`() {
        val h = recoveryHarness()
        val original = h.storage.values.toMap()
        h.nextBefore = BigInteger.valueOf(64)
        checkPending(h)
        assertTrue(requireNotNull(h.vm.viewState.value.recovery).canCheckOlder)
        checkPending(h, older = true)
        checkPending(h)
        assertEquals(listOf(null, BigInteger.valueOf(64), null), h.checkedCursors.toList())
        assertEquals(original, h.storage.values.toMap())
        assertEquals(0, h.keys.get())
        assertEquals(0, h.sent.get())
    }

    @Test fun `legacy unanchored attempt remains visible without unsafe reconciliation`() {
        val h = recoveryHarness(anchored = false)
        val original = h.storage.values.toMap()
        assertFalse(requireNotNull(h.vm.viewState.value.recovery).canCheck)
        h.vm.onCheckPending()
        h.vm.onConfirm()
        assertTrue(h.checkedCursors.isEmpty())
        assertEquals(original, h.storage.values.toMap())
        assertEquals(0, h.keys.get())
    }

    @Test fun `read failure retains original payment and allows a later read only retry`() {
        val h = recoveryHarness()
        val original = h.storage.values.toMap()
        h.searchFailure = IllegalStateException("local read failure")
        checkPending(h)
        assertEquals(original, h.storage.values.toMap())
        assertTrue(requireNotNull(h.vm.viewState.value.recovery).canCheck)
        h.searchFailure = null
        checkPending(h)
        assertEquals(2, h.checkedCursors.size)
        assertEquals(original, h.storage.values.toMap())
        assertEquals(0, h.keys.get())
    }

    @Test fun `finalized inclusion archives bytes consumes review and waits for explicit return`() {
        val h = recoveryHarness()
        val original = h.pending()
        h.found = true
        checkPending(h)
        assertEquals(null, h.storage.journal().read(original.scope))
        assertEquals(original, h.storage.journal().readFinalized(original.scope, original.attemptId)?.payment)
        assertEquals(null, h.shared.joinFlowState.get())
        assertTrue(requireNotNull(h.vm.viewState.value.recovery).finalized)
        assertEquals(jp.co.soramitsu.feature_staking_impl.R.string.pool_recovery_return_earn, h.vm.viewState.value.recovery?.returnLabelRes)
        verify(h.router, never()).returnToMain()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        h.vm.onConfirm()
        h.vm.onReturnToPool()
        h.vm.onReturnToPool()
        verify(h.router).returnToMain()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        assertEquals(0, h.keys.get())
        assertEquals(0, h.sent.get())
    }

    @Test fun `failed archive never reports finalization or consumes the review`() {
        val h = recoveryHarness()
        val original = h.pending()
        h.found = true
        h.storage.failCommit = true
        checkPending(h)
        assertFalse(requireNotNull(h.vm.viewState.value.recovery).finalized)
        assertTrue(h.shared.joinFlowState.get() != null)
        org.junit.Assert.assertThrows(IllegalStateException::class.java) { h.storage.journal().requireAvailable(original.scope) }
        h.vm.onReturnToPool()
        h.vm.onConfirm()
        verify(h.router, never()).returnToMain()
        assertEquals(0, h.keys.get())
    }

    @Test fun `Back during reconciliation cannot navigate after the result`() { checkDepartedRecovery { vm.onBackClick() } }
    @Test fun `dismissal during reconciliation cannot navigate after the result`() { checkDepartedRecovery { vm.onDismissed() } }
    @Test fun `wallet change during reconciliation cannot navigate after the result`() { checkDepartedRecovery { switchWallet() } }
    @Test fun `replaced draft during reconciliation is preserved`() { checkDepartedRecovery(replaced = true) {
        shared.joinFlowState.set(shared.requireJoinState.copy(amount = BigDecimal.valueOf(99)))
    } }

    @Test fun `cancelled check retains the attempt and releases the claim`() {
        val h = recoveryHarness()
        val original = h.storage.values.toMap()
        h.boundary = "reconciliation"
        h.vm.onCheckPending()
        await { h.entered.isCompleted }
        h.vm.viewModelScope.cancel()
        runBlocking { withTimeout(10_000) { h.shared.joinFlowState.submissionInProgress.first { !it } } }
        assertEquals(original, h.storage.values.toMap())
        assertEquals(0, h.keys.get())
    }

    @Test fun `double check shares one exact attempt and does not block Back`() {
        val h = recoveryHarness()
        h.boundary = "reconciliation"
        h.vm.onCheckPending()
        await { h.entered.isCompleted }
        h.vm.onCheckPending()
        h.vm.onConfirm()
        assertEquals(1, h.checkedCursors.size)
        h.vm.onBackClick()
        verify(h.router).back()
        assertFalse(h.vm.viewState.value.isLoading)
        h.resume.complete(Unit)
        runBlocking { withTimeout(10_000) { h.shared.joinFlowState.submissionInProgress.first { !it } } }
        assertEquals(0, h.keys.get())
    }

    @Test fun `new draft after inclusion cannot be cleared by old return action`() {
        val h = recoveryHarness()
        h.found = true
        checkPending(h)
        val replacement = StakingPoolJoinFlowState(BigDecimal.ONE, h.pool, h.shared.mainState.capture())
        h.shared.joinFlowState.set(replacement)
        h.vm.onReturnToPool()
        assertEquals(replacement, h.shared.joinFlowState.get())
        verify(h.router, never()).returnToMain()
    }

    @Test fun `pending API uses exact wallet network and account`() = runBlocking {
        val h = recoveryHarness()
        assertEquals(h.pending(), h.api.pendingJoin(h.chain, h.account.toAddress(42), 7))
        assertEquals(null, h.api.pendingJoin(h.chain, h.account.toAddress(42), 8))
        assertEquals(null, h.api.pendingJoin(h.chain, ByteArray(32) { 6 }.toAddress(42), 7))
        val otherChain = mock<Chain> { on { id } doReturn "92".repeat(32); on { ecosystem } doReturn Ecosystem.Substrate }
        assertEquals(null, h.api.pendingJoin(otherChain, h.account.toAddress(42), 7))
        assertEquals(0, h.keys.get())
    }

    @Test fun `unreadable pending record disables fresh confirmation without changing storage`() {
        val storage = PoolJoinPreferencesFixture()
        val scope = jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal.Scope(7, GENESIS, "05".repeat(32))
        storage.values[scope.key] = "unknown future record"
        val h = Harness(storage).also { owned += it }
        await { h.vm.viewState.value.recovery != null }
        h.vm.onConfirm()
        h.vm.onCheckPending()
        assertFalse(requireNotNull(h.vm.viewState.value.recovery).canCheck)
        assertEquals(mapOf(scope.key to "unknown future record"), storage.values.toMap())
        assertEquals(0, h.keys.get())
    }

    private fun recoveryHarness(anchored: Boolean = true, heldValidators: Boolean = false): Harness {
        val storage = PoolJoinPreferencesFixture()
        val scope = jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal.Scope(7, GENESIS, "05".repeat(32))
        storage.journal().retain(scope, BigInteger.valueOf(37), BigInteger.valueOf(8), "0x01020304", "11".repeat(32),
            if (anchored) jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader.Anchor("0x" + GENESIS, BigInteger.ZERO, "0x" + "20".repeat(32)) else null)
        return Harness(storage, if (heldValidators) "validators" else null).also { h ->
            owned += h
            if (!heldValidators) await { h.vm.viewState.value.recovery != null && h.estimated.get() == 1 }
        }
    }

    private fun checkPending(h: Harness, older: Boolean = false) {
        val before = h.vm.viewModelScope.coroutineContext.job.children.toSet()
        h.vm.onCheckPending(older)
        val jobs = h.vm.viewModelScope.coroutineContext.job.children.toSet() - before
        runBlocking { withTimeout(10_000) { jobs.forEach { it.join() } } }
        runBlocking { withTimeout(10_000) {
            h.shared.joinFlowState.submissionInProgress.first { !it }
            h.vm.viewState.first { it.recovery?.canCheck == true || it.recovery?.finalized == true }
        } }
    }

    private fun checkDepartedRecovery(replaced: Boolean = false, depart: Harness.() -> Unit) {
        val h = recoveryHarness()
        val original = h.pending()
        h.found = true
        h.boundary = "reconciliation"
        val before = h.vm.viewModelScope.coroutineContext.job.children.toSet()
        h.vm.onCheckPending()
        await { h.entered.isCompleted }
        val checking = (h.vm.viewModelScope.coroutineContext.job.children.toSet() - before).single()
        h.depart()
        val state = h.shared.joinFlowState.get()
        h.resume.complete(Unit)
        // Replacing a draft releases its UI claim immediately, before this read-only check finishes.
        runBlocking { withTimeout(10_000) { checking.join() } }
        assertEquals(original, h.storage.journal().readFinalized(original.scope, original.attemptId)?.payment)
        if (replaced) assertEquals(state, h.shared.joinFlowState.get())
        h.vm.onReturnToPool()
        verify(h.router, never()).returnToMain()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        assertEquals(0, h.keys.get())
    }

    @Test fun `unchanged review retains its finalized anchor before signing`() { check("anchor", true) {} }
    @Test fun `Back during original anchor read prevents key read and handoff`() { check("anchor") { vm.onBackClick() } }
    @Test fun `wallet change during original anchor read prevents key read and handoff`() { check("anchor") { switchWallet() } }
    @Test fun `scope cancellation during original anchor read prevents key read and handoff`() { check("anchor") { vm.viewModelScope.cancel() } }

    @Test fun `unchanged review signs and hands off once after held nonce`() { check("nonce", true) {} }
    @Test fun `unchanged review hands off once after held transport`() { check("transport", true) {} }
    @Test fun `wallet change during nonce preparation prevents key read and handoff`() { check("nonce") { switchWallet() } }
    @Test fun `wallet round trip during nonce preparation cannot revive submission`() { check("nonce") { switchWallet(); switchWallet() } }
    @Test fun `main context replacement during nonce preparation prevents handoff`() { check("nonce") { shared.mainState.set(shared.requireMainState.copy()) } }
    @Test fun `join draft replacement during nonce preparation prevents handoff`() { check("nonce") { shared.joinFlowState.set(shared.requireJoinState.copy()) } }
    @Test fun `Back during nonce preparation prevents handoff`() { check("nonce") { vm.onBackClick() } }
    @Test fun `dismissal during nonce preparation prevents handoff`() { check("nonce") { vm.onDismissed() } }
    @Test fun `wallet change after signing before queued send prevents handoff`() { check("transport") { switchWallet() } }
    @Test fun `dismissal after signing before queued send prevents handoff`() { check("transport") { vm.onDismissed() } }

    @Test fun `unchanged review signs and hands off once after held runtime`() { check("runtime", true) {} }
    @Test fun `unchanged review signs and hands off once after held key provider`() { check("key", true) {} }
    @Test fun `wallet change during runtime preparation prevents key read and handoff`() { check("runtime") { switchWallet() } }
    @Test fun `dismissal during runtime preparation prevents key read and handoff`() { check("runtime") { vm.onDismissed() } }
    @Test fun `wallet change during key provider wait prevents key read and handoff`() { check("key") { switchWallet() } }
    @Test fun `draft replacement during key provider wait prevents key read and handoff`() { check("key") { shared.joinFlowState.set(shared.requireJoinState.copy()) } }
    @Test fun `dismissal during key provider wait prevents key read and handoff`() { check("key") { vm.onDismissed() } }
    @Test fun `main context replacement after signing prevents queued handoff`() { check("transport") { shared.mainState.set(shared.requireMainState.copy()) } }
    @Test fun `draft replacement after signing prevents queued handoff`() { check("transport") { shared.joinFlowState.set(shared.requireJoinState.copy()) } }
    @Test fun `Back after signing prevents queued handoff`() { check("transport") { vm.onBackClick() } }
    @Test fun `scope cancellation during nonce preparation prevents handoff`() { check("nonce") { vm.viewModelScope.cancel() } }
    @Test fun `scope cancellation during key provider wait prevents handoff`() { check("key") { vm.viewModelScope.cancel() } }
    @Test fun `scope cancellation after signing prevents queued handoff`() { check("transport") { vm.viewModelScope.cancel() } }

    @Test fun `Back remains responsive while an admitted output write is held`() { checkHeldOutput { vm.onBackClick() } }
    @Test fun `wallet selection remains responsive while an admitted output write is held`() { checkHeldOutput { switchWallet() } }

    @Test fun `Back remains responsive while actual output write is held`() { checkHeldOutput("output-write") { vm.onBackClick() } }
    @Test fun `wallet selection remains responsive while actual output write is held`() { checkHeldOutput("output-write") { switchWallet() } }
    @Test fun `healthy admitted output is written once and acknowledged after flush`() { checkHeldOutput {} }

    @Test fun `ViewModelStore clearing during nonce preparation prevents handoff`() {
        check("nonce") { androidx.lifecycle.ViewModelStore().apply { put("review", vm); clear() } }
    }
    @Test fun `ViewModelStore clearing after signing prevents queued handoff`() {
        check("transport") { androidx.lifecycle.ViewModelStore().apply { put("review", vm); clear() } }
    }
    @Test fun `ViewModelStore clearing remains responsive during admitted output`() {
        checkHeldOutput { androidx.lifecycle.ViewModelStore().apply { put("review", vm); clear() } }
    }

    @Test fun `lost response after admitted output cannot authorize another payment`() {
        checkAmbiguousRetry(IllegalStateException("Local response lost after write"), false)
    }
    @Test fun `cancellation after admitted output cannot authorize another payment`() {
        checkAmbiguousRetry(kotlinx.coroutines.CancellationException("Local response cancelled after write"), false)
    }
    @Test fun `failure before output admission permits a healthy retry`() {
        checkAmbiguousRetry(IllegalStateException("Local queued request failed before admission"), true)
    }

    @Test fun `new API and review cannot recreate a payment after lost acknowledgement`() {
        checkRecreatedReview(false)
    }
    @Test fun `new API and review retain known acknowledgement without another payment`() {
        checkRecreatedReview(true)
    }
    @Test fun `wrong node acknowledgement retains payment without consuming review`() {
        val h = Harness().also { owned += it }
        await { h.estimated.get() == 1 }
        h.responseOverride = "0x" + "ff".repeat(32)
        h.vm.onConfirm()
        runBlocking { withTimeout(10_000) { h.shared.joinFlowState.submissionInProgress.first { !it } } }
        assertEquals(1, h.sent.get())
        assertTrue(h.shared.joinFlowState.get() != null)
        h.responseOverride = null
        h.vm.onConfirm()
        runBlocking { withTimeout(10_000) { h.shared.joinFlowState.submissionInProgress.first { !it } } }
        assertEquals(1, h.sent.get())
        assertEquals(1, h.keys.get())
    }
    @Test fun `Back remains responsive while signed bytes are durably retained`() {
        val h = Harness().also { owned += it }
        await { h.estimated.get() == 1 }
        val storing = java.util.concurrent.CountDownLatch(1)
        val stored = java.util.concurrent.CountDownLatch(1)
        h.storage.beforeCommit = {
            h.storage.beforeCommit = null
            storing.countDown()
            check(stored.await(5, java.util.concurrent.TimeUnit.SECONDS))
        }
        h.vm.onConfirm()
        assertTrue(storing.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val departed = java.util.concurrent.CountDownLatch(1)
        val worker = kotlin.concurrent.thread { h.vm.onBackClick(); departed.countDown() }
        val responsive: Boolean
        try { responsive = departed.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
        finally { stored.countDown(); worker.join(5000) }
        assertTrue("Navigation must not wait for disk persistence", responsive)
        runBlocking { withTimeout(10_000) { h.shared.joinFlowState.submissionInProgress.first { !it } } }
        assertEquals(0, h.sent.get())
        assertTrue(h.storage.values.isEmpty())
    }

    private fun checkRecreatedReview(knownAcknowledgement: Boolean) {
        val h = Harness().also { owned += it }
        await { h.estimated.get() == 1 }
        if (!knownAcknowledgement) h.transportFailure = IllegalStateException("local lost acknowledgement")
        val existing = h.vm.viewModelScope.coroutineContext.job.children.toSet()
        h.vm.onConfirm()
        val submitted = h.vm.viewModelScope.coroutineContext.job.children.toSet() - existing
        // Known-success consumption deliberately precedes the durable acknowledgement update.
        runBlocking { withTimeout(10_000) { submitted.forEach { it.join() } } }
        assertEquals(1, h.sent.get())
        assertEquals(1, h.keys.get())
        val retained = h.storage.values.toMap()
        val recreated = Harness(h.storage).also { owned += it }
        await { recreated.estimated.get() == 1 }
        recreated.vm.onConfirm()
        runBlocking { withTimeout(10_000) { recreated.shared.joinFlowState.submissionInProgress.first { !it } } }
        assertEquals(0, recreated.sent.get())
        assertEquals(0, recreated.keys.get())
        assertEquals(retained, h.storage.values.toMap())
    }

    private fun checkAmbiguousRetry(failure: Throwable, beforeAdmission: Boolean) {
        val h = Harness().also { owned += it }
        await { h.estimated.get() == 1 }
        h.boundary = "transport"
        h.transportFailure = failure
        h.failBeforeAdmission = beforeAdmission
        val before = h.vm.viewModelScope.coroutineContext.job.children.toSet()
        h.vm.onConfirm()
        await { h.entered.isCompleted }
        val submitting = (h.vm.viewModelScope.coroutineContext.job.children.toSet() - before).single()
        h.resume.complete(Unit)
        runBlocking { withTimeout(10_000) { submitting.join() } }
        assertEquals(if (beforeAdmission) 0 else 1, h.sent.get())
        h.transportFailure = null
        h.boundary = null
        val existing = h.vm.viewModelScope.coroutineContext.job.children.toSet()
        h.vm.onConfirm()
        val retryJobs = h.vm.viewModelScope.coroutineContext.job.children.toSet() - existing
        runBlocking { withTimeout(10_000) { retryJobs.forEach { it.join() } } }
        assertEquals("An uncertain admitted payment must not be recreated; proven unsent failure may retry", 1, h.sent.get())
    }

    private fun checkHeldOutput(stage: String = "output", invalidate: Harness.() -> Unit) {
        val h = Harness().also { owned += it }
        await { h.estimated.get() == 1 }
        h.boundary = stage
        h.vm.onConfirm()
        await { h.entered.isCompleted }
        assertFalse("Already admitted output cannot be cancelled as an unsent frame", requireNotNull(h.outputHandle).cancel())
        val invalidated = java.util.concurrent.CountDownLatch(1)
        val invalidator = kotlin.concurrent.thread { h.invalidate(); invalidated.countDown() }
        val responsive: Boolean
        try { responsive = invalidated.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
        finally { h.outputResume.countDown(); invalidator.join(5_000) }
        assertTrue("Invalidation must not wait for a stalled network output", responsive)
        await { h.sent.get() == 1 && h.shared.joinFlowState.get() == null }
        assertEquals(1, h.sent.get())
    }

    private fun check(boundary: String, succeeds: Boolean = false, revoke: Harness.() -> Unit) {
        val h = Harness().also { owned += it }
        await { h.estimated.get() == 1 }
        h.boundary = boundary
        val before = h.vm.viewModelScope.coroutineContext.job.children.toSet()
        h.vm.onConfirm()
        await { h.entered.isCompleted }
        val submitting = (h.vm.viewModelScope.coroutineContext.job.children.toSet() - before).single()
        h.revoke()
        h.resume.complete(Unit)
        runBlocking { withTimeout(10_000) { submitting.join() } }
        assertEquals("Actual local transport handoff count", if (succeeds) 1 else 0, h.sent.get())
        if (!succeeds && boundary in listOf("anchor", "nonce", "runtime", "key")) assertEquals("No key needed until current context authorizes final execution", 0, h.keys.get())
        if (succeeds) {
            assertEquals(1, h.keys.get())
            assertEquals(null, h.shared.joinFlowState.get())
            val scope = jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal.Scope(7, GENESIS, h.account.toHexString(withPrefix = false))
            assertEquals(BigInteger.ZERO, h.storage.journal().read(scope)?.anchor?.number)
        }
    }

    private class Harness(val storage: PoolJoinPreferencesFixture = PoolJoinPreferencesFixture(), initialBoundary: String? = null) {
        val chain = mock<Chain> {
            on { id } doReturn GENESIS
            on { name } doReturn "Local execution fixture"
            on { ecosystem } doReturn Ecosystem.Substrate
        }
        val account = ByteArray(32) { 5 }
        val chainAsset = mock<ChainAsset> {
            on { chainId } doReturn GENESIS
            on { id } doReturn "native"
            on { symbol } doReturn "LOCAL"
            on { precision } doReturn 0
            on { currencyId } doReturn "native"
            on { isUtility } doReturn true
        }
        val pool = mock<PoolInfo> { on { poolId } doReturn BigInteger.ONE; on { name } doReturn "Local pool" }
        val shared = StakingPoolSharedStateProvider().apply {
            mainState.set(StakingPoolState(chain=chain, asset=Asset.createEmpty(chainAsset, 7, account, minSupportedVersion = null), address=account.toAddress(42)))
            joinFlowState.set(StakingPoolJoinFlowState(BigDecimal.TEN,pool,mainState.capture()))
        }
        @Volatile var outputHandle: com.neovisionaries.ws.client.GuardedWriteHandle? = null
        val outputResume = java.util.concurrent.CountDownLatch(1)
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val keys = AtomicInteger(0)
        val sent = AtomicInteger(0)
        val estimated = AtomicInteger(0)
        @Volatile var boundary: String? = initialBoundary
        @Volatile var transportFailure: Throwable? = null
        @Volatile var failBeforeAdmission = false
        @Volatile var responseOverride: String? = null
        @Volatile var found = false
        @Volatile var nextBefore: BigInteger? = null
        @Volatile var searchFailure: Throwable? = null
        val checkedCursors = CopyOnWriteArrayList<BigInteger?>()
        val completedPendingReads = AtomicInteger(0)
        val router = mock<jp.co.soramitsu.staking.impl.presentation.StakingRouter>()
        val rpc = mock<RpcCalls>()
        val provider = mock<GuardedKeypairProvider>()
        val wallets = mock<WalletBoundKeypairProvider>()
        val runtime = runtime(POLKADOT_EXTENSIONS, listOf(POOL))
        val registry = mock<IChainRegistry>()
        private val mortality = mock<MortalityConstructor>()
        private val service = ExtrinsicService(rpc, wallets, ExtrinsicBuilderFactory(rpc, registry, mortality))
        private val finality = mock<jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader> {
            onBlocking { anchor(any()) } doSuspendableAnswer {
                hold("anchor")
                jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader.Anchor("0x" + GENESIS, BigInteger.ZERO, "0x" + "20".repeat(32))
            }
        }
        val api = StakingPoolApi(service, mock(), mock(), storage.journal(), finality)
        val interactor = spy(StakingPoolInteractor(api,mock(),mock(),mock(),mock(),mock(),mock(),mock()))
        private val resources = mock<ResourceManager> { on { getString(any()) } doAnswer { "fixture-text" } }
        val vm: ConfirmJoinPoolViewModel
        init {
            runBlocking {
                whenever(finality.search(any(), any(), anyOrNull(), any(), anyOrNull())).doSuspendableAnswer { call ->
                    checkedCursors += call.getArgument<BigInteger?>(2)
                    hold("reconciliation")
                    searchFailure?.let { throw it }
                    val inclusion = if (found) jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader.Inclusion("0x" + GENESIS,
                        call.getArgument<String>(1).fromHex().blake2b256().toHexString(withPrefix = true), "0x" + "31".repeat(32),
                        BigInteger.ONE, 0, "0x" + "32".repeat(32), BigInteger.TEN) else null
                    jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader.SearchResult(inclusion, if (found) null else nextBefore, 32)
                }
                whenever(registry.getRuntime(GENESIS)).doSuspendableAnswer { hold("runtime"); runtime }
                whenever(rpc.getRuntimeVersion(GENESIS)).thenReturn(RuntimeVersion(1_005_000,26))
                whenever(rpc.getAccountNonce(any(),any())).doSuspendableAnswer {
                    hold("nonce"); BigInteger.valueOf(3)
                }
                whenever(rpc.estimateExtrinsicFee(any(),any())).doSuspendableAnswer { estimated.incrementAndGet(); BigInteger.ONE }
                whenever(mortality.construct(chain)).thenReturn(Mortality(Era.Mortal(64,12),BLOCK_HASH))
                whenever(wallets.getCryptoTypeFor(any(),any())).thenReturn(CryptoType.ED25519) // Public fee lookup.
                whenever(wallets.forWallet(any())).thenAnswer { call ->
                    assertEquals(7L, call.getArgument<Long>(0))
                    provider
                }
                whenever(provider.getCryptoTypeFor(any(),any())).thenReturn(CryptoType.ED25519)
                whenever(provider.getKeypairFor(any(),any())).doSuspendableAnswer {
                    keys.incrementAndGet(); BaseKeypair(ByteArray(32) { 7 },account)
                }
                whenever(provider.getAuthorizedKeypairFor(any(),any(),any(),any())).doSuspendableAnswer { call ->
                    hold("key")
                    call.getArgument<MutationExecutionGuard>(2).runIfAuthorized(call.getArgument(3)) {
                        keys.incrementAndGet(); BaseKeypair(ByteArray(32) { 7 },account)
                    }
                }
                whenever(rpc.submitExtrinsic(any(),any())).doSuspendableAnswer {
                    hold("transport"); sent.incrementAndGet(); "0xlocal-recorded-handoff"
                }
                whenever(rpc.submitAuthorizedExtrinsic(any(),any(),any(),any())).doSuspendableAnswer { call ->
                    hold("transport")
                    if (failBeforeAdmission) transportFailure?.let { throw it }
                    val guard = call.getArgument<MutationExecutionGuard>(2)
                    val intent = call.getArgument<String>(3)
                    if (boundary in listOf("output", "output-write")) {
                        writeThroughActualHandle(guard, intent, call.getArgument<String>(1).toByteArray())
                    } else guard.runSubmissionIfAuthorized(intent) { sent.incrementAndGet() }
                    transportFailure?.let { throw it }
                    responseOverride ?: call.getArgument<String>(1).fromHex().blake2b256().toHexString(withPrefix = true)
                }
                doReturn(emptyList<ByteArray>()).whenever(interactor).getValidatorsIds(any(),any())
                whenever(interactor.getValidatorsIds(chain, BigInteger.ONE)).doSuspendableAnswer { hold("validators"); emptyList<ByteArray>() }
                doReturn(null).whenever(interactor).pendingJoin(any(), any(), any())
                whenever(interactor.pendingJoin(chain, account.toAddress(42), 7)).doSuspendableAnswer { call ->
                    hold("pending-lookup")
                    api.pendingJoin(call.getArgument(0), call.getArgument(1), call.getArgument(2)).also { completedPendingReads.incrementAndGet() }
                }
            }
            vm=ConfirmJoinPoolViewModel(resources,shared,interactor,router)
        }
        fun pending() = requireNotNull(storage.journal().read(
            jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal.Scope(7, GENESIS, account.toHexString(withPrefix = false))))
        /** Exercises the source-pinned writer handle against a local OutputStream, without a socket. */
        private fun writeThroughActualHandle(guard: MutationExecutionGuard, intent: String, frozenBytes: ByteArray) {
            val writerGuard = com.neovisionaries.ws.client.WebSocketWriteGuard { write ->
                guard.runSubmissionIfAuthorized(intent) { write.write() }
            }
            val callback = com.neovisionaries.ws.client.GuardedWriteCallback { throw AssertionError("Unexpected writer failure callback", it) }
            // The handle constructor/writeTo are transport-package internal; preserve production visibility.
            val constructor = com.neovisionaries.ws.client.GuardedWriteHandle::class.java.getDeclaredConstructor(
                com.neovisionaries.ws.client.WebSocketWriteGuard::class.java,
                com.neovisionaries.ws.client.GuardedWriteCallback::class.java
            ).apply { isAccessible = true }
            val handle = constructor.newInstance(writerGuard, callback)
            outputHandle = handle
            val method = handle.javaClass.getDeclaredMethod("writeTo", java.io.OutputStream::class.java, ByteArray::class.java)
                .apply { isAccessible = true }
            val output = object : java.io.OutputStream() {
                override fun write(value: Int) { error("Expected one frozen byte batch") }
                override fun write(bytes: ByteArray) {
                    assertArrayEquals(frozenBytes, bytes)
                    if (boundary == "output-write") holdOutput()
                    sent.incrementAndGet()
                }
                override fun flush() { if (boundary == "output") holdOutput() }
            }
            try { method.invoke(handle, output, frozenBytes) }
            catch (error: java.lang.reflect.InvocationTargetException) { throw error.targetException }
            val duplicate = org.junit.Assert.assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
                method.invoke(handle, output, frozenBytes)
            }
            assertTrue(duplicate.targetException is IllegalStateException)
            assertEquals("Duplicate final writer invocation cannot replay the primitive", 1, sent.get())
        }

        private fun holdOutput() {
            entered.complete(Unit)
            check(outputResume.await(10, java.util.concurrent.TimeUnit.SECONDS))
        }

        private suspend fun hold(stage: String) { if (boundary==stage) { entered.complete(Unit); resume.await() } }
        fun switchWallet() { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
    }

    private fun await(condition: () -> Boolean) {
        val deadline=System.currentTimeMillis()+10_000
        while (!condition() && System.currentTimeMillis()<deadline) Thread.sleep(10)
        assertTrue("Local held boundary was reached",condition())
    }
    private companion object {
        val GENESIS = "91".repeat(32)
        val BLOCK_HASH = ByteArray(32) { 0x11 }
        val FEE: BigInteger = BigInteger.valueOf(15_000_000)

        // Order and names as Polkadot 2005000 and Avail list them; unmapped ones encode nothing.
        val POLKADOT_EXTENSIONS = listOf(
            "CheckNonZeroSender", "CheckSpecVersion", "CheckTxVersion", "CheckGenesis", "CheckMortality",
            "CheckNonce", "CheckWeight", "ChargeTransactionPayment", "PrevalidateAttests", "CheckMetadataHash"
        )
        val POOL = Module(
            "NominationPools", null,
            mapOf("join" to MetadataFunction("join", listOf(FunctionArgument("amount", u8), FunctionArgument("pool_id", u8)), emptyList(), 39 to 0)),
            emptyMap(), emptyMap(), emptyMap(), BigInteger.valueOf(39)
        )

        fun runtime(signedExtensions: List<String>, modules: List<Module>): RuntimeSnapshot {
            val signature = DictEnum(
                "ExtrinsicSignature",
                listOf(
                    DictEnum.Entry("Ed25519", TypeReference(FixedByteArray("Ed25519", 64))),
                    DictEnum.Entry("Sr25519", TypeReference(FixedByteArray("Sr25519", 64))),
                    DictEnum.Entry("Ecdsa", TypeReference(FixedByteArray("Ecdsa", 65)))
                )
            )
            val registry = TypeRegistry(
                v13Preset() + mapOf(
                    "Address" to TypeReference(FixedByteArray("Address", 32)),
                    "ExtrinsicSignature" to TypeReference(signature)
                ),
                DynamicTypeResolver.defaultCompoundResolver()
            )
            return RuntimeSnapshot(
                registry,
                RuntimeMetadata(
                    BigInteger.valueOf(14),
                    modules.associateBy { it.name },
                    ExtrinsicMetadata(BigInteger.valueOf(4), signedExtensions)
                )
            )
        }
    }
}
