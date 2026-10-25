package jp.co.soramitsu.staking.impl.presentation.setup.pool.join

import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicBoolean
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.CallMovedToAssetHubException
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolJoinFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever

/**
 * Joining a pool on Polkadot or Kusama estimates NominationPools.join, which fearless-utils refuses since pools moved
 * to Asset Hub. The estimate runs eagerly in viewModelScope; before this fix the refusal was uncaught there and
 * crashed the app as soon as the screen opened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupStakingPoolFeeFailureTest {

    private val resourceManager = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
        on { getString(any(), anyVararg()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
    }
    private val asset = mock<Asset>(defaultAnswer = Answers.RETURNS_DEEP_STUBS).also { asset ->
        whenever(asset.transferable).thenReturn(BigDecimal.TEN)
        whenever(asset.token.configuration.symbol).thenReturn("DOT")
        whenever(asset.token.configuration.precision).thenReturn(10)
        whenever(asset.token.fiatRate).thenReturn(null)
    }
    private val stakingInteractor = mock<StakingInteractor> {
        // The account header is not part of this test.
        onBlocking { getCurrentMetaAccount() } doSuspendableAnswer { awaitCancellation() }
    }
    private val stakingPoolInteractor = mock<StakingPoolInteractor> {
        onBlocking { estimateJoinFee(any(), any(), any(), any(), any()) } doAnswer { throw CallMovedToAssetHubException("NominationPools", "join") }
        onBlocking { getMinToJoinPool(POLKADOT_ID) } doReturn BigInteger.ONE
    }
    private val quickInputsUseCase = mock<QuickInputsUseCase> {
        onBlocking { calculateStakingQuickInputs(any(), any(), any(), any()) } doReturn emptyMap()
    }
    private val existentialDepositUseCase = mock<ExistentialDepositUseCase> {
        onBlocking { invoke(anyOrNull()) } doReturn Result.success(BigInteger.ZERO)
    }
    private val router = mock<StakingRouter>()
    private val sharedState = StakingPoolSharedStateProvider().apply {
        mainState.set(StakingPoolState(chain = mock<Chain> { on { id } doReturn POLKADOT_ID }, asset = asset, address = ADDRESS, amount = BigDecimal.ONE))
        joinFlowState.set(StakingPoolJoinFlowState(context = mainState.capture()))
    }

    private val uncaught = mutableListOf<Throwable>()
    private var ownedViewModel: SetupStakingPoolViewModel? = null
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // viewModelScope reports uncaught failures here; on a device the same failure kills the app.
        Thread.setDefaultUncaughtExceptionHandler { _, error -> synchronized(uncaught) { uncaught += error } }
    }

    @After
    fun tearDown() {
        try {
            settleOwnedViewModel()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
            Dispatchers.resetMain()
        }
        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
    }

    private fun settleOwnedViewModel() = runBlocking {
        // Fee estimation has a Default-thread producer whose completion resumes on Main.
        // Cancellation alone does not finish that work before the test dispatcher is removed.
        withTimeout(5_000) { ownedViewModel?.viewModelScope?.coroutineContext?.job?.cancelAndJoin() }
    }

    @Test
    fun `teardown waits for background fee producer cleanup before resetting Main`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cleaned = AtomicBoolean(false)
        whenever(stakingPoolInteractor.estimateJoinFee(any(), any(), any(), any(), any())).doSuspendableAnswer {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    delay(25)
                    withContext(Dispatchers.Main) { cleaned.set(true) }
                }
            }
        }
        val viewModel = SetupStakingPoolViewModel(
            resourceManager, stakingInteractor, mock(), stakingPoolInteractor, router,
            sharedState, existentialDepositUseCase, quickInputsUseCase
        ).also { ownedViewModel = it }
        try {
            withTimeout(5_000) { started.await() }
            settleOwnedViewModel()
            assertTrue("Default-thread fee cleanup must finish while Main is still installed", cleaned.get())
            assertTrue(viewModel.viewModelScope.coroutineContext.job.isCompleted)
        } finally {
            // Keep even the failing regression isolated from the next test.
            withTimeout(5_000) { viewModel.viewModelScope.coroutineContext.job.cancelAndJoin() }
        }
    }

    @Test
    fun `a pool join refused for Asset Hub is explained on next instead of crashing`() {
        val viewModel = SetupStakingPoolViewModel(
            resourceManager = resourceManager,
            stakingInteractor = stakingInteractor,
            iconGenerator = mock(),
            stakingPoolInteractor = stakingPoolInteractor,
            router = router,
            stakingPoolSharedStateProvider = sharedState,
            existentialDepositUseCase = existentialDepositUseCase,
            quickInputsUseCase = quickInputsUseCase
        ).also { ownedViewModel = it }
        // The estimate runs on Dispatchers.Default.
        awaitEstimates(1)

        viewModel.onAmountEntered(BigDecimal.ONE.movePointLeft(1))
        awaitEstimates(2)
        viewModel.onNextClick()

        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
        val alert = argumentCaptor<AlertViewState>().also { verify(router).openAlert(it.capture()) }.firstValue
        assertEquals("text-${R.string.relay_moved_to_asset_hub_title}", alert.title)
        assertEquals("text-${R.string.relay_moved_to_asset_hub_message}", alert.message)
        verify(router, never()).openSelectPool()
    }

    private fun awaitEstimates(count: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        fun estimates() = mockingDetails(stakingPoolInteractor).invocations.count { it.method.name == "estimateJoinFee" }
        while (estimates() < count && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("estimateJoinFee called ${estimates()} times", estimates() >= count)
        // Let the failed estimate settle before the screen reads it.
        Thread.sleep(100)
    }

    @Test fun `returned minimum cancellation does not alert or advance setup`() {
        checkMinimum(Result.failure(kotlinx.coroutines.CancellationException("local lookup cancelled")), 0, 0)
    }

    @Test fun `unknown minimum still explains and blocks setup`() {
        checkMinimum(Result.failure(IllegalStateException("local minimum unavailable")), 1, 0)
    }

    @Test fun `known minimum permits healthy setup to advance`() {
        checkMinimum(Result.success(BigInteger.ZERO), 0, 1)
    }

    @Test fun `unknown transferable rejects join with a known fee and minimum`() {
        whenever(asset.transferable).thenReturn(null)
        checkMinimum(Result.success(BigInteger.ZERO), 1, 0)
    }

    private fun checkMinimum(minimum: Result<BigInteger>, alertCount: Int, nextCount: Int) {
        runBlocking {
            whenever(stakingPoolInteractor.estimateJoinFee(any(), any(), any(), any(), any())).thenReturn(BigInteger.ONE)
        }
        val viewModel = SetupStakingPoolViewModel(
            resourceManager = resourceManager,
            stakingInteractor = stakingInteractor,
            iconGenerator = mock(),
            stakingPoolInteractor = stakingPoolInteractor,
            router = router,
            stakingPoolSharedStateProvider = sharedState,
            existentialDepositUseCase = object : ExistentialDepositUseCase {
                override suspend fun invoke(chainAsset: jp.co.soramitsu.core.models.Asset) = minimum
            },
            quickInputsUseCase = quickInputsUseCase
        ).also { ownedViewModel = it }
        runBlocking { withTimeout(10_000) { viewModel.viewState.first { it.feeInfoViewState.feeAmount != null } } }
        viewModel.onNextClick()
        verify(router, times(alertCount)).openAlert(any())
        verify(router, times(nextCount)).openSelectPool()
        assertTrue(uncaught.isEmpty())
    }

    private companion object {
        const val ADDRESS = "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"
        const val POLKADOT_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
    }
}
