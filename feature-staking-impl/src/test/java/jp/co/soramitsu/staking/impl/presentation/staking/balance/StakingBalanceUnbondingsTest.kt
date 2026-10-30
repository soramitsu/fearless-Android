package jp.co.soramitsu.staking.impl.presentation.staking.balance

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Asset as ChainAsset
import jp.co.soramitsu.staking.api.domain.model.DelegationAction
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.model.Unbonding
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.balance.rebond.RebondKind
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.math.BigInteger

/**
 * The balance screen lists the unbondings with their time left, which reads the era clock; on an Asset Hub that read
 * is remote and can fail. A list that cannot be read shows Retry and keeps rebonding off; it never closes the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingBalanceUnbondingsTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<StakingBalanceViewModel>()
    private val unbonding = Unbonding(BigInteger.TEN, timeLeft = 60_000L, calculatedAt = 0L, type = DelegationAction.UNSTAKE)
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" }
    }
    private val config = mock<ChainAsset> {
        on { chainId } doReturn "fixture"
        on { id } doReturn "native"
        on { precision } doReturn 2
        on { staking } doReturn ChainAsset.StakingType.RELAYCHAIN
    }
    private val asset = Asset.createEmpty(config, 7, byteArrayOf(1), minSupportedVersion = null)
    private var unbondingRead: () -> Flow<List<Unbonding>> = { flowOf(emptyList()) }
    private var rebondingRead: () -> List<Unbonding> = { emptyList() }
    private var unbondingReads = 0
    private val scenario = mock<StakingScenarioInteractor> {
        on { getStakingBalanceFlow(anyOrNull()) } doReturn emptyFlow()
        onBlocking { currentUnbondingsFlow(anyOrNull()) } doAnswer { unbondingReads++; unbondingRead() }
        onBlocking { getRebondingUnbondings(anyOrNull()) } doAnswer { rebondingRead() }
    }
    private val router = mock<StakingRouter>()
    private val observer = Observer<Any?> { }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        try {
            // Background (flowOn Default) collectors must finish on the test Main dispatcher before it is reset.
            owned.forEach { it.viewModelScope.cancel() }
            runBlocking {
                withTimeout(5_000) {
                    while (owned.any { !it.viewModelScope.coroutineContext.job.isCompleted }) {
                        dispatcher.scheduler.runCurrent()
                        delay(1)
                    }
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun balance(): StakingBalanceViewModel {
        val interactor = mock<StakingInteractor> { on { currentAssetFlow() } doReturn flowOf(asset) }
        return StakingBalanceViewModel(router, mock(), mock(), resources, interactor, scenario, SavedStateHandle()).also {
            owned += it
            it.unbondingModelsLiveData.observeForever(observer)
            it.unbondingEnabledLiveData.observeForever(observer)
        }
    }

    @Test fun `an unreadable era clock shows Retry and keeps rebonding off until a read succeeds`() = runTest(dispatcher) {
        unbondingRead = { flow { throw IllegalStateException("Asset Hub active era unavailable") } }
        val vm = balance()
        runCurrent()

        val failure = requireNotNull(vm.errorDialogStateLiveData.value?.peekContent())
        assertEquals("Asset Hub active era unavailable", failure.message)
        assertEquals("fixture-${R.string.common_retry}", failure.positiveButtonText)
        assertEquals(false, vm.unbondingEnabledLiveData.value)

        unbondingRead = { flowOf(listOf(unbonding)) }
        rebondingRead = { listOf(unbonding) }
        failure.positiveClick()
        runCurrent()
        assertEquals(2, unbondingReads)
        assertEquals(true, vm.unbondingEnabledLiveData.value)
    }

    @Test fun `a read that times out has failed`() = runTest(dispatcher) {
        unbondingRead = { flow { withTimeout(10) { awaitCancellation() } } }
        val vm = balance()
        advanceUntilIdle()

        val failure = requireNotNull(vm.errorDialogStateLiveData.value?.peekContent())
        assertEquals("fixture-${R.string.common_error_general_message}", failure.message)
        assertEquals(false, vm.unbondingEnabledLiveData.value)
    }

    @Test fun `a rebond that cannot read the unbondings shows Retry instead of opening`() = runTest(dispatcher) {
        unbondingRead = { flowOf(listOf(unbonding)) }
        rebondingRead = { listOf(unbonding) }
        val vm = balance()
        runCurrent()
        assertEquals(true, vm.unbondingEnabledLiveData.value)

        rebondingRead = { throw IllegalStateException("node unreachable") }
        vm.rebondKindChosen(RebondKind.ALL)
        runCurrent()
        assertEquals("node unreachable", vm.errorDialogStateLiveData.value?.peekContent()?.message)
        verify(router, never()).openConfirmRebond(any())
    }
}
