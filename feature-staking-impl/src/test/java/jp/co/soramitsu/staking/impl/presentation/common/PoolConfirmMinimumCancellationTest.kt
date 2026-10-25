package jp.co.soramitsu.staking.impl.presentation.common

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Before
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.claim.ConfirmPoolClaimViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.redeem.ConfirmPoolRedeemViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm.ConfirmPoolUnbondViewModel
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.presentation.BaseConfirmViewModel
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.math.BigDecimal
import java.math.BigInteger

/** Actual claim/redeem/unbond ViewModels and their production isValid overrides. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class PoolConfirmMinimumCancellationTest(private val screen: String) {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<BaseConfirmViewModel>()
    private val alerts = mutableListOf<AlertViewState>()
    private val pool = mock<StakingPoolInteractor> {
        onBlocking { estimateClaimFee(any()) } doReturn BigInteger.ONE
        onBlocking { estimateRedeemFee(any()) } doReturn BigInteger.ONE
        onBlocking { estimateUnstakeFee(any(), any()) } doReturn BigInteger.ONE
        onBlocking { getAccountName(any()) } doReturn null
    }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }

    @Test fun `cancelled lookup does not alert or execute`() {
        val vm = create(Result.failure(CancellationException("local lookup cancelled")))
        val state = vm.viewState
        vm.onConfirm()
        verifyOperations(0)
        assertFalse(state.value.isLoading)
        assertTrue(alerts.isEmpty())
    }

    @Test fun `unknown minimum still alerts and blocks`() {
        val vm = create(Result.failure(IllegalStateException("local minimum unavailable")))
        vm.onConfirm()
        verifyOperations(0)
        assertEquals(1, alerts.size)
    }

    @Test fun `known minimum permits one healthy operation`() {
        val vm = create(Result.success(BigInteger.ZERO))
        vm.onConfirm()
        verifyOperations(1)
        assertTrue(alerts.isEmpty())
    }

    @Test fun `unknown transferable blocks the actual operation even with known fee and minimum`() {
        val vm = create(Result.success(BigInteger.ZERO), available = null)
        vm.onConfirm()
        verifyOperations(0)
        assertFalse(vm.viewState.value.isLoading)
        assertEquals(1, alerts.size)
    }

    private fun verifyOperations(count: Int) = runBlocking {
        when (screen) {
            "claim" -> verify(pool, times(count)).claim(any())
            "redeem" -> verify(pool, times(count)).redeem(any())
            "unbond" -> verify(pool, times(count)).unstake(any(), any())
        }
    }

    private fun create(minimum: Result<BigInteger>, available: BigInteger? = BigInteger.TEN.pow(10)): BaseConfirmViewModel {
        val resources = mock<ResourceManager> {
            on { getString(any()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" }
            on { getString(any(), anyVararg()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" }
        }
        val asset = mock<Asset>(defaultAnswer = Answers.RETURNS_DEEP_STUBS).also { a ->
            whenever(a.transferable).thenReturn(BigDecimal.ONE)
            whenever(a.transferableInPlanks).thenReturn(available)
            whenever(a.token.configuration.id).thenReturn("native")
            whenever(a.token.configuration.chainId).thenReturn("public-fixture-chain")
            whenever(a.token.configuration.symbol).thenReturn("DOT")
            whenever(a.token.configuration.precision).thenReturn(10)
            whenever(a.token.configuration.iconUrl).thenReturn("https://fixture.invalid/dot.svg")
            whenever(a.token.fiatRate).thenReturn(null)
        }
        val shared = StakingPoolSharedStateProvider().also {
            it.mainState.set(StakingPoolState(chain = mock { on { id } doReturn "public-fixture-chain" }, asset = asset, address = "public-fixture-address"))
            it.manageState.set(StakingPoolManageFlowState(BigInteger.ONE, BigInteger.ONE, BigInteger.TEN, BigInteger.ONE, context = it.mainState.capture()))
        }
        val wallet = mock<WalletInteractor> { on { assetFlow(any(), any()) } doReturn emptyFlow() }
        val minimumUseCase = object : ExistentialDepositUseCase {
            override suspend fun invoke(chainAsset: jp.co.soramitsu.core.models.Asset) = minimum
        }
        val router = mock<StakingRouter> {
            on { openAlert(any()) } doAnswer { alerts += it.getArgument<AlertViewState>(0); Unit }
        }
        // Suspend Result methods return the unboxed success payload to Mockito's JVM interceptor.
        runBlocking {
            org.mockito.Mockito.doAnswer { "0xfixture" }.`when`(pool).claim(any())
            org.mockito.Mockito.doAnswer { "0xfixture" }.`when`(pool).redeem(any())
            org.mockito.Mockito.doAnswer { "0xfixture" }.`when`(pool).unstake(any(), any())
        }
        return when (screen) {
            "claim" -> ConfirmPoolClaimViewModel(wallet, minimumUseCase, shared, pool, resources, router)
            "redeem" -> ConfirmPoolRedeemViewModel(wallet, minimumUseCase, shared, pool, resources, router)
            "unbond" -> ConfirmPoolUnbondViewModel(wallet, minimumUseCase, shared, pool, resources, router)
            else -> error("Unknown fixture screen")
        }.also(owned::add)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun screens(): List<Array<String>> = listOf(arrayOf("claim"), arrayOf("redeem"), arrayOf("unbond"))
    }
}
