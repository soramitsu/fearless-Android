package jp.co.soramitsu.staking.impl.presentation.common

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.claim.PoolClaimViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.redeem.PoolRedeemViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.PoolUnstakeViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.bond.select.PoolBondMoreViewModel
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseEnterAmountViewModel
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import jp.co.soramitsu.staking.api.domain.model.RoleInPool
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Answers
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.math.BigInteger

/** Actual amount/preparation controllers with local fee and navigation callbacks only. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class PoolManagePreparationContextTest(private val screen: String) {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<PoolManagePreparationHarness>()
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.flatMap { it.vms }.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }
    @Test fun `current member preparation opens its intended confirmation`() {
        val h = harness(); h.vm.onNextClick(); assertEquals(listOf(screen), h.navigations)
    }
    @Test fun `Back revokes retained member preparation`() {
        val h = harness(); h.back(); h.vm.onNextClick(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `main replacement invalidates member preparation`() {
        val h = harness(); h.replaceMain(); h.vm.onNextClick(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `draft replacement invalidates member preparation and preserves replacement fields`() {
        val h = harness(); h.replaceManage(); val newer = h.shared.requireManageState
        h.vm.onNextClick(); assertTrue(h.navigations.isEmpty()); assertSame(newer, h.shared.manageState.get())
    }
    @Test fun `wallet round trip cannot revive member preparation`() {
        val h = harness(); repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        h.vm.onNextClick(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `cleared member preparation cannot navigate`() {
        val h = harness(); ViewModelStore().also { it.put("preparation", h.vm); it.clear() }
        h.vm.onNextClick(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `Back navigates once for member preparation`() {
        val h = harness(); h.back(); h.back(); verify(h.router, times(1)).back()
    }
    @Test fun `claimed member draft cannot be edited or opened by retained preparation`() {
        val h = harness(); val current = h.shared.manageState.capture()
        val claim = requireNotNull(h.shared.manageState.tryClaim(current)); h.vm.onNextClick()
        assertTrue(h.navigations.isEmpty()); assertTrue(h.shared.manageState.owns(claim))
    }
    @Test fun `consumed member draft cannot be opened by retained preparation`() {
        val h = harness(); val claim = requireNotNull(h.shared.manageState.tryClaim(h.shared.manageState.capture()))
        requireNotNull(h.shared.manageState.consumeKeepingValue(claim)); h.vm.onNextClick()
        assertTrue(h.navigations.isEmpty())
    }
    @Test fun `new preparation cannot adopt old fields after main replacement`() {
        val h = harness(); h.replaceMain(); val second = h.newVm(); second.onNextClick()
        assertTrue(h.navigations.isEmpty())
    }
    @Test fun `amount callbacks cannot estimate fees for replacement address`() {
        val h = harness(); h.feeAddresses.clear(); h.replaceMain()
        h.vm.onAmountInput(BigDecimal("0.0000000002")); assertTrue(h.feeAddresses.isEmpty())
    }
    @Test fun `dismissed preparation cannot be revived by resume`() {
        val h = harness(); h.dismiss(); h.resume(); h.vm.onNextClick(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `repeated continue opens only one confirmation`() {
        val h = harness(); h.vm.onNextClick(); h.vm.onNextClick(); assertEquals(listOf(screen), h.navigations)
    }
    @Test fun `actual return allows editing and another confirmation`() {
        val h = harness(); h.vm.onNextClick(); h.resume(); h.vm.onAmountInput(BigDecimal("0.0000000002"))
        h.vm.onNextClick(); assertEquals(listOf(screen, screen), h.navigations)
        if (screen == "unbond" || screen == "bondmore") assertEquals(BigInteger.TWO, h.shared.requireManageState.amountInPlanks)
    }
    @Test fun `resume cannot adopt a replacement management draft`() {
        val h = harness(); h.replaceManage(); h.resume(); h.vm.onNextClick(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `released confirmation attempt needs actual screen resume before edit`() {
        val h = harness(); val claim = requireNotNull(h.shared.manageState.tryClaim(h.shared.manageState.capture()))
        h.shared.manageState.release(claim); h.vm.onNextClick(); assertTrue(h.navigations.isEmpty())
        h.resume(); h.vm.onNextClick(); assertEquals(listOf(screen), h.navigations)
    }
    @Test fun `new preparation under a claim cannot start another fee request`() {
        val h = harness(); val claim = requireNotNull(h.shared.manageState.tryClaim(h.shared.manageState.capture()))
        h.feeAddresses.clear(); h.newVm(); assertTrue(h.feeAddresses.isEmpty()); assertTrue(h.shared.manageState.owns(claim))
    }
    @Test fun `retained Back cannot pop a replacement context`() {
        val h = harness(); h.replaceMain(); h.back(); verify(h.router, never()).back()
    }
    @Test fun `return after interrupted fee input restarts fee calculation`() {
        val h = harness(); val claim = requireNotNull(h.shared.manageState.tryClaim(h.shared.manageState.capture()))
        h.vm.onAmountInput(BigDecimal("0.0000000002")); h.shared.manageState.release(claim); h.resume()
        h.feeAddresses.clear(); h.vm.onAmountInput(BigDecimal("0.0000000003"))
        assertEquals(listOf("public-original"), h.feeAddresses)
    }
    @Test fun `same context refreshed draft still permits leaving the amount screen`() {
        val h = harness(); h.replaceManage(); val replacement = h.shared.requireManageState
        h.back(); h.back(); verify(h.router, times(1)).back(); assertSame(replacement, h.shared.manageState.get())
        h.vm.onNextClick(); assertTrue(h.navigations.isEmpty())
    }
    private fun harness() = PoolManagePreparationHarness(screen).also(owned::add)
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun screens(): List<Array<String>> = listOf(arrayOf("claim"), arrayOf("redeem"), arrayOf("unbond"), arrayOf("bondmore"))
    }
}

internal class PoolManagePreparationHarness(val screen: String, holdMinimum: Boolean = false, role: RoleInPool? = null) {
        var minimumFailure: Throwable? = null
        var minimumValue = BigInteger.ZERO
        val alertResults = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)
        val minimumEntered = CompletableDeferred<Unit>()
        val minimumResume = CompletableDeferred<Unit>()
        val navigations = mutableListOf<String>()
        val feeAddresses = mutableListOf<String>()
        val vms = mutableListOf<BaseEnterAmountViewModel>()
        val router = mock<StakingRouter> {
            on { alertResultFlow(any()) } doReturn alertResults
            on { openPoolConfirmClaim() } doAnswer { navigations += "claim"; Unit }
            on { openPoolConfirmRedeem() } doAnswer { navigations += "redeem"; Unit }
            on { openPoolConfirmUnstake() } doAnswer { navigations += "unbond"; Unit }
            on { openPoolConfirmBondMore() } doAnswer { navigations += "bondmore"; Unit }
        }
        val resources = mock<ResourceManager> {
            on { getString(any()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" }
            on { getString(any(), anyVararg()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" }
        }
        val asset = mock<Asset>(defaultAnswer = Answers.RETURNS_DEEP_STUBS).also { a ->
            whenever(a.transferable).thenReturn(BigDecimal.ONE)
            whenever(a.transferableInPlanks).thenReturn(BigInteger.TEN.pow(10))
            whenever(a.metaId).thenReturn(7L)
            whenever(a.token.configuration.id).thenReturn("native")
            whenever(a.token.configuration.chainId).thenReturn("public-fixture-chain")
            whenever(a.token.configuration.symbol).thenReturn("DOT")
            whenever(a.token.configuration.precision).thenReturn(10)
            whenever(a.token.configuration.iconUrl).thenReturn("https://fixture.invalid/dot.svg")
            whenever(a.token.fiatRate).thenReturn(null)
        }
        val shared = StakingPoolSharedStateProvider().also {
            it.mainState.set(StakingPoolState(chain = mock { on { id } doReturn "public-fixture-chain" }, asset = asset, address = "public-original"))
            it.manageState.set(StakingPoolManageFlowState(BigInteger.ONE, BigInteger.ONE, BigInteger.TEN, BigInteger.ONE, userRole = role, context = it.mainState.capture()))
        }
        val pool = mock<StakingPoolInteractor> {
            onBlocking { estimateClaimFee(any()) } doAnswer { feeAddresses += it.getArgument<String>(0); BigInteger.ONE }
            onBlocking { estimateRedeemFee(any()) } doAnswer { feeAddresses += it.getArgument<String>(0); BigInteger.ONE }
            onBlocking { estimateUnstakeFee(any(), any()) } doAnswer { feeAddresses += it.getArgument<String>(0); BigInteger.ONE }
            onBlocking { estimateBondMoreFee(any(), any()) } doAnswer { feeAddresses += it.getArgument<String>(0); BigInteger.ONE }
            onBlocking { getMinToCreate(any()) } doReturn BigInteger.ZERO
        }
        val quick = mock<QuickInputsUseCase> {
            onBlocking { calculateStakingQuickInputs(any(), any(), any(), any()) } doReturn emptyMap()
        }
        init {
            if (holdMinimum) runBlocking {
                whenever(pool.getMinToCreate(any())).doSuspendableAnswer {
                    minimumEntered.complete(Unit)
                    withContext(NonCancellable) { minimumResume.await() }
                    minimumFailure?.let { throw it }
                    minimumValue
                }
            }
        }
        val vm = newVm()
        fun newVm(): BaseEnterAmountViewModel = when (screen) {
            "claim" -> PoolClaimViewModel(resources, shared, pool, router)
            "redeem" -> PoolRedeemViewModel(resources, shared, pool, router)
            "unbond" -> PoolUnstakeViewModel(resources, shared, pool, router, quick)
            "bondmore" -> PoolBondMoreViewModel(resources, shared, pool, router, quick)
            else -> error("Unsupported local fixture")
        }.also { vms += it; it.onAmountInput(BigDecimal("0.0000000001")) }
        fun back() = when (val current = vm) {
            is PoolClaimViewModel -> current.onBackClick()
            is PoolRedeemViewModel -> current.onBackClick()
            is PoolUnstakeViewModel -> current.onBackClick()
            is PoolBondMoreViewModel -> current.onBackClick()
            else -> error("Unsupported local fixture")
        }
        fun resume() = when (val current = vm) {
            is PoolClaimViewModel -> current.onResumed()
            is PoolRedeemViewModel -> current.onResumed()
            is PoolUnstakeViewModel -> current.onResumed()
            is PoolBondMoreViewModel -> current.onResumed()
            else -> error("Unsupported local fixture")
        }
        fun dismiss() = when (val current = vm) {
            is PoolClaimViewModel -> current.onDismissed()
            is PoolRedeemViewModel -> current.onDismissed()
            is PoolUnstakeViewModel -> current.onDismissed()
            is PoolBondMoreViewModel -> current.onDismissed()
            else -> error("Unsupported local fixture")
        }
        fun replaceMain() = shared.mainState.set(shared.requireMainState.copy(address = "public-replacement"))
        fun replaceManage() = shared.manageState.set(shared.requireManageState.copy(amountInPlanks = BigInteger.valueOf(99)))
    }
