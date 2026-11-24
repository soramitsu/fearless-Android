package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.CandidateCapacity
import jp.co.soramitsu.staking.api.domain.model.CandidateInfo
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.api.domain.model.CollatorDelegation
import jp.co.soramitsu.staking.api.domain.model.DelegatorStateStatus
import jp.co.soramitsu.staking.api.domain.model.Identity
import jp.co.soramitsu.staking.api.domain.model.Round
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.model.NominatorStatus
import jp.co.soramitsu.staking.impl.domain.model.StakeSummary
import jp.co.soramitsu.staking.impl.domain.model.StakeSummarySource
import jp.co.soramitsu.staking.impl.domain.model.StashNoneStatus
import jp.co.soramitsu.staking.impl.domain.model.ValidatorStatus
import jp.co.soramitsu.staking.impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.rewards.SoraStakingRewardsScenario
import jp.co.soramitsu.staking.impl.domain.rewards.SubqueryRewardCalculator
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.welcome.WelcomeStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.welcome.WelcomeStakingValidationPayload
import jp.co.soramitsu.core.models.Asset as ChainAsset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StakingViewStateOldTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val subquery = Chain.ExternalApi(
        staking = Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.SUBQUERY, "https://example.org"),
        history = null,
        crowdloans = null
    )
    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val id = StakingLaneFixtures.accountId

    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val relay = mockk<StakingRelayChainScenarioInteractor>(relaxed = true)
    private val parachain = mockk<StakingParachainScenarioInteractor>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val router = mockk<StakingRouter>(relaxed = true)
    private val rewardFactory = mockk<RewardCalculatorFactory>(relaxed = true)
    private val calculator = mockk<RewardCalculator>(relaxed = true)
    private val subqueryCalculator = mockk<SubqueryRewardCalculator>(relaxed = true)
    private val setupShared = SetupStakingSharedState()
    private val validationExecutor = ValidationExecutor(resources)
    private val sora = mockk<SoraStakingRewardsScenario>(relaxed = true)
    private val errors = mutableListOf<Throwable>()

    private val factory = StakingViewStateFactory(
        interactor, setupShared, resources, router, rewardFactory, validationExecutor, relay, parachain, sora
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        coEvery { interactor.syncStakingRewards(any(), any()) } returns Result.failure(IllegalStateException("sync failed"))
        coEvery { rewardFactory.create(any<ChainAsset>()) } returns calculator
        every { rewardFactory.createSubquery() } returns subqueryCalculator
        coEvery { calculator.calculateReturns(any<BigDecimal>(), any(), any(), any()) } returns PeriodReturns(BigDecimal.ONE, BigDecimal("0.15"))
        coEvery { calculator.calculateMaxAPY(any()) } returns BigDecimal("0.2")
        coEvery { calculator.calculateAvgAPY() } returns BigDecimal("0.1")
        coEvery { subqueryCalculator.calculateReturns(any<BigDecimal>(), any(), any(), any()) } returns PeriodReturns(BigDecimal.ONE, BigDecimal("0.15"))
        coEvery { subqueryCalculator.calculateMaxAPY(any()) } returns BigDecimal("0.3")
        coEvery { subqueryCalculator.calculateAvgAPY() } returns BigDecimal("0.12")
    }

    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not reached", condition())
    }

    private fun <S> summary(status: S, a: Asset = asset) =
        StakeSummary(status, BigDecimal.TEN, BigDecimal.ONE, 12, StakeSummarySource.from(a))

    private fun <S> StakeViewState<S>.awaitSummary(): StakeSummaryModel<S> {
        waitUntil { stakeSummaryFlow.replayCache.firstOrNull() is LoadingState.Loaded }
        return (stakeSummaryFlow.replayCache.first() as LoadingState.Loaded).data
    }

    @Test
    fun validatorStateSummarisesStakeAndOffersManagementWithoutPayoutsOnPlainChains() {
        every { relay.observeValidatorSummary(any()) } returns flowOf(summary(ValidatorStatus.INACTIVE))
        val state = factory.createValidatorViewState(StakingState.Stash.Validator(chain, id, id, id, mockk()), flowOf(asset), scope) { errors += it }

        val model = state.awaitSummary()
        assertEquals(ValidatorStatus.INACTIVE, model.status)
        assertTrue(model.totalStaked, model.totalStaked.contains("10"))
        assertTrue(state.manageStakingActionsButtonVisible)

        state.statusClicked()
        assertEquals(
            "s${jp.co.soramitsu.feature_staking_impl.R.string.staking_nominator_status_alert_inactive_title}",
            state.showStatusAlertEvent.value!!.peekContent().first
        )
        state.moreActionsClicked()
        val actions = state.showManageActionsEvent.value!!.peekContent().availableActions
        assertEquals(setOf(ManageStakeAction.BALANCE, ManageStakeAction.CONTROLLER, ManageStakeAction.REWARD_DESTINATION), actions)

        ManageStakeAction.entries.forEach(state::manageActionChosen)
        verify { router.openStakingBalance() }
        verify { router.openControllerAccount() }
        verify { router.openChangeRewardDestination() }
        verify(exactly = 0) { router.openPayouts() }
        verify(exactly = 0) { router.openCurrentValidators() }
        assertEquals(listOf("sync failed"), errors.map { it.message })
    }

    @Test
    fun nominatorStateOnIndexedChainOffersPayoutsAndExplainsEachStatus() {
        val indexed = StakingLaneFixtures.chain(chainAsset, externalApi = subquery)
        val statuses = listOf(
            NominatorStatus.Active to jp.co.soramitsu.feature_staking_impl.R.string.staking_nominator_status_alert_active_message,
            NominatorStatus.Waiting(1000L) to jp.co.soramitsu.feature_staking_impl.R.string.staking_nominator_status_alert_waiting_message,
            NominatorStatus.Inactive(NominatorStatus.Inactive.Reason.MIN_STAKE) to
                jp.co.soramitsu.feature_staking_impl.R.string.staking_nominator_status_alert_low_stake,
            NominatorStatus.Inactive(NominatorStatus.Inactive.Reason.NO_ACTIVE_VALIDATOR) to
                jp.co.soramitsu.feature_staking_impl.R.string.staking_nominator_status_alert_no_validators
        )
        statuses.forEach { (status, message) ->
            every { relay.observeNominatorSummary(any()) } returns flowOf(summary(status))
            val state = runBlocking {
                factory.createNominatorViewState(StakingState.Stash.Nominator(indexed, id, id, id, mockk()), flowOf(asset), scope) { errors += it }
            }
            assertEquals(status, state.awaitSummary().status)
            state.statusClicked()
            assertEquals("s$message", state.showStatusAlertEvent.value!!.peekContent().second)
            state.moreActionsClicked()
            assertEquals(ManageStakeAction.entries.toSet(), state.showManageActionsEvent.value!!.peekContent().availableActions)
        }
        val state = runBlocking {
            factory.createNominatorViewState(StakingState.Stash.Nominator(indexed, id, id, id, mockk()), flowOf(asset), scope) { errors += it }
        }
        state.awaitSummary()
        state.manageActionChosen(ManageStakeAction.PAYOUTS)
        state.manageActionChosen(ManageStakeAction.VALIDATORS)
        verify { router.openPayouts() }
        verify { router.openCurrentValidators() }
    }

    @Test
    fun stashNoneStateExplainsInactiveBond() {
        every { relay.observeStashSummary(any()) } returns flowOf(summary(StashNoneStatus.INACTIVE))
        val state = factory.createStashNoneState(flowOf(asset), StakingState.Stash.None(chain, id, id, id), scope) { errors += it }

        assertEquals(StashNoneStatus.INACTIVE, state.awaitSummary().status)
        state.statusClicked()
        assertEquals(
            "s${jp.co.soramitsu.feature_staking_impl.R.string.staking_bonded_inactive}",
            state.showStatusAlertEvent.value!!.peekContent().second
        )
        state.manageActionChosen(ManageStakeAction.PAYOUTS)
        verify(exactly = 0) { router.openPayouts() }
    }

    @Test
    fun relayWelcomeEstimatesReturnsShowsRewardInfoAndValidatesNext() {
        setupShared.setupStakingProcess.value = SetupStakingProcess.Initial(StakingType.RELAYCHAIN)
        val shown = mutableListOf<String>()
        val system = ValidationSystem(CompositeValidation<WelcomeStakingValidationPayload, WelcomeStakingValidationFailure>(emptyList()))
        val state = runBlocking { factory.createRelayChainWelcomeViewState(flowOf(asset), scope, system) { shown += it } }

        assertEquals("10", state.enteredAmountFlow.value)
        val returns = runBlocking { state.returns.first() }
        assertTrue(returns.yearly.gain, returns.yearly.gain.isNotEmpty())
        assertEquals(chainAsset.chainId, runBlocking { state.chainId.first() })
        state.assetLiveData.observeForever { }
        state.amountFiat.observeForever { }

        state.infoActionClicked()
        waitUntil { state.showRewardEstimationEvent.value != null }
        val payload = state.showRewardEstimationEvent.value!!.peekContent()
        assertEquals("0.2%", payload.apr)
        assertEquals("0.1%", payload.apy)

        state.enteredAmountFlow.value = "4"
        state.nextClicked()
        waitUntil { setupShared.setupStakingProcess.value is SetupStakingProcess.SetupStep.Stash }
        assertEquals(BigDecimal("4"), (setupShared.setupStakingProcess.value as SetupStakingProcess.SetupStep.Stash).amount)
        verify { router.openSetupStaking() }
    }

    @Test
    fun parachainWelcomeUsesSubqueryAprAndMovesToSetup() {
        setupShared.setupStakingProcess.value = SetupStakingProcess.Initial(StakingType.PARACHAIN)
        val state = factory.createParachainWelcomeViewState(flowOf(asset), scope) { }

        val returns = runBlocking { state.returns.first() }
        assertTrue(returns.monthly.gain.isNotEmpty())
        state.infoActionClicked()
        waitUntil { state.showRewardEstimationEvent.value != null }
        assertEquals(
            jp.co.soramitsu.feature_staking_impl.R.string.staking_reward_info_apr_max,
            state.showRewardEstimationEvent.value!!.peekContent().maximumTitle
        )

        state.enteredAmountFlow.value = "3"
        state.nextClicked()
        waitUntil { setupShared.setupStakingProcess.value is SetupStakingProcess.SetupStep.Parachain }
        assertEquals(BigDecimal("3"), (setupShared.setupStakingProcess.value as SetupStakingProcess.SetupStep.Parachain).amount)
        verify { router.openSetupStaking() }
    }

    @Test
    fun poolWelcomeOpensPoolStaking() {
        setupShared.setupStakingProcess.value = SetupStakingProcess.Initial(StakingType.POOL)
        val state = factory.createPoolWelcomeViewState(flowOf(asset), scope) { }

        assertTrue(runBlocking { state.returns.first() }.yearly.gain.isNotEmpty())
        state.infoActionClicked()
        waitUntil { state.showRewardEstimationEvent.value != null }
        state.nextClicked()
        verify { router.openStakingPoolWelcome() }
    }

    @Test
    fun delegatorStateListsDelegationsWithCollatorStatus() {
        val paraAsset = StakingLaneFixtures.chainAsset(staking = ChainAsset.StakingType.PARACHAIN, symbol = "GLMR", precision = 18)
        val paraChain = StakingLaneFixtures.chain(paraAsset, ethereum = true)
        val collatorId = ByteArray(20) { 3 }
        val leavingId = ByteArray(20) { 4 }
        val delegations = listOf(
            CollatorDelegation(collatorId, StakingLaneFixtures.planks(5, 18), BigInteger.ONE, DelegatorStateStatus.ACTIVE),
            CollatorDelegation(leavingId, StakingLaneFixtures.planks(2, 18), BigInteger.ONE, DelegatorStateStatus.ACTIVE)
        )
        val delegator = StakingState.Parachain.Delegator(paraChain, collatorId, delegations, BigDecimal(7))
        val identity = mockk<Identity>(relaxed = true) { every { display } returns "Collator One" }
        fun candidate(status: CandidateInfoStatus) = CandidateInfo(
            BigInteger.TEN, BigInteger.TWO, BigInteger.TEN, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE,
            CandidateCapacity.Full, CandidateCapacity.Partial, null, status
        )
        val collatorHex = collatorId.joinToString("") { "%02x".format(it) }
        val leavingHex = leavingId.joinToString("") { "%02x".format(it) }
        every { interactor.selectedChainFlow() } returns flowOf(paraChain)
        coEvery { interactor.currentBlockNumber() } returns BigInteger.valueOf(150)
        coEvery { parachain.getIdentities(any()) } returns mapOf(collatorHex to identity)
        coEvery { parachain.getCandidateInfos(any(), any()) } returns mapOf(
            collatorHex to candidate(CandidateInfoStatus.ACTIVE),
            leavingHex to candidate(CandidateInfoStatus.LEAVING(10L))
        )
        coEvery { parachain.getCollatorIdsWithReadyToUnlockingTokens(any(), any()) } returns emptyList()
        coEvery { parachain.getCurrentRound(any()) } returns Result.success(Round(BigInteger.valueOf(5), BigInteger.valueOf(100), BigInteger.valueOf(100)))
        coEvery { parachain.getLeaveCandidatesDelay() } returns Result.success(2)
        every { parachain.hoursInRound } returns mapOf(paraChain.id to 6)
        coEvery { subqueryCalculator.getApy(any()) } returns mapOf(collatorHex to BigDecimal("0.05"))
        val paraAssetValue = StakingLaneFixtures.asset(paraAsset)

        val state = factory.createDelegatorViewState(delegator, flowOf(paraAssetValue), scope) { errors += it } as DelegatorViewState
        val models = runBlocking { state.delegations.first { it is LoadingState.Loaded } } as LoadingState.Loaded

        assertEquals(listOf("Collator One", leavingHex), models.data.map { it.collatorName })
        assertTrue(models.data[0].status is DelegatorViewState.CollatorDelegationModel.Status.Active)
        assertTrue(models.data[1].status is DelegatorViewState.CollatorDelegationModel.Status.Leaving)

        state.openCollatorInfo(models.data.first())
        coVerify { router.openCollatorDetails(any()) }
    }

    @Test
    fun candidateStatusMapsToDelegationStatus() {
        fun info(status: CandidateInfoStatus) = CandidateInfo(
            BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE,
            CandidateCapacity.Empty, CandidateCapacity.Empty, null, status
        )
        assertTrue(info(CandidateInfoStatus.ACTIVE).toModelStatus(5, null, true) is DelegatorViewState.CollatorDelegationModel.Status.ReadyToUnlock)
        assertEquals(5L, (info(CandidateInfoStatus.ACTIVE).toModelStatus(5, null, false) as DelegatorViewState.CollatorDelegationModel.Status.Active).nextRoundTimeLeft)
        assertTrue(info(CandidateInfoStatus.EMPTY).toModelStatus(5, null, false) is DelegatorViewState.CollatorDelegationModel.Status.Inactive)
        assertEquals(9L, (info(CandidateInfoStatus.LEAVING(1)).toModelStatus(5, 9, false) as DelegatorViewState.CollatorDelegationModel.Status.Leaving).collatorLeaveTimeLeft)
        assertTrue(info(CandidateInfoStatus.IDLE).toModelStatus(5, null, false) is DelegatorViewState.CollatorDelegationModel.Status.Idle)
        assertEquals("", ReturnsModel.default.monthly.amount)
        assertTrue(Pool is StakingViewStateOld)
        assertEquals(5, ManageStakeAction.entries.size)
    }
}
