package jp.co.soramitsu.staking.impl.presentation.setup.pool

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock

/**
 * viewModelScope starts on the immediate main dispatcher, so the page's loading coroutine runs inside the
 * constructor up to its first real suspension. Its asset and chain deferreds complete at once, which takes it
 * to `state.update` before the constructor has returned: the state must already exist by then. The unconfined
 * dispatcher stands in for the immediate main dispatcher; the slow lookups stay pending, as on a real network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartStakingPoolInitOrderTest {
    @get:Rule val instant = InstantTaskExecutorRule()

    private val uncaught = mutableListOf<Throwable>()
    private val thread = Thread.currentThread()
    private val previousHandler = thread.uncaughtExceptionHandler
    private var owned: StartStakingPoolViewModel? = null

    private val resource = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
    }
    private val relay = mock<StakingRelayChainScenarioInteractor> {
        onBlocking { stakePeriodInHours() } doSuspendableAnswer { awaitCancellation() }
        onBlocking { unstakingPeriod() } doSuspendableAnswer { awaitCancellation() }
    }
    private val pools = mock<StakingPoolInteractor> {
        onBlocking { getPossiblePools(any()) } doSuspendableAnswer { awaitCancellation() }
    }
    private val rewards = mock<RewardCalculatorFactory> {
        onBlocking { create(any()) } doSuspendableAnswer { awaitCancellation() }
    }
    private val shared = StakingPoolSharedStateProvider().apply {
        val token = mock<Token> { on { configuration } doReturn mock() }
        mainState.set(
            StakingPoolState(
                chain = mock<Chain> { on { id } doReturn "local" },
                asset = mock<Asset> { on { this.token } doReturn token },
                address = "public-fixture-address",
                amount = BigDecimal.ONE
            )
        )
    }

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error -> uncaught += error }
    }

    @After fun teardown() {
        owned?.viewModelScope?.cancel()
        thread.uncaughtExceptionHandler = previousHandler
        Dispatchers.resetMain()
    }

    @Test fun `opening the page shows its placeholders instead of crashing on a missing state`() {
        val viewModel = StartStakingPoolViewModel(relay, mock(), pools, resource, rewards, mock(), shared)
        owned = viewModel

        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals("...", viewModel.state.value.assetName)
    }
}
