package jp.co.soramitsu.staking.impl.presentation.common

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.OwnPool
import jp.co.soramitsu.staking.api.domain.model.NominationPoolState
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.balance.ManagePoolStakeViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.balance.compose.PoolStakeManagementOptions
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Answers
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class PoolManageActionContextTest(private val action: String) {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<PoolManageActionHarness>()
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }
    private fun harness() = PoolManageActionHarness(action).also(owned::add)
    @Test fun `current management action opens original destination`() = runTest {
        val h = harness(); h.act(); assertEquals(listOf(action), h.navigations)
    }
    @Test fun `Back revokes retained management action`() = runTest {
        val h = harness(); h.vm.onBackClick(); h.act(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `main replacement prevents retained management navigation`() = runTest {
        val h = harness(); h.replaceMain(); h.act(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `wallet round trip cannot revive management action`() = runTest {
        val h = harness(); repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        h.act(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `cleared management controller cannot navigate`() = runTest {
        val h = harness(); ViewModelStore().also { it.put("manage", h.vm); it.clear() }
        h.act(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `duplicate management action opens one destination`() = runTest {
        val h = harness(); h.act(); h.act(); assertEquals(listOf(action), h.navigations)
    }
    @Test fun `Back after forward action cannot pop its destination`() = runTest {
        val h = harness(); h.act(); h.vm.onBackClick(); assertEquals(0, h.backCalls)
    }
    @Test fun `Back is consumed once`() = runTest {
        val h = harness(); h.vm.onBackClick(); h.vm.onBackClick(); assertEquals(1, h.backCalls)
    }
    @Test fun `stale Back cannot pop replacement main context`() = runTest {
        val h = harness(); h.replaceMain(); h.vm.onBackClick(); assertEquals(0, h.backCalls)
    }
    @Test fun `late pool update after Back cannot replace management fields`() = runTest {
        val h = harness(); h.vm.onBackClick(); val original = h.shared.manageState.capture()
        h.deliverUpdatedPool(); assertTrue(h.shared.manageState.isCurrent(original))
    }
    @Test fun `late pool update after forward action preserves child fields`() = runTest {
        val h = harness(); h.act(); val original = h.shared.manageState.capture()
        h.deliverUpdatedPool(); assertTrue(h.shared.manageState.isCurrent(original))
    }
    @Test fun `current visible pool update still refreshes management fields`() = runTest {
        val h = harness(); h.deliverUpdatedPool()
        assertEquals(BigInteger.TWO, h.shared.requireManageState.claimableInPlanks)
        h.act(); assertEquals(listOf(action), h.navigations)
    }
    @Test fun `paused actions are suppressed and actual resume permits new action`() = runTest {
        val h = harness(); h.vm.onPaused(); h.act(); assertTrue(h.navigations.isEmpty())
        h.vm.onResumed(); h.act(); assertEquals(listOf(action), h.navigations)
    }
    @Test fun `return from child permits one new explicit management action`() = runTest {
        val h = harness(); h.act(); h.vm.onPaused(); h.vm.onResumed(); h.act(); h.act()
        assertEquals(listOf(action, action), h.navigations)
    }
    @Test fun `dismissal cannot be revived by resume`() = runTest {
        val h = harness(); h.vm.onDismissed(); h.vm.onResumed(); h.act(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `cleared controller cannot be revived by resume`() = runTest {
        val h = harness(); ViewModelStore().also { it.put("manage", h.vm); it.clear() }
        h.vm.onResumed(); h.act(); assertTrue(h.navigations.isEmpty())
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun actions() = listOf("claim", "redeem", "bondmore", "unbond", "select", "nominations", "info").map { arrayOf(it) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class PoolManageHeldActionContextTest(private val action: String) {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<PoolManageActionHarness>()
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }
    private fun harness() = PoolManageActionHarness(action, held = true).also(owned::add)
    @Test fun `current held pool action opens only after original pool arrives`() = runTest {
        val h = harness(); h.act(); assertTrue(h.navigations.isEmpty()); h.deliverPool()
        assertEquals(listOf(action), h.navigations)
    }
    @Test fun `Back before held pool arrival suppresses management action`() = runTest {
        val h = harness(); h.act(); h.vm.onBackClick(); h.deliverPool(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `main replacement before held pool arrival suppresses management action`() = runTest {
        val h = harness(); h.act(); h.replaceMain(); h.deliverPool(); assertTrue(h.navigations.isEmpty())
        assertNull(h.shared.selectValidatorsState.get()); assertNull(h.shared.selectedValidatorsState.get()); assertTrue(h.shared.poolsCache.value.isEmpty())
    }
    @Test fun `clearing before held pool arrival suppresses management action`() = runTest {
        val h = harness(); h.act(); ViewModelStore().also { it.put("manage", h.vm); it.clear() }
        h.deliverPool(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `pause and resume cannot revive older held action`() = runTest {
        val h = harness(); h.act(); h.vm.onPaused(); h.vm.onResumed(); h.deliverPool()
        assertTrue(h.navigations.isEmpty()); verify(h.pool, never()).getValidatorsIds(any(), any())
        h.act(); assertEquals(listOf(action), h.navigations)
    }
    @Test fun `wallet round trip before pool arrives suppresses work and publication`() = runTest {
        val h = harness(); h.act(); repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        h.deliverPool(); assertTrue(h.navigations.isEmpty()); verify(h.pool, never()).getValidatorsIds(any(), any())
        assertNull(h.shared.selectValidatorsState.get()); assertNull(h.shared.selectedValidatorsState.get()); assertTrue(h.shared.poolsCache.value.isEmpty())
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun actions() = listOf("select", "nominations", "info").map { arrayOf(it) }
    }
}

internal class PoolManageActionHarness(private val action: String, held: Boolean = false) {
    val navigations = mutableListOf<String>()
    var backCalls = 0
    private val publicAccount = ByteArray(32) { 7 }
    val chain = mock<Chain> {
        on { id } doReturn "public-fixture-chain"
        on { ecosystem } doReturn Ecosystem.Substrate
    }
    val ownPool = OwnPool(
        poolId = BigInteger.valueOf(7), name = "Public fixture pool", myStakeInPlanks = BigInteger.TEN,
        totalStakedInPlanks = BigInteger.valueOf(100), lastRecordedRewardCounter = BigInteger.ZERO,
        state = NominationPoolState.HasNoValidators, redeemable = BigInteger.ONE, unbonding = BigInteger.ZERO,
        pendingRewards = BigInteger.ONE, members = BigInteger.ONE,
        depositor = publicAccount, root = publicAccount, nominator = publicAccount, stateToggler = publicAccount
    )
    private val poolFlow = MutableStateFlow<OwnPool?>(if (held) null else ownPool)
    val router = mock<StakingRouter> {
        on { back() } doAnswer { backCalls++; Unit }
        on { openPoolClaim() } doAnswer { navigations += "claim"; Unit }
        on { openPoolRedeem() } doAnswer { navigations += "redeem"; Unit }
        on { openPoolBondMore() } doAnswer { navigations += "bondmore"; Unit }
        on { openPoolUnstake() } doAnswer { navigations += "unbond"; Unit }
        on { openStartSelectValidators() } doAnswer { navigations += "select"; Unit }
        on { openSelectedValidators() } doAnswer { navigations += "nominations"; Unit }
        on { openPoolInfo(any()) } doAnswer { navigations += "info"; Unit }
    }
    val resources = mock<ResourceManager> {
        on { getString(any()) } doReturn "fixture"
        on { getString(any(), anyVararg()) } doReturn "fixture"
        on { getQuantityString(any(), any(), anyVararg()) } doReturn "fixture duration"
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
        it.mainState.set(StakingPoolState(chain = chain, asset = asset, address = publicAccount.toAddress(42)))
    }
    val pool = mock<StakingPoolInteractor> {
        on { observeCurrentPool(any(), any()) } doReturn poolFlow.map { Result.success(it) }
        onBlocking { getValidatorsIds(any(), any()) } doReturn emptyList()
    }
    val relay = mock<StakingRelayChainScenarioInteractor> {
        onBlocking { unstakingPeriod() } doReturn 24
    }
    val vm = ManagePoolStakeViewModel(pool, shared, resources, relay, router)
    fun act() = when (action) {
        "claim" -> vm.onClaimClick()
        "redeem" -> vm.onRedeemClick()
        "bondmore" -> vm.onStakeMoreClick()
        "unbond" -> vm.onUnstakeClick()
        "select" -> vm.onSelectValidatorsClick()
        "nominations" -> vm.onBottomSheetOptionSelected(PoolStakeManagementOptions.Nominations)
        "info" -> vm.onBottomSheetOptionSelected(PoolStakeManagementOptions.PoolInfo)
        else -> error("Unsupported fixture action")
    }
    fun replaceMain() = shared.mainState.set(shared.requireMainState.copy(address = ByteArray(32) { 9 }.toAddress(42)))
    fun deliverPool() { poolFlow.value = ownPool }
    fun withdrawPool() { poolFlow.value = null }
    fun deliverSamePoolUpdate(pool: OwnPool) { poolFlow.value = pool }
    fun deliverUpdatedPool() { poolFlow.value = ownPool.copy(poolId = BigInteger.valueOf(8), pendingRewards = BigInteger.TWO) }
}
