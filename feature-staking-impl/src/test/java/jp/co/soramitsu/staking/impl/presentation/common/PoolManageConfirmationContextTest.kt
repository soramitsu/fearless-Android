package jp.co.soramitsu.staking.impl.presentation.common

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.claim.ConfirmPoolClaimViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.redeem.ConfirmPoolRedeemViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm.ConfirmPoolUnbondViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm.ConfirmPoolBondMoreViewModel
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.presentation.BaseConfirmViewModel
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Answers
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.math.BigInteger

/** Actual member-confirmation controllers; local callbacks only, no key access or RPC. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class PoolManageConfirmationContextTest(private val screen: String) {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<Harness>()
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        // Baseline assertion failures must not strand deliberately non-cancellable fixture work.
        owned.forEach { it.minimumResume.complete(Unit); it.submission.complete(Result.success("0xfixture-cleanup")) }
        runBlocking { withTimeout(5_000) { owned.flatMap { it.vms }.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }
    @Test fun `unchanged member review executes original address once`() {
        val h = harness(); h.vm.onConfirm(); h.vm.onConfirm()
        assertEquals(listOf("public-original"), h.calls)
    }
    @Test fun `Back revokes retained member confirmation`() {
        val h = harness(); h.back(); h.vm.onConfirm(); assertTrue(h.calls.isEmpty())
    }
    @Test fun `replaced main context invalidates member review`() {
        val h = harness(); h.replaceMain(); h.vm.onConfirm(); assertTrue(h.calls.isEmpty())
    }
    @Test fun `replaced manage draft invalidates member review`() {
        val h = harness(); h.replaceManage(); h.vm.onConfirm(); assertTrue(h.calls.isEmpty())
    }
    @Test fun `wallet round trip cannot revive member review`() {
        val h = harness(); repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        h.vm.onConfirm(); assertTrue(h.calls.isEmpty())
    }
    @Test fun `Back during minimum lookup prevents member execution`() {
        val h = harness(holdMinimum = true); h.vm.onConfirm(); assertTrue(h.minimumEntered.isCompleted)
        h.back(); h.minimumResume.complete(Unit); assertTrue(h.calls.isEmpty())
    }
    @Test fun `main replacement during minimum lookup prevents member execution`() {
        val h = harness(holdMinimum = true); h.vm.onConfirm(); assertTrue(h.minimumEntered.isCompleted)
        h.replaceMain(); h.minimumResume.complete(Unit); assertTrue(h.calls.isEmpty())
    }
    @Test fun `cleared view model rejects minimum callback that ignores cancellation`() {
        val h = harness(holdMinimum = true); h.vm.onConfirm(); assertTrue(h.minimumEntered.isCompleted)
        ViewModelStore().also { it.put("member", h.vm); it.clear() }
        h.minimumResume.complete(Unit); assertTrue(h.calls.isEmpty())
    }
    @Test fun `two confirmation instances share one member attempt`() {
        val h = harness(holdOperation = true); val second = h.newVm(); awaitFee(second)
        h.vm.onConfirm(); assertTrue(h.operationEntered.isCompleted); second.onConfirm()
        assertEquals(1, h.calls.size); h.submission.complete(Result.success("0xfixture"))
    }
    @Test fun `known member output cannot be reused by a new confirmation`() {
        val h = harness(); h.vm.onConfirm(); val second = h.newVm(); awaitFee(second); second.onConfirm()
        assertEquals(1, h.calls.size)
    }
    @Test fun `late successful member output after Back does not navigate`() {
        val h = harness(holdOperation = true); h.vm.onConfirm(); h.back(); h.submission.complete(Result.success("0xfixture"))
        verify(h.router, never()).returnToManagePoolStake()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }
    @Test fun `late failed member output after context replacement does not alert`() {
        val h = harness(holdOperation = true); h.vm.onConfirm(); h.replaceMain()
        h.submission.complete(Result.failure(IllegalStateException("old local output")))
        verify(h.router, never()).openAlert(any())
    }
    @Test fun `late successful member output preserves replacement draft`() {
        val h = harness(holdOperation = true); h.vm.onConfirm(); h.replaceManage()
        val current = h.shared.requireManageState
        h.submission.complete(Result.success("0xfixture"))
        assertSame(current, h.shared.manageState.get())
        verify(h.router, never()).returnToManagePoolStake()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }
    @Test fun `Back navigates only once for the original review`() {
        val h = harness(); h.back(); h.back(); verify(h.router, times(1)).back()
    }
    @Test fun `dismissal revokes submit and repeated Back navigation`() {
        val h = harness(); h.dismiss(); h.vm.onConfirm(); h.back()
        assertTrue(h.calls.isEmpty()); verify(h.router, never()).back()
    }
    @Test fun `new confirmation cannot adopt stale management fields on another main context`() {
        val h = harness(); h.replaceMain(); val second = h.newVm(); second.onConfirm()
        assertTrue(h.calls.isEmpty())
    }
    @Test fun `claim observer replacement prevents member execution`() {
        val h = harness()
        var observerRan = false
        // Unconfined's event loop defers this observer until after an immediate mocked result.
        // An inline dispatcher explicitly exercises a reentrant publication observer.
        val inline = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) = block.run()
        }
        h.vm.viewModelScope.launch(inline, start = CoroutineStart.UNDISPATCHED) {
            h.shared.manageState.submissionInProgress.collect { if (it) { observerRan = true; h.replaceMain() } }
        }
        h.vm.onConfirm(); assertTrue(observerRan); assertTrue(h.calls.isEmpty())
    }
    @Test fun `matching result after departure still consumes original member draft`() {
        val h = harness(holdOperation = true); h.vm.onConfirm(); h.back()
        h.submission.complete(Result.success("0xfixture"))
        val second = h.newVm(); awaitFee(second); second.onConfirm()
        assertEquals(1, h.calls.size)
    }
    @Test fun `known local failure releases current member attempt for retry`() = runTest {
        val h = harness(holdOperation = true); h.vm.onConfirm()
        h.submission.complete(Result.failure(IllegalStateException("pre-output fixture")))
        verify(h.router, times(1)).openAlert(any())
        // A fresh suspended response avoids Mockito boxing an immediate Kotlin Result.
        h.submission = CompletableDeferred()
        h.vm.onConfirm(); assertEquals(2, h.calls.size)
        h.submission.complete(Result.success("0xfixture-retry"))
        verify(h.router, times(1)).returnToManagePoolStake()
    }
    @Test fun `owning success pop completes the one current member handoff`() {
        val h = harness()
        doAnswer { ViewModelStore().also { it.put("member", h.vm); it.clear() }; Unit }
            .whenever(h.router).returnToManagePoolStake()
        h.vm.onConfirm()
        verify(h.router, times(1)).returnToManagePoolStake()
        verify(h.router, times(1)).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }
    @Test fun `replacement during success pop suppresses subsequent old member success screen`() {
        val h = harness()
        doAnswer { h.replaceMain(); Unit }.whenever(h.router).returnToManagePoolStake()
        h.vm.onConfirm()
        verify(h.router, times(1)).returnToManagePoolStake()
        verify(h.router, never()).openOperationSuccess(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }
    private fun harness(holdMinimum: Boolean = false, holdOperation: Boolean = false) = Harness(holdMinimum, holdOperation).also { owned += it; awaitFee(it.vm) }
    private fun awaitFee(vm: BaseConfirmViewModel) = runBlocking { withTimeout(5_000) { vm.feeViewStateFlow.first { it.value != null } } }
    private inner class Harness(val holdMinimum: Boolean, holdOperation: Boolean) {
        val calls = mutableListOf<String>()
        val vms = mutableListOf<BaseConfirmViewModel>()
        val minimumEntered = CompletableDeferred<Unit>(); val minimumResume = CompletableDeferred<Unit>()
        val operationEntered = CompletableDeferred<Unit>(); var submission = CompletableDeferred<Result<String>>()
        val router = mock<StakingRouter>()
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
            it.manageState.set(StakingPoolManageFlowState(BigInteger.ONE, BigInteger.ONE, BigInteger.TEN, BigInteger.ONE, context = it.mainState.capture()))
        }
        val pool = mock<StakingPoolInteractor> {
            onBlocking { estimateClaimFee(any()) } doReturn BigInteger.ONE
            onBlocking { estimateRedeemFee(any()) } doReturn BigInteger.ONE
            onBlocking { estimateUnstakeFee(any(), any()) } doReturn BigInteger.ONE
            onBlocking { estimateBondMoreFee(any(), any()) } doReturn BigInteger.ONE
            onBlocking { getAccountName(any()) } doReturn null
        }
        val vm: BaseConfirmViewModel
        init {
            runBlocking {
                if (holdOperation) {
                    when (screen) {
                        "claim" -> whenever(pool.claim(any())).doSuspendableAnswer { calls += it.getArgument<String>(0); operationEntered.complete(Unit); withContext(NonCancellable) { submission.await() } }
                        "redeem" -> whenever(pool.redeem(any())).doSuspendableAnswer { calls += it.getArgument<String>(0); operationEntered.complete(Unit); withContext(NonCancellable) { submission.await() } }
                        "unbond" -> whenever(pool.unstake(any(), any())).doSuspendableAnswer { calls += it.getArgument<String>(0); operationEntered.complete(Unit); withContext(NonCancellable) { submission.await() } }
                        "bondmore" -> whenever(pool.bondMore(any(), any())).doSuspendableAnswer { calls += it.getArgument<String>(0); operationEntered.complete(Unit); withContext(NonCancellable) { submission.await() } }
                    }
                } else {
                    val answer = org.mockito.stubbing.Answer<Any> { calls += it.getArgument<String>(0); "0xfixture" }
                    org.mockito.Mockito.doAnswer(answer).`when`(pool).claim(any())
                    org.mockito.Mockito.doAnswer(answer).`when`(pool).redeem(any())
                    org.mockito.Mockito.doAnswer(answer).`when`(pool).unstake(any(), any())
                    org.mockito.Mockito.doAnswer(answer).`when`(pool).bondMore(any(), any())
                }
            }
            vm = newVm()
        }
        fun newVm(): BaseConfirmViewModel {
            val wallet = mock<WalletInteractor> { on { assetFlow(any(), any()) } doReturn emptyFlow() }
            val minimum = object : ExistentialDepositUseCase {
                override suspend fun invoke(chainAsset: jp.co.soramitsu.core.models.Asset): Result<BigInteger> {
                    minimumEntered.complete(Unit)
                    if (holdMinimum) withContext(NonCancellable) { minimumResume.await() }
                    return Result.success(BigInteger.ZERO)
                }
            }
            return when (screen) {
                "claim" -> ConfirmPoolClaimViewModel(wallet, minimum, shared, pool, resources, router)
                "redeem" -> ConfirmPoolRedeemViewModel(wallet, minimum, shared, pool, resources, router)
                "unbond" -> ConfirmPoolUnbondViewModel(wallet, minimum, shared, pool, resources, router)
                "bondmore" -> ConfirmPoolBondMoreViewModel(wallet, minimum, shared, pool, resources, router)
                else -> error("Unsupported local fixture")
            }.also(vms::add)
        }
        fun back() = when (val current = vm) {
            is ConfirmPoolClaimViewModel -> current.onBackClick()
            is ConfirmPoolRedeemViewModel -> current.onBackClick()
            is ConfirmPoolUnbondViewModel -> current.onBackClick()
            is ConfirmPoolBondMoreViewModel -> current.onBackClick()
            else -> error("Unsupported local fixture")
        }
        fun dismiss() = when (val current = vm) {
            is ConfirmPoolClaimViewModel -> current.onDismissed()
            is ConfirmPoolRedeemViewModel -> current.onDismissed()
            is ConfirmPoolUnbondViewModel -> current.onDismissed()
            is ConfirmPoolBondMoreViewModel -> current.onDismissed()
            else -> error("Unsupported local fixture")
        }
        fun replaceMain() = shared.mainState.set(shared.requireMainState.copy(address = "public-replacement"))
        fun replaceManage() = shared.manageState.set(shared.requireManageState.copy(amountInPlanks = BigInteger.valueOf(99)))
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun screens(): List<Array<String>> = listOf(arrayOf("claim"), arrayOf("redeem"), arrayOf("unbond"), arrayOf("bondmore"))
    }
}
