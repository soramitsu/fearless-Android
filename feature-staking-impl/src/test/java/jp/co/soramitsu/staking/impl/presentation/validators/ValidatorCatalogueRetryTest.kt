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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking

/**
 * The validator start screens keep their choices disabled while the validator catalogue loads. A failed load is shown
 * with Retry and enables the choices again: choosing one loads the catalogue before acting, so a dismissed dialog or a
 * recreated screen never strands the screen. A loader that times out has failed; only the screen's own cancellation is
 * silent. Every load uses the catalogue owner captured when the screen opened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ValidatorCatalogueRetryTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<BaseViewModel>()
    private val screen = mock<Lifecycle>()
    private val dialogOnTop = mock<Lifecycle>()
    private val loads = ArrayDeque<suspend () -> Unit>()
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
            loads.removeFirstOrNull()?.invoke()
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

    private fun fail(message: String?) {
        loads.addLast { throw IllegalStateException(message) }
    }

    @Test fun `a failed load enables the choices and choosing one loads again before acting`() {
        fail("node unreachable")
        val vm = startSelect()
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.isLoading)
        val failure = requireNotNull(dialog(vm))
        assertEquals("node unreachable", failure.message)
        assertEquals("fixture-${R.string.common_retry}", failure.positiveButtonText)
        verify(router, never()).openAlertFromStartSelectValidatorsScreen(any(), any())

        // The dialog was dismissed: the screen is still usable.
        val reload = CompletableDeferred<Unit>()
        loads.addLast { reload.await() }
        vm.onRecommendedClick()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.isLoading)
        verify(router, never()).openAlertFromStartSelectValidatorsScreen(any(), any())
        reload.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.isLoading)
        verify(router).openAlertFromStartSelectValidatorsScreen(any(), any())
        verifyBlocking(factory, times(2)) { awaitBlockCreatorsLoading(screen) }
        verifyBlocking(factory, never()) { awaitBlockCreatorsLoading(dialogOnTop) }
    }

    @Test fun `retry loads again without acting, then the choices act at once`() {
        fail(null)
        val vm = startChange()
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.validatorsLoading.value)
        val failure = requireNotNull(dialog(vm))
        assertEquals("fixture-${R.string.common_error_general_message}", failure.message)

        failure.positiveClick()
        assertTrue(vm.validatorsLoading.value)
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.validatorsLoading.value)
        verify(router, never()).openSelectCustomValidators()

        vm.goToCustomClicked()
        verify(router).openSelectCustomValidators()
        verifyBlocking(factory, times(2)) { awaitBlockCreatorsLoading(screen) }
    }

    @Test fun `a loader that times out has failed`() {
        loads.addLast { withTimeout(10) { awaitCancellation() } }
        val vm = startSelect()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
        assertEquals("fixture-${R.string.common_error_general_message}", requireNotNull(dialog(vm)).message)
    }

    @Test fun `the screen's own cancellation shows nothing`() {
        loads.addLast { awaitCancellation() }
        val vm = startChange()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.validatorsLoading.value)
        vm.viewModelScope.cancel()
        dispatcher.scheduler.runCurrent()
        assertNull(dialog(vm))
        verify(router, never()).openSelectCustomValidators()
    }
}
