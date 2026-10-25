package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

import jp.co.soramitsu.staking.impl.data.repository.PoolJoinPreferencesFixture
import jp.co.soramitsu.core.extrinsic.ExtrinsicSubmissionTracking
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Real confirmation, interactor and API; the final extrinsic service is a local recorder, never a signer. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmJoinPoolReviewedChainTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val account = ByteArray(32) { (it + 1).toByte() }
    private val address = account.toAddress(42)
    private val chainAsset = mock<ChainAsset> {
        on { chainId } doReturn REVIEWED_CHAIN
        on { id } doReturn "native"
        on { symbol } doReturn "LOCAL"
        on { precision } doReturn 10
        on { currencyId } doReturn "local-native"
        on { isUtility } doReturn true
    }
    private fun chain(identifier: String) = mock<Chain> {
        on { id } doReturn identifier
        on { name } doReturn identifier
        on { ecosystem } doReturn Ecosystem.Substrate
        on { assets } doReturn listOf(chainAsset)
    }
    private val reviewed = chain(REVIEWED_CHAIN)
    private val other = chain("local-other-pool-chain")
    private val selected = AtomicReference(reviewed)
    private val stakingSelection = mock<StakingSharedState> {
        onBlocking { chain() } doAnswer { selected.get() }
    }
    private val estimated = CopyOnWriteArrayList<String>()
    private val submitted = CopyOnWriteArrayList<Pair<String, ByteArray>>()
    private val cancelAtKnownResult = AtomicBoolean(false)
    private val extrinsics = mock<ExtrinsicService> {
        onBlocking { estimateFeeForWallet(any(), any<ByteArray>(), any(), any(), anyOrNull(), anyOrNull(), any()) } doAnswer {
            assertEquals(7L, it.getArgument<Long>(2))
            estimated += it.getArgument<IChain>(0).id
            BigInteger.ONE
        }
        onBlocking { submitTrackedExtrinsicForWallet(any(), any(), any(), any(), any(), any(), any()) } doSuspendableAnswer {
            assertEquals(7L, it.getArgument<Long>(2))
            submitted += it.getArgument<IChain>(0).id to it.getArgument<ByteArray>(1).copyOf()
            val tracking = it.getArgument<ExtrinsicSubmissionTracking>(4)
            val intent = "12".repeat(32)
            val signed = "0x0102"
            val hash = signed.fromHex().blake2b256().toHexString(withPrefix = true)
            val authority = it.getArgument<(String) -> MutationExecutionGuard>(3)(intent)
            tracking.guard(signed, intent, authority).runSubmissionIfAuthorized(intent) { Unit }
            yield()
            // The service returned a known success; cancelling here tests the API's IO dispatch-back.
            if (cancelAtKnownResult.get()) owned?.viewModelScope?.cancel()
            tracking.acknowledged(hash)
            tracking.finished()
            Result.success(hash)
        }
    }
    private val finality = mock<jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader> {
        onBlocking { anchor(any()) } doAnswer {
            jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader.Anchor("0x" + it.getArgument<String>(0).removePrefix("0x"),
                BigInteger.ZERO, "0x" + "20".repeat(32))
        }
    }
    private val api = StakingPoolApi(extrinsics, stakingSelection, mock(), PoolJoinPreferencesFixture().journal(), finality)
    private val interactor = spy(StakingPoolInteractor(api, mock(), mock(), mock(), mock(), mock(), mock(), mock())).also {
        runBlocking { doReturn(emptyList<ByteArray>()).whenever(it).getValidatorsIds(any(), any()) }
    }
    private val pool = mock<PoolInfo> {
        on { poolId } doReturn BigInteger.ONE
        on { name } doReturn "Local reviewed pool"
    }
    private val shared = StakingPoolSharedStateProvider().apply {
        mainState.set(StakingPoolState(chain = reviewed,
            asset = Asset.createEmpty(chainAsset, 7, account, minSupportedVersion = null), address = address))
        joinFlowState.set(StakingPoolJoinFlowState(BigDecimal.ONE, pool, mainState.capture()))
    }
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
    }
    private var owned: ConfirmJoinPoolViewModel? = null
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }
    }

    @After fun teardown() {
        owned?.let {
            val job = it.viewModelScope.coroutineContext.job
            it.viewModelScope.cancel()
            runBlocking { withTimeout(10_000) { job.join() } }
        }
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        Dispatchers.resetMain()
        assertEquals(emptyList<Throwable>(), uncaught.toList())
    }

    @Test fun `unchanged review estimates and submits its exact chain and public account`() {
        val vm = viewModel()
        await { estimated.isNotEmpty() }
        vm.onConfirm()
        await { submitted.isNotEmpty() }
        await { shared.joinFlowState.get() == null }
        assertEquals(listOf(REVIEWED_CHAIN), estimated.toList())
        assertEquals(REVIEWED_CHAIN, submitted.single().first)
        assertArrayEquals(account, submitted.single().second)
    }

    @Test fun `selection changed after fee cannot redirect a reviewed pool submission`() {
        val vm = viewModel()
        await { estimated.isNotEmpty() }
        selected.set(other)
        vm.onConfirm()
        await { submitted.isNotEmpty() }
        assertEquals(REVIEWED_CHAIN, submitted.single().first)
        assertArrayEquals(account, submitted.single().second)
    }

    @Test fun `fee estimate uses displayed review even when global selection has changed`() {
        selected.set(other)
        viewModel()
        await { estimated.isNotEmpty() }
        assertEquals(listOf(REVIEWED_CHAIN), estimated.toList())
    }


    @Test fun `known service success stays spent when caller cancellation interrupts API dispatch back`() {
        val vm = viewModel()
        await { estimated.isNotEmpty() }
        cancelAtKnownResult.set(true)
        vm.onConfirm()
        val job = vm.viewModelScope.coroutineContext.job
        runBlocking { withTimeout(10_000) { job.join() } }
        assertEquals(1, submitted.size)
        assertNull(shared.joinFlowState.get())
        assertNull(vm.errorLiveData.value)
    }

    private fun viewModel() = ConfirmJoinPoolViewModel(resources, shared, interactor, mock())
        .also { owned = it }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("Expected local boundary before timeout", condition())
    }

    private companion object { val REVIEWED_CHAIN = "91".repeat(32) }
}
