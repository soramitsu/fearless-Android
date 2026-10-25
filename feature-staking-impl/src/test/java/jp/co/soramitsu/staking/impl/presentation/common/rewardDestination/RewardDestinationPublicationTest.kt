package jp.co.soramitsu.staking.impl.presentation.common.rewardDestination

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.models.Asset as ChainAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.rewards.SoraRewardCalculator
import jp.co.soramitsu.staking.impl.domain.rewards.SoraStakingRewardsScenario
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.select.SelectRewardDestinationViewModel
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*

/** Actual provider and select ViewModel. Calculators and public data dependencies are controlled. */
@OptIn(ExperimentalCoroutinesApi::class)
class RewardDestinationPublicationTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<SelectRewardDestinationViewModel>()
    private class ReadFailure(val marker: Any) : RuntimeException("reward read failed")
    private class ReadCancelled(val marker: Any) : CancellationException("reward read cancelled")
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        owned.forEach { it.viewModelScope.cancel() }
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private inner class Fixture(initialBonded: AssetAmountState<BigInteger> = AssetAmountState.Known(BigInteger.valueOf(1_000))) {
        val config = mock<ChainAsset> {
            on { chainId } doReturn "captured-chain"
            on { id } doReturn "native"
            on { symbol } doReturn "UNIT"
            on { precision } doReturn 2
            on { staking } doReturn ChainAsset.StakingType.RELAYCHAIN
            on { priceId } doReturn "unit"
            on { priceProvider } doReturn null
        }
        val chain = mock<Chain> { on { id } doReturn "captured-chain" }
        val token = Token(config, AssetAmountState.Known(BigDecimal("2")), AssetAmountState.Missing,
            FiatValuationContext("usd"), "usd", "unit")
        val initial = Asset.createEmpty(config, 7, byteArrayOf(1), minSupportedVersion = null)
            .copy(token = token, bondedInPlanksState = initialBonded)
        val assets = MutableStateFlow(initial)
        val stash = mock<StakingState.Stash.None> { on { this.chain } doReturn this@Fixture.chain }
        val resources = mock<ResourceManager> {
            on { getString(any()) } doReturn "Unavailable"
            on { getString(any(), any()) } doReturn "gain"
        }
        val interactor = mock<StakingInteractor> { on { currentAssetFlow() } doReturn assets }
        val shared = mock<StakingSharedState> {
            on { assetWithChain } doReturn flowOf(SingleAssetSharedState.AssetWithChain(chain, config))
        }
        val scenario = mock<StakingScenarioInteractor> {
            onBlocking { getRewardDestination(stash) } doReturn RewardDestination.Restake
        }
        val sora = mock<SoraStakingRewardsScenario>()
        val calls = mutableListOf<Triple<BigDecimal, Boolean, String>>()
        var calculation: suspend (BigDecimal, Boolean, String) -> PeriodReturns = { amount, _, _ ->
            PeriodReturns(amount, BigDecimal.ONE)
        }
        val calculator = mock<RewardCalculator> {
            onBlocking { calculateReturns(any<BigDecimal>(), any(), any(), any()) } doSuspendableAnswer {
                val amount = it.getArgument<BigDecimal>(0)
                val compound = it.getArgument<Boolean>(2)
                val chainId = it.getArgument<String>(3)
                calls += Triple(amount, compound, chainId)
                calculation(amount, compound, chainId)
            }
        }
        var creation: suspend (ChainAsset) -> RewardCalculator = { calculator }
        val created = mutableListOf<ChainAsset>()
        val factory = mock<RewardCalculatorFactory> {
            onBlocking { create(any()) } doSuspendableAnswer {
                val config = it.getArgument<ChainAsset>(0)
                created += config
                creation(config)
            }
        }
        val provider = RewardDestinationProvider(resources, interactor, scenario, mock(), mock(), shared, mock(), sora)
        val publications = mutableListOf<RewardDestinationEstimations>()
        private val observer = Observer<RewardDestinationEstimations> { publications += it }
        init { provider.rewardReturnsLiveData.observeForever(observer) }
        fun viewModel(): SelectRewardDestinationViewModel {
            val relay = mock<StakingRelayChainScenarioInteractor> {
                on { selectedAccountStakingStateFlow() } doReturn flowOf(stash)
            }
            return SelectRewardDestinationViewModel(mock(), interactor, relay, factory, resources, mock(), mock(),
                ValidationExecutor(resources), mock(), provider).also { owned += it }
        }
        fun assertUnavailable() {
            val current = requireNotNull(provider.rewardReturnsLiveData.value)
            listOf(current.restake, current.payout).forEach {
                assertEquals("Unavailable", it.amount); assertEquals("Unavailable", it.gain); assertNull(it.fiatAmount)
            }
        }
        fun assertKnown() {
            val current = requireNotNull(provider.rewardReturnsLiveData.value)
            listOf(current.restake, current.payout).forEach {
                assertNotEquals("Unavailable", it.amount); assertNotEquals("Unavailable", it.gain); assertNotNull(it.fiatAmount)
            }
        }
    }

    @Test fun invalidationClearsAllSixFieldsAndCalculationUsesTheCapturedChain() = runTest(dispatcher) {
        val f = Fixture()
        f.provider.updateReturns(f.calculator, f.initial, BigDecimal.TEN)
        f.assertKnown()
        assertEquals(listOf("captured-chain", "captured-chain"), f.calls.map { it.third })
        f.provider.invalidateReturns()
        f.assertUnavailable()
        f.provider.updateReturns(f.calculator, f.initial, BigDecimal.ZERO)
        f.assertKnown()
        assertEquals(listOf(BigDecimal.ZERO, BigDecimal.ZERO), f.calls.takeLast(2).map { it.first })
    }

    @Test fun reentrantInvalidationDuringClearCannotBeAdoptedByTheEarlierCalculation() = runTest(dispatcher) {
        val f = Fixture()
        var revoke = true
        val observer = Observer<RewardDestinationEstimations> {
            if (revoke) { revoke = false; f.provider.invalidateReturns() }
        }
        f.provider.rewardReturnsLiveData.observeForever(observer)
        try {
            f.provider.updateReturns(f.calculator, f.initial, BigDecimal.TEN)
            f.assertUnavailable()
            assertTrue(f.calls.isEmpty())
        } finally { f.provider.rewardReturnsLiveData.removeObserver(observer) }
    }

    @Test fun invalidationRejectsCalculationWhichCompletesAfterNewerUnavailableState() = runTest(dispatcher) {
        val f = Fixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        f.calculation = { amount, _, _ -> entered.complete(Unit); release.await(); PeriodReturns(amount, BigDecimal.ONE) }
        val old = backgroundScope.launch { f.provider.updateReturns(f.calculator, f.initial, BigDecimal.TEN) }
        try {
            runCurrent(); assertTrue(entered.isCompleted)
            f.provider.invalidateReturns(); val unavailable = f.provider.rewardReturnsLiveData.value
            release.complete(Unit); runCurrent(); old.join()
            assertSame(unavailable, f.provider.rewardReturnsLiveData.value)
            assertEquals(1, f.calls.size)
        } finally { release.complete(Unit) }
    }

    @Test fun lateSoraRewardTokenLookupCannotRestoreInvalidatedEstimates() = runTest(dispatcher) {
        val f = Fixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val calculator = mock<SoraRewardCalculator> {
            onBlocking { calculateReturns(any<BigDecimal>(), any(), any(), any()) } doReturn PeriodReturns(BigDecimal.TEN, BigDecimal.ONE)
        }
        whenever(f.sora.getRewardAsset()).doSuspendableAnswer {
            entered.complete(Unit); withContext(NonCancellable) { release.await() }; f.token
        }
        val old = backgroundScope.launch { f.provider.updateReturns(calculator, f.initial, BigDecimal.TEN) }
        try {
            runCurrent(); assertTrue(entered.isCompleted)
            f.provider.invalidateReturns(); val unavailable = f.provider.rewardReturnsLiveData.value
            release.complete(Unit); runCurrent(); old.join()
            assertSame(unavailable, f.provider.rewardReturnsLiveData.value)
        } finally { release.complete(Unit) }
    }

    @Test fun providerFailureRevokesPriorValuesAndPropagatesTheOriginalFailure() = runTest(dispatcher) {
        val f = Fixture(); f.provider.updateReturns(f.calculator, f.initial, BigDecimal.TEN)
        val failure = ReadFailure(Any()); f.calculation = { _, _, _ -> throw failure }
        assertSame(failure, runCatching { f.provider.updateReturns(f.calculator, f.initial, BigDecimal.ONE) }.exceptionOrNull())
        f.assertUnavailable()
    }

    @Test fun cancelledProviderCannotPublishAfterNonCancellableCalculationCleanup() = runTest(dispatcher) {
        val f = Fixture(); val release = CompletableDeferred<Unit>(); val entered = CompletableDeferred<Unit>()
        f.calculation = { amount, _, _ -> entered.complete(Unit); withContext(NonCancellable) { release.await() }; PeriodReturns(amount, BigDecimal.ONE) }
        val old = backgroundScope.launch { f.provider.updateReturns(f.calculator, f.initial, BigDecimal.TEN) }
        val reason = ReadCancelled(Any()); var completion: Throwable? = null
        old.invokeOnCompletion { completion = it }
        try {
            runCurrent(); assertTrue(entered.isCompleted)
            val unavailable = f.provider.rewardReturnsLiveData.value
            old.cancel(reason); release.complete(Unit); runCurrent(); old.join()
            assertSame(reason, completion)
            assertSame(unavailable, f.provider.rewardReturnsLiveData.value)
        } finally { release.complete(Unit) }
    }

    @Test fun actualViewModelClearsUnknownAndRecoversKnownZeroWithoutCalculatingUnknown() = runTest(dispatcher) {
        val f = Fixture(); f.viewModel(); runCurrent(); f.assertKnown()
        val calculations = f.calls.size; val creations = f.created.size
        f.assets.value = f.initial.copy(bondedInPlanksState = AssetAmountState.Missing)
        runCurrent(); f.assertUnavailable()
        assertEquals(calculations, f.calls.size); assertEquals(creations, f.created.size)
        f.assets.value = f.initial.copy(bondedInPlanksState = AssetAmountState.Known(BigInteger.ZERO))
        runCurrent(); f.assertKnown()
        assertEquals(listOf(BigDecimal("0.00"), BigDecimal("0.00")), f.calls.takeLast(2).map { it.first })
    }

    @Test fun initiallyUnknownBalanceDoesNotStartCalculatorCreation() = runTest(dispatcher) {
        val f = Fixture(AssetAmountState.Missing); f.viewModel(); runCurrent()
        f.assertUnavailable(); assertTrue(f.created.isEmpty()); assertTrue(f.calls.isEmpty())
    }

    @Test fun newUnknownBalanceRevokesBeforeCancelledFactoryCleanupFinishes() = runTest(dispatcher) {
        val f = Fixture(); val release = CompletableDeferred<Unit>(); val entered = CompletableDeferred<Unit>()
        f.viewModel(); runCurrent(); f.assertKnown()
        f.creation = { entered.complete(Unit); withContext(NonCancellable) { release.await() }; f.calculator }
        try {
            f.assets.value = f.initial.copy(bondedInPlanksState = AssetAmountState.Known(BigInteger.valueOf(2_000)))
            runCurrent(); assertTrue(entered.isCompleted)
            f.assets.value = f.initial.copy(bondedInPlanksState = AssetAmountState.Missing)
            runCurrent(); f.assertUnavailable()
            val unavailable = f.provider.rewardReturnsLiveData.value
            val count = f.calls.size
            assertFalse(release.isCompleted)
            release.complete(Unit); runCurrent()
            assertSame(unavailable, f.provider.rewardReturnsLiveData.value)
            assertEquals(count, f.calls.size)
        } finally { release.complete(Unit) }
    }

    @Test fun factoryFailureReportsAndNextKnownAssetCanRecover() = runTest(dispatcher) {
        val f = Fixture(); val failure = ReadFailure(Any())
        f.creation = { throw failure }
        val vm = f.viewModel(); runCurrent(); f.assertUnavailable()
        assertEquals(failure.message, vm.errorLiveData.value?.peekContent())
        f.creation = { f.calculator }
        f.assets.value = f.initial.copy(bondedInPlanksState = AssetAmountState.Known(BigInteger.valueOf(2_000)))
        runCurrent(); f.assertKnown(); assertEquals(2, f.created.size)
    }

    @Test fun calculationFailureReportsAndNextKnownAssetCanRecover() = runTest(dispatcher) {
        val f = Fixture(); val failure = ReadFailure(Any())
        f.calculation = { _, _, _ -> throw failure }
        val vm = f.viewModel(); runCurrent(); f.assertUnavailable()
        assertEquals(failure.message, vm.errorLiveData.value?.peekContent())
        f.calculation = { amount, _, _ -> PeriodReturns(amount, BigDecimal.ONE) }
        f.assets.value = f.initial.copy(bondedInPlanksState = AssetAmountState.Known(BigInteger.valueOf(2_000)))
        runCurrent(); f.assertKnown()
    }
}
