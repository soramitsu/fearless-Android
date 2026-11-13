package jp.co.soramitsu.staking.impl.presentation.validators.current

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.NominatedValidator
import jp.co.soramitsu.staking.api.domain.model.Nominations
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.validators.current.CurrentValidatorsInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.mockito.kotlin.mock
import java.io.IOException
import java.math.BigInteger
import java.util.concurrent.TimeUnit

/**
 * The Current Validators list reads exposures and slash warnings, which on an Asset Hub are remote reads that can
 * fail. A list that cannot be read shows Retry instead of closing the app, and Retry reads it again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrentValidatorsRetryTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<CurrentValidatorsViewModel>()
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" }
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        // Background (flowOn Default) collectors must finish on the test Main dispatcher before it is reset.
        val jobs = owned.map { it.viewModelScope.coroutineContext.job.apply { cancel() } }
        settle { jobs.all { it.isCompleted } }
        Dispatchers.resetMain()
    }

    // The list is read on a background dispatcher; run Main until its result arrives.
    private fun settle(done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!done() && System.nanoTime() < deadline) {
            dispatcher.scheduler.runCurrent()
            Thread.sleep(1)
        }
    }

    @Test fun `a list that cannot be read shows Retry, and Retry reads it again`() {
        val chain = mock<Chain> {
            on { id } doReturn AssetHubStaking.POLKADOT
            on { ecosystem } doReturn Ecosystem.Substrate
        }
        val stash = ByteArray(32) { 6 }
        val nominator = StakingState.Stash.Nominator(chain, stash, stash, stash, Nominations(emptyList(), BigInteger.ONE, false))
        val reads = ArrayDeque<Flow<GroupedList<NominatedValidator.Status.Group, NominatedValidator>>>()
        reads.addLast(flow { throw IOException("Asset Hub active era unavailable") })
        reads.addLast(flowOf(emptyMap()))
        val validators = mock<CurrentValidatorsInteractor> {
            onBlocking { nominatedValidatorsFlow(any()) } doAnswer { reads.removeFirst() }
        }
        val vm = CurrentValidatorsViewModel(
            router = mock(),
            resourceManager = resources,
            stakingInteractor = mock<StakingInteractor> { on { currentAssetFlow() } doReturn emptyFlow() },
            stakingRelayChainScenarioInteractor = mock<StakingRelayChainScenarioInteractor> {
                on { selectedAccountStakingStateFlow() } doReturn flowOf(nominator)
            },
            iconGenerator = mock(),
            currentValidatorsInteractor = validators,
            setupStakingSharedState = mock()
        ).also(owned::add)

        settle { vm.errorDialogStateLiveData.value != null }
        val failure = requireNotNull(vm.errorDialogStateLiveData.value?.peekContent())
        assertEquals("Asset Hub active era unavailable", failure.message)
        assertEquals("fixture-${R.string.common_retry}", failure.positiveButtonText)

        failure.positiveClick()
        settle { reads.isEmpty() }
        assertEquals(0, reads.size)
    }
}
