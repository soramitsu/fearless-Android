package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.io.IOException
import java.math.BigDecimal
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.model.NominatorStatus
import jp.co.soramitsu.staking.impl.domain.model.ValidatorStatus
import jp.co.soramitsu.staking.impl.domain.rewards.SoraStakingRewardsScenario
import jp.co.soramitsu.staking.impl.domain.model.StakeSummary
import jp.co.soramitsu.staking.impl.domain.model.StakeSummarySource
import jp.co.soramitsu.staking.impl.domain.model.StashNoneStatus
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.doSuspendableAnswer
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual relay scenario body with independently controlled public state/asset/summary.
 * No claim about eligibility, selected-wallet epoch, exact rendered callbacks or transaction output.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RelayStakingBodyContextTest {
    @Test fun `healthy matching relay stash renders its own denomination`() = runTest {
        val h = Harness(this); h.start()
        assertTrue(h.stake().stakeInfoViewState.staked.value!!.contains("AAA"))
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `different chain asset withdraws old relay stash body`() = runTest {
        val h = Harness(this); h.start(); h.assets.emit(h.asset(h.b)); runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `different account asset withdraws old relay stash body`() = runTest {
        val h = Harness(this); h.start(); h.assets.emit(h.asset(h.a, ByteArray(32) { 2 })); runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `non relay asset cannot retain relay management body`() = runTest {
        val h = Harness(this); h.start(); h.assets.emit(h.asset(h.a, type = ChainAsset.StakingType.PARACHAIN)); runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `new relay state cannot use lagging previous chain asset`() = runTest {
        val h = Harness(this); h.start(); h.summariesB.emit(h.summary); h.state.value = h.stash(h.b); runCurrent()
        assertNull(h.values.last())
        h.assets.emit(h.asset(h.b)); runCurrent()
        assertTrue(h.stake().stakeInfoViewState.staked.value!!.contains("BBB"))
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `new relay summary wait removes previous body before fresh summary arrives`() = runTest {
        val h = Harness(this); h.start(); h.assets.emit(h.asset(h.b)); h.state.value = h.stash(h.b); runCurrent()
        assertNull(h.values.last())
        h.summariesB.emit(h.summary); runCurrent()
        assertTrue(h.stake().stakeInfoViewState.staked.value!!.contains("BBB"))
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `matching relay asset remains usable after a mismatched observation`() = runTest {
        val h = Harness(this); h.start(); h.assets.emit(h.asset(h.b)); runCurrent()
        h.assets.emit(h.asset(h.a)); runCurrent()
        assertTrue(h.stake().stakeInfoViewState.staked.value!!.contains("AAA"))
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `matching nominator body remains healthy`() = runTest {
        val h = Harness(this); h.start()
        h.state.value = StakingState.Stash.Nominator(h.a, h.account, h.account, h.account, mock()); runCurrent()
        assertTrue(h.stake().stakeInfoViewState.staked.value!!.contains("AAA"))
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `matching validator body remains healthy`() = runTest {
        val h = Harness(this); h.start()
        h.state.value = StakingState.Stash.Validator(h.a, h.account, h.account, h.account, mock()); runCurrent()
        assertTrue(h.stake().stakeInfoViewState.staked.value!!.contains("AAA"))
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `nominator body is withdrawn when observed account changes`() = runTest {
        val h = Harness(this); h.start()
        h.state.value = StakingState.Stash.Nominator(h.a, h.account, h.account, h.account, mock()); runCurrent()
        assertTrue(h.stake().stakeInfoViewState.staked.value!!.contains("AAA"))
        h.assets.emit(h.asset(h.a, ByteArray(32) { 2 })); runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `validator body is withdrawn when observed chain changes`() = runTest {
        val h = Harness(this); h.start()
        h.state.value = StakingState.Stash.Validator(h.a, h.account, h.account, h.account, mock()); runCurrent()
        assertTrue(h.stake().stakeInfoViewState.staked.value!!.contains("AAA"))
        h.assets.emit(h.asset(h.b)); runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `asset mismatch withdraws body while reward token lookup is held`() = runTest {
        val h = Harness(this, soraMainChainId); h.start()
        val entered = CompletableDeferred<Unit>(); val held = CompletableDeferred<Token>()
        whenever(h.rewards.getRewardAsset()).doSuspendableAnswer { entered.complete(Unit); held.await() }
        h.assets.emit(h.asset(h.a)); runCurrent(); assertTrue(entered.isCompleted)
        h.assets.emit(h.asset(h.b)); runCurrent()
        assertNull(h.values.last())
        held.complete(h.asset(h.a).token); runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `late reward token completion cannot publish an obsolete body`() = runTest {
        val h = Harness(this, soraMainChainId); h.start()
        val entered = CompletableDeferred<Unit>(); val held = CompletableDeferred<Token>()
        whenever(h.rewards.getRewardAsset()).doSuspendableAnswer { entered.complete(Unit); held.await() }
        h.assets.emit(h.asset(h.a)); runCurrent(); assertTrue(entered.isCompleted)
        val beforeMismatch = h.values.size
        h.assets.emit(h.asset(h.b)); runCurrent()
        held.complete(h.asset(h.a).token); runCurrent()
        assertTrue("No obsolete body after the mismatch", h.values.drop(beforeMismatch).all { it == null })
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `summary failure withdraws body and later coherent state recovers`() = runTest {
        val h = Harness(this); val fail = CompletableDeferred<Unit>()
        whenever(h.scenario.observeStashSummary(any())).doAnswer {
            if (it.getArgument<StakingState.Stash.None>(0).chain.id == h.a.id) h.summariesA else flow {
                emit(h.summary); fail.await(); throw IOException("Public local summary unavailable")
            }
        }
        h.start(); h.assets.emit(h.asset(h.b)); h.state.value = h.stash(h.b); runCurrent()
        assertTrue(h.stake().stakeInfoViewState.staked.value!!.contains("BBB"))
        fail.complete(Unit); runCurrent()
        assertNull(h.values.last())
        h.assets.emit(h.asset(h.a)); h.state.value = h.stash(h.a); runCurrent()
        assertTrue(h.stake().stakeInfoViewState.staked.value!!.contains("AAA"))
        assertTrue(h.errors.isEmpty())
    }

    private class Harness(private val scope: TestScope, aChainId: String = "local-relay-body-a") {
        val account = ByteArray(32) { 1 }
        val a = chain(aChainId)
        val b = chain("local-relay-body-b")
        val state = MutableStateFlow<StakingState>(stash(a))
        val summary get() = StakeSummary(StashNoneStatus.INACTIVE, BigDecimal.ONE, BigDecimal.ZERO, 12, StakeSummarySource.from(asset(state.value.chain)))
        val summariesA = MutableSharedFlow<StakeSummary<StashNoneStatus>>(replay = 1).apply { tryEmit(summary) }
        val summariesB = MutableSharedFlow<StakeSummary<StashNoneStatus>>(replay = 1)
        val assets = MutableSharedFlow<Asset>(replay = 1).apply { tryEmit(asset(a)) }
        val values = mutableListOf<StakingViewState?>()
        val errors = mutableListOf<Throwable>()
        private val owned = CoroutineScope(scope.backgroundScope.coroutineContext +
            SupervisorJob(scope.backgroundScope.coroutineContext[Job]) + CoroutineExceptionHandler { _, e -> errors += e })
        private val resource = mock<ResourceManager> {
            on { getString(any()) } doReturn "Public label"
            on { getString(any(), any<Int>()) } doReturn "Public era"
            on { getString(any(), any<String>()) } doReturn "Public balance"
        }
        private val base = mock<BaseStakingViewModel> {
            on { stakingStateScope } doReturn owned
            on { enteredAmountEvent } doReturn emptyFlow()
        }
        private val interactor = mock<StakingInteractor> {
            on { currentAssetFlow() } doReturn assets
        }
        private val initialSummarySource = StakeSummarySource.from(asset(a))
        val scenario = mock<StakingRelayChainScenarioInteractor> {
            on { observeNominatorSummary(any()) } doReturn flowOf(StakeSummary(NominatorStatus.Active, BigDecimal.ONE, BigDecimal.ZERO, 12, initialSummarySource))
            on { observeValidatorSummary(any()) } doReturn flowOf(StakeSummary(ValidatorStatus.ACTIVE, BigDecimal.ONE, BigDecimal.ZERO, 12, initialSummarySource))
            on { stakingStateFlow() } doReturn state
            on { observeStashSummary(any()) } doAnswer {
                val selectedChain = it.getArgument<StakingState.Stash.None>(0).chain
                (if (selectedChain.id == a.id) summariesA else summariesB).map { value ->
                    value.copy(source = StakeSummarySource.from(asset(selectedChain)))
                }
            }
        }
        private val initialRewardToken = asset(a).token
        val rewards = mock<SoraStakingRewardsScenario> {
            onBlocking { getRewardAsset() } doReturn initialRewardToken
        }
        suspend fun start() {
            val vm = StakingRelaychainScenarioViewModel(
                interactor, scenario, resource, base, mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), rewards
            )
            scope.backgroundScope.launch { vm.getStakingViewStateFlow().catch { errors += it }.collect { values += it } }
            scope.runCurrent()
            assertTrue("Healthy initial fixture must produce a relay body: $errors", values.lastOrNull() is StakingViewState.RelayChain.Stake)
        }
        fun stake() = values.last() as StakingViewState.RelayChain.Stake
        fun stash(chain: Chain) = StakingState.Stash.None(chain, account, account, account)
        fun asset(chain: Chain, id: ByteArray = account, type: ChainAsset.StakingType = ChainAsset.StakingType.RELAYCHAIN): Asset {
            val chainId = chain.id
            val symbol = if (chainId == a.id) "AAA" else "BBB"
            val config = mock<ChainAsset> {
                on { this.chainId } doReturn chainId
                on { this.id } doReturn "native"
                on { this.symbol } doReturn symbol
                on { staking } doReturn type
                on { precision } doReturn 2
                on { iconUrl } doReturn ""
            }
            return Asset.createEmpty(config, 1, id, minSupportedVersion = null)
        }
        private fun chain(id: String) = mock<Chain> {
            on { this.id } doReturn id
            on { addressPrefix } doReturn 42
            on { ecosystem } doReturn Ecosystem.Substrate
        }
    }
}
