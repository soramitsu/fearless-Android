package jp.co.soramitsu.staking.impl.presentation.payouts.list

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigInteger
import jp.co.soramitsu.common.base.errors.TitledException
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.model.PendingPayout
import jp.co.soramitsu.staking.impl.domain.model.PendingPayoutsStatistics
import jp.co.soramitsu.staking.impl.domain.rewards.SoraStakingRewardsScenario
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.staking.impl.presentation.payouts.list.model.PendingPayoutsStatisticsModel
import jp.co.soramitsu.staking.impl.presentation.payouts.model.PendingPayoutParcelable
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatSigned
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PayoutsListViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<PayoutsListViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val asset = StakingLaneFixtures.asset(chainAsset)

    private val router = mockk<StakingRouter>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val relay = mockk<StakingRelayChainScenarioInteractor>(relaxed = true)
    private val sora = mockk<SoraStakingRewardsScenario>(relaxed = true)

    private val expiring = PendingPayout(
        validatorInfo = PendingPayout.ValidatorInfo("validator-a", "Validator A"),
        era = BigInteger.valueOf(100),
        amountInPlanks = StakingLaneFixtures.planks(2),
        timeLeft = 3_600_000L,
        createdAt = 1_000L,
        closeToExpire = true,
        page = BigInteger.ONE
    )
    private val regular = PendingPayout(
        validatorInfo = PendingPayout.ValidatorInfo("validator-b", null),
        era = BigInteger.valueOf(101),
        amountInPlanks = StakingLaneFixtures.planks(1),
        timeLeft = 7_200_000L,
        createdAt = 2_000L,
        closeToExpire = false
    )
    private val statistics = PendingPayoutsStatistics(listOf(expiring, regular), StakingLaneFixtures.planks(3))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers {
            "s${firstArg<Int>()}:${secondArg<Array<Any>>().joinToString()}"
        }
        every { interactor.currentAssetFlow() } returns flowOf(asset)
        coEvery { relay.calculatePendingPayouts() } returns Result.success(statistics)
    }

    @After
    fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }

    private fun viewModel() = PayoutsListViewModel(router, resources, interactor, relay, sora).also(owned::add)

    private fun PayoutsListViewModel.loaded(): PendingPayoutsStatisticsModel = runBlocking {
        withTimeout(5_000) {
            val state = payoutsStatisticsState.first { it is LoadingState.Loaded }
            (state as LoadingState.Loaded).data
        }
    }

    private fun total(token: Token, planks: BigInteger) =
        token.amountFromPlanks(planks).formatCryptoDetail(token.configuration.symbol)

    @Test
    fun pendingPayoutsAreMappedForTheList() {
        val model = viewModel().loaded()

        assertEquals("s${R.string.staking_reward_payouts_payout_all}:${total(asset.token, statistics.totalAmountInPlanks)}", model.payoutAllTitle)
        assertFalse(model.placeholderVisible)
        val (first, second) = model.payouts
        assertEquals("Validator A", first.validatorTitle)
        assertEquals(R.color.error_red, first.daysLeftColor)
        assertEquals(3_600_000L, first.timeLeft)
        assertEquals(1_000L, first.createdAt)
        assertEquals(total(asset.token, expiring.amountInPlanks).formatSigned(true), first.amount)
        assertNull(first.amountFiat)
        assertEquals("validator-b", second.validatorTitle)
        assertEquals(R.color.white_64, second.daysLeftColor)
        assertTrue(second.amount.startsWith("+"))
    }

    @Test
    fun emptyPayoutsShowThePlaceholder() {
        coEvery { relay.calculatePendingPayouts() } returns Result.success(PendingPayoutsStatistics(emptyList(), BigInteger.ZERO))

        val model = viewModel().loaded()

        assertTrue(model.placeholderVisible)
        assertTrue(model.payouts.isEmpty())
    }

    @Test
    fun soraPayoutsAreShownInTheRewardToken() {
        val xor = StakingLaneFixtures.chainAsset(chainId = soraMainChainId, symbol = "XOR", precision = 18)
        val valToken = StakingLaneFixtures.token(StakingLaneFixtures.chainAsset(chainId = soraMainChainId, symbol = "VAL", precision = 18))
        every { interactor.currentAssetFlow() } returns flowOf(StakingLaneFixtures.asset(xor))
        coEvery { sora.getRewardAsset() } returns valToken
        val totalPlanks = StakingLaneFixtures.planks(3, precision = 18)
        coEvery { relay.calculatePendingPayouts() } returns Result.success(PendingPayoutsStatistics(emptyList(), totalPlanks))

        val model = viewModel().loaded()

        assertEquals("s${R.string.staking_reward_payouts_payout_all}:${total(valToken, totalPlanks)}", model.payoutAllTitle)
        coVerify { sora.getRewardAsset() }
    }

    @Test
    fun payoutAllOpensTheConfirmationWithEveryPayout() {
        val vm = viewModel()
        val payload = slot<ConfirmPayoutPayload>()
        every { router.openConfirmPayout(capture(payload)) } returns Unit

        vm.payoutAllClicked()

        verify(timeout = 5_000) { router.openConfirmPayout(any()) }
        assertEquals(statistics.totalAmountInPlanks, payload.captured.totalRewardInPlanks)
        val (first, second) = payload.captured.payouts
        assertEquals("validator-a", first.validatorInfo.address)
        assertEquals("Validator A", first.validatorInfo.identityName)
        assertEquals(BigInteger.valueOf(100), first.era)
        assertEquals(BigInteger.ONE, first.page)
        assertTrue(first.closeToExpire)
        assertNull(second.page)
        assertNull(second.validatorInfo.identityName)
    }

    @Test
    fun payoutClickOpensItsDetails() {
        val vm = viewModel()
        val payload = slot<PendingPayoutParcelable>()
        every { router.openPayoutDetails(capture(payload)) } returns Unit

        vm.payoutClicked(1)

        verify(timeout = 5_000) { router.openPayoutDetails(any()) }
        assertEquals("validator-b", payload.captured.validatorInfo.address)
        assertEquals(StakingLaneFixtures.planks(1), payload.captured.amountInPlanks)
        assertEquals(7_200_000L, payload.captured.timeLeft)
        assertEquals(2_000L, payload.captured.createdAt)
        assertEquals(BigInteger.valueOf(101), payload.captured.era)
        assertFalse(payload.captured.closeToExpire)
    }

    @Test
    fun titledFailureOffersRetryThatReloads() {
        coEvery { relay.calculatePendingPayouts() } returns Result.failure(TitledException("Payouts", "indexer down"))
        val vm = viewModel()

        val retry = requireNotNull(vm.retryEvent.value).peekContent()
        assertEquals("Payouts", retry.title)
        assertEquals("indexer down", retry.message)

        coEvery { relay.calculatePendingPayouts() } returns Result.success(statistics)
        requireNotNull(retry.onRetry).invoke()

        coVerify(exactly = 2) { relay.calculatePendingPayouts() }
        assertEquals(2, vm.loaded().payouts.size)
    }

    @Test
    fun untitledFailureUsesGeneralTextsAndCancelGoesBack() {
        coEvery { relay.calculatePendingPayouts() } returns Result.failure(IllegalStateException())
        val vm = viewModel()

        val retry = requireNotNull(vm.retryEvent.value).peekContent()
        assertEquals("s${R.string.common_error_general_title}", retry.title)
        assertEquals("s${R.string.common_undefined_error_message}", retry.message)

        requireNotNull(retry.onCancel).invoke()
        vm.backClicked()
        verify(exactly = 2) { router.back() }
    }
}
