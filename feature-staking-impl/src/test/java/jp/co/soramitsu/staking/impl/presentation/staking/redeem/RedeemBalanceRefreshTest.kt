package jp.co.soramitsu.staking.impl.presentation.staking.redeem

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.api.presentation.model.AmountModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/**
 * Redeem reads the account's ledger again when it opens; on an Asset Hub that read includes the Hub's active era. A
 * refresh that cannot be read shows the amount as unavailable with Retry and blocks the redeem until a refresh
 * succeeds: Confirm refreshes instead of redeeming. It never closes the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RedeemBalanceRefreshTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val unavailable = "fixture-${R.string.portfolio_balance_unavailable}"
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" }
    }
    private val refreshed = StakingBalanceModel(
        AmountModel(BigDecimal.TEN, "10", null),
        AmountModel(BigDecimal.ONE, "1", null),
        AmountModel(BigDecimal("2.5"), "2.5", null)
    )
    private val balances = ArrayDeque<Flow<StakingBalanceModel>>()
    private val scenario = mock<StakingScenarioInteractor> {
        on { stakingStateFlow() } doReturn emptyFlow()
        on { getSelectedAccountAddress() } doReturn emptyFlow()
        on { getCollatorAddress(anyOrNull()) } doReturn emptyFlow()
        on { getStakingBalanceFlow(anyOrNull()) } doAnswer { balances.removeFirst() }
    }
    private val staking = mock<StakingInteractor> {
        on { currentAssetFlow() } doReturn emptyFlow()
    }
    private val fees = mock<FeeLoaderMixin.Presentation>()
    private val owned = mutableListOf<RedeemViewModel>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        // The view model collects background (flowOn Default) flows at once. They must finish on the test Main
        // dispatcher before it is reset, or their completion reaches a missing Main dispatcher in a later test.
        val jobs = owned.map { it.viewModelScope.coroutineContext.job.apply { cancel() } }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (jobs.any { !it.isCompleted } && System.nanoTime() < deadline) {
            dispatcher.scheduler.runCurrent()
            Thread.sleep(1)
        }
        Dispatchers.resetMain()
    }

    private fun redeem() = RedeemViewModel(
        router = mock(),
        stakingScenarioInteractor = scenario,
        interactor = staking,
        redeemInteractor = mock(),
        resourceManager = resources,
        validationExecutor = mock(),
        iconGenerator = mock(),
        chainRegistry = mock(),
        feeLoaderMixin = fees,
        externalAccountActions = mock(),
        savedStateHandle = SavedStateHandle(mapOf(PAYLOAD_KEY to RedeemPayload(null, null)))
    ).also(owned::add)

    @Test fun `an unreadable refresh blocks the redeem until Confirm refreshes it`() {
        balances.addLast(flow { throw IllegalStateException("Asset Hub active era unavailable") })
        balances.addLast(flowOf(refreshed))
        val vm = redeem()
        val amounts = mutableListOf<String>()
        vm.viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.stakingUnlockAmount.collect { amounts += it } }
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(unavailable), amounts)
        val failure = requireNotNull(vm.errorDialogStateLiveData.value?.peekContent())
        assertEquals("Asset Hub active era unavailable", failure.message)
        assertEquals("fixture-${R.string.common_retry}", failure.positiveButtonText)

        vm.confirmClicked()
        dispatcher.scheduler.runCurrent()
        verify(fees, never()).requireFee(any(), any())
        assertEquals(2, amounts.size)
        assertNotEquals(unavailable, amounts.last())

        vm.confirmClicked()
        verify(fees).requireFee(any(), any())
    }

    @Test fun `Retry refreshes, and Confirm waits while a refresh runs`() {
        balances.addLast(flow { throw IllegalStateException("node unreachable") })
        balances.addLast(flowOf(refreshed))
        val vm = redeem()
        dispatcher.scheduler.runCurrent()
        requireNotNull(vm.errorDialogStateLiveData.value?.peekContent()).positiveClick()
        // The retried refresh has not run yet: Confirm neither redeems nor starts another refresh.
        vm.confirmClicked()
        dispatcher.scheduler.runCurrent()
        verify(scenario, times(2)).getStakingBalanceFlow(anyOrNull())
        verify(fees, never()).requireFee(any(), any())

        vm.confirmClicked()
        verify(fees).requireFee(any(), any())
    }
}
