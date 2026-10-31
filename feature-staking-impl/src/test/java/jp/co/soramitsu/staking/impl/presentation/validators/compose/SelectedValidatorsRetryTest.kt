package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.NominatedValidator
import jp.co.soramitsu.staking.impl.presentation.common.SelectedValidatorsFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import jp.co.soramitsu.core.models.Asset as CoreAsset

/**
 * A Hub pool's Selected Validators list reads the pool's nominations, exposures and slash warnings remotely. A list
 * that cannot be read, a timeout included, shows Retry instead of closing the app or loading forever, and Retry reads
 * it again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SelectedValidatorsRetryTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<SelectedValidatorsViewModel>()
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" }
    }
    private val chain = mock<Chain> {
        on { id } doReturn AssetHubStaking.POLKADOT
        on { ecosystem } doReturn Ecosystem.Substrate
    }
    private val config = mock<CoreAsset> {
        on { chainId } doReturn AssetHubStaking.POLKADOT
        on { id } doReturn "dot"
        on { precision } doReturn 10
    }
    private val asset = Asset.createEmpty(config, 7, ByteArray(32) { 6 }, minSupportedVersion = null)
    private val reads = ArrayDeque<() -> Flow<GroupedList<NominatedValidator.Status.Group, NominatedValidator>>>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        // Background collectors must finish on the test Main dispatcher before it is reset.
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

    private fun screen(): SelectedValidatorsViewModel {
        val selected = SelectedValidatorsFlowState(emptyList(), canChangeValidators = false, poolName = "pool", poolId = BigInteger.ONE)
        val main = StakingPoolState(chain = chain, asset = asset, address = ByteArray(32) { 6 }.toAddress(0))
        val pools = mock<StakingPoolSharedStateProvider> {
            on { requireSelectedValidatorsState } doReturn selected
            on { requireMainState } doReturn main
        }
        val interactor = mock<StakingPoolInteractor> {
            onBlocking { nominatedValidatorsFlow(any(), any()) } doAnswer { reads.removeFirst().invoke() }
        }
        return SelectedValidatorsViewModel(pools, interactor, resources, mock(), mock()).also(owned::add)
    }

    @Test fun `a list that cannot be read shows Retry, and Retry reads it again`() {
        reads.addLast { flow { throw IOException("Asset Hub slash warnings unavailable") } }
        reads.addLast { flowOf(emptyMap()) }
        val vm = screen()

        settle { vm.errorDialogStateLiveData.value != null }
        val failure = requireNotNull(vm.errorDialogStateLiveData.value?.peekContent())
        assertEquals("Asset Hub slash warnings unavailable", failure.message)
        assertEquals("fixture-${R.string.common_retry}", failure.positiveButtonText)
        assertTrue(vm.state.value is LoadingState.Loading)

        failure.positiveClick()
        settle { vm.state.value is LoadingState.Loaded }
        assertTrue(vm.state.value is LoadingState.Loaded)
    }

    @Test fun `a read that times out offers Retry instead of loading forever`() {
        reads.addLast { flow { withTimeout(10) { awaitCancellation() } } }
        reads.addLast { flowOf(emptyMap()) }
        val vm = screen()

        settle { vm.errorDialogStateLiveData.value != null }
        val failure = requireNotNull(vm.errorDialogStateLiveData.value?.peekContent())
        assertEquals("fixture-${R.string.common_error_general_message}", failure.message)

        failure.positiveClick()
        settle { vm.state.value is LoadingState.Loaded }
        assertTrue(vm.state.value is LoadingState.Loaded)
    }
}
