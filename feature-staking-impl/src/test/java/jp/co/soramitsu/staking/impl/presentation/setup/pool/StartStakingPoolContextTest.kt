package jp.co.soramitsu.staking.impl.presentation.setup.pool

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolJoinFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/** Actual welcome action; unrelated reward/limit jobs remain queued and are cancelled at teardown. */
@OptIn(ExperimentalCoroutinesApi::class)
class StartStakingPoolContextTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val router = mock<StakingRouter>()
    private val resource = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
    }
    private val shared = StakingPoolSharedStateProvider().apply {
        mainState.set(StakingPoolState(chain = mock<Chain> { on { id } doReturn "local" },
            asset = mock<Asset>(), address = "public-fixture-address", amount = BigDecimal.ONE))
    }
    private val owned = mutableListOf<StartStakingPoolViewModel>()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        owned.forEach { it.viewModelScope.cancel() }
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun `fresh welcome starts a join bound to its prepared context`() {
        val vm = viewModel()
        vm.onJoinPool()
        val draft = shared.requireJoinState
        assertTrue(shared.isJoinContextCurrent(draft.context))
        assertSame(shared.requireMainState, draft.context.value)
        verify(router).openSetupStakingPool()
    }

    @Test fun `returning to the current welcome preserves a legitimate edited draft`() {
        val draft = StakingPoolJoinFlowState(BigDecimal.TEN, context = shared.mainState.capture())
        shared.joinFlowState.set(draft)
        viewModel().onJoinPool()
        assertSame(draft, shared.requireJoinState)
        verify(router).openSetupStakingPool()
    }

    @Test fun `fresh welcome replaces a draft bound to an earlier main context`() {
        val previous = StakingPoolJoinFlowState(BigDecimal.TEN, context = shared.mainState.capture())
        shared.joinFlowState.set(previous)
        shared.mainState.set(shared.requireMainState.copy(address = "new-public-fixture-address"))
        viewModel().onJoinPool()
        val draft = shared.requireJoinState
        assertNotSame(previous, draft)
        assertNull(draft.amount)
        assertTrue(shared.isJoinContextCurrent(draft.context))
        assertEquals("new-public-fixture-address", draft.context.value?.address)
        verify(router).openSetupStakingPool()
    }

    @Test fun `retained welcome cannot start a join after main context replacement`() {
        val vm = viewModel()
        shared.mainState.set(shared.requireMainState.copy(address = "new-public-fixture-address"))
        vm.onJoinPool()
        assertNull(shared.joinFlowState.get())
        verify(router, never()).openSetupStakingPool()
    }

    @Test fun `wallet change and return cannot revive a retained welcome`() {
        val vm = viewModel()
        repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        vm.onJoinPool()
        assertNull(shared.joinFlowState.get())
        verify(router, never()).openSetupStakingPool()
    }

    @Test fun `fresh welcome cannot adopt retained main state after wallet selection`() {
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        viewModel().onJoinPool()
        assertNull(shared.joinFlowState.get())
        verify(router, never()).openSetupStakingPool()
    }

    private fun viewModel() = StartStakingPoolViewModel(mock(), mock(), mock(), resource, mock(), router, shared)
        .also { owned += it }
}
