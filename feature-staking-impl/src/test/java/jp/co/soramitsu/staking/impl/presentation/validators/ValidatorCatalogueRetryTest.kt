package jp.co.soramitsu.staking.impl.presentation.validators

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.validators.change.start.StartChangeValidatorsViewModel
import jp.co.soramitsu.staking.impl.presentation.validators.compose.StartSelectValidatorsViewModel
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking

/**
 * The validator start screens keep both choices disabled until the validator catalogue loads. A failed load is shown
 * with a retry on the screen's own catalogue owner, never a crash; cancellation shows nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ValidatorCatalogueRetryTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<BaseViewModel>()
    private val screen = mock<Lifecycle>()
    private val dialogOnTop = mock<Lifecycle>()
    private val outcomes = ArrayDeque<Throwable?>()
    private val router = mock<StakingRouter> {
        on { currentStackEntryLifecycle } doReturn screen doReturn dialogOnTop
        on { listenAlertResultFlowFromStartSelectValidatorsScreen(any()) } doReturn emptyFlow()
        on { listenAlertResultFlowFromStartChangeValidatorsScreen(any()) } doReturn emptyFlow()
    }
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" }
    }
    private val factory = mock<ValidatorRecommendatorFactory> {
        onBlocking { awaitBlockCreatorsLoading(any()) } doSuspendableAnswer {
            outcomes.removeFirstOrNull()?.let { throw it }
            Unit
        }
    }
    private val relay = mock<StakingRelayChainScenarioInteractor> {
        onBlocking { maxValidatorsPerNominator() } doReturn 16
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        owned.forEach { it.viewModelScope.cancel() }
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private fun startSelect() = StartSelectValidatorsViewModel(resources, factory, router, StakingPoolSharedStateProvider()).also(owned::add)

    private fun startChange() = StartChangeValidatorsViewModel(router, factory, SetupStakingSharedState(), resources, relay).also(owned::add)

    private fun dialog(vm: BaseViewModel) = vm.errorDialogStateLiveData.value?.peekContent()

    @Test fun `start select keeps its choices disabled until a retried load succeeds`() {
        outcomes += IllegalStateException("node unreachable")
        val vm = startSelect()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.isLoading)
        val failure = requireNotNull(dialog(vm))
        assertEquals("node unreachable", failure.message)
        assertEquals("fixture-${R.string.common_retry}", failure.positiveButtonText)

        failure.positiveClick()
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.isLoading)
        verifyBlocking(factory, times(2)) { awaitBlockCreatorsLoading(screen) }
        verifyBlocking(factory, never()) { awaitBlockCreatorsLoading(dialogOnTop) }
    }

    @Test fun `start change keeps its choices disabled until a retried load succeeds`() {
        outcomes += IllegalStateException(null as String?)
        val vm = startChange()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.validatorsLoading.value)
        val failure = requireNotNull(dialog(vm))
        assertEquals("fixture-${R.string.common_error_general_message}", failure.message)

        failure.positiveClick()
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.validatorsLoading.value)
        verifyBlocking(factory, times(2)) { awaitBlockCreatorsLoading(screen) }
    }

    @Test fun `a cancelled load shows no failure`() {
        outcomes += CancellationException("left")
        val select = startSelect()
        val change = startChange()
        dispatcher.scheduler.runCurrent()
        assertNull(dialog(select))
        assertNull(dialog(change))
        assertTrue(select.state.value.isLoading)
    }
}
