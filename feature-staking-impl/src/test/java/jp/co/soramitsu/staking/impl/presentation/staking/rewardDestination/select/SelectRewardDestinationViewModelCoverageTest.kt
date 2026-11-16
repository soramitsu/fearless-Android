package jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.select

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.staking.api.domain.model.Nominations
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.staking.rewardDestination.ChangeRewardDestinationInteractor
import jp.co.soramitsu.staking.impl.domain.validations.rewardDestination.RewardDestinationValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.rewardDestination.RewardDestinationValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationMixin
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationModel
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.RewardDestinationParcelModel
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SelectRewardDestinationViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<SelectRewardDestinationViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val target = ByteArray(32) { 5 }
    private val stash = StakingState.Stash.Nominator(
        chain,
        StakingLaneFixtures.accountId,
        ByteArray(32) { 8 },
        ByteArray(32) { 9 },
        Nominations(listOf(target), BigInteger.ONE, false)
    )
    private val payoutAddress = chain.addressOf(ByteArray(32) { 4 })
    private val destination = MutableStateFlow<RewardDestinationModel>(RewardDestinationModel.Restake)

    private val router = mockk<StakingRouter>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val relay = mockk<StakingRelayChainScenarioInteractor>(relaxed = true)
    private val calculators = mockk<RewardCalculatorFactory>(relaxed = true)
    private val calculator = mockk<RewardCalculator>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val change = mockk<ChangeRewardDestinationInteractor>(relaxed = true)
    private val fees = mockk<FeeLoaderMixin.Presentation>(relaxed = true)
    private val mixin = mockk<RewardDestinationMixin.Presentation>(relaxed = true)

    private val feeConstructor = slot<suspend (Token) -> BigInteger>()
    private val retryCancelled = slot<() -> Unit>()
    private val validated = CopyOnWriteArrayList<RewardDestinationValidationPayload>()

    @Volatile
    private var status: ValidationStatus<RewardDestinationValidationFailure> = ValidationStatus.Valid()

    private val validationSystem = ValidationSystem(
        object : Validation<RewardDestinationValidationPayload, RewardDestinationValidationFailure> {
            override suspend fun validate(
                value: RewardDestinationValidationPayload
            ): ValidationStatus<RewardDestinationValidationFailure> {
                validated += value
                return status
            }
        }
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers {
            "f${firstArg<Int>()}:${secondArg<Array<Any>>().joinToString()}"
        }
        every { interactor.currentAssetFlow() } returns flowOf(asset)
        every { relay.selectedAccountStakingStateFlow() } returns flowOf(stash)
        every { mixin.rewardDestinationModelFlow } returns destination
        every { mixin.rewardDestinationChangedFlow } returns flowOf(true)
        coEvery { mixin.loadActiveRewardDestination(any()) } returns Unit
        coEvery { mixin.updateReturns(any(), any(), any()) } returns Unit
        coEvery { calculators.createWithValidators(any(), any()) } returns calculator
        coEvery { change.estimateFee(any(), any()) } returns BigInteger.TEN
        every { fees.loadFee(any(), capture(feeConstructor), capture(retryCancelled), any()) } returns Unit
        every { fees.requireFee(any(), any()) } answers { firstArg<(BigDecimal) -> Unit>().invoke(BigDecimal.ONE) }
    }

    @After
    fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not reached", condition())
    }

    private fun viewModel() = SelectRewardDestinationViewModel(
        router = router,
        interactor = interactor,
        stakingRelayChainScenarioInteractor = relay,
        rewardCalculatorFactory = calculators,
        resourceManager = resources,
        changeRewardDestinationInteractor = change,
        validationSystem = validationSystem,
        validationExecutor = ValidationExecutor(resources),
        feeLoaderMixin = fees,
        rewardDestinationMixin = mixin
    ).also(owned::add)

    @Test
    fun feeIsEstimatedForTheChosenDestination() {
        viewModel()

        val fee = runBlocking { feeConstructor.captured.invoke(asset.token) }

        assertEquals(BigInteger.TEN, fee)
        coVerify { change.estimateFee(stash, RewardDestination.Restake) }
    }

    @Test
    fun returnsAreCalculatedWithTheNominatedValidators() {
        viewModel()

        coVerify(timeout = 5_000) { mixin.updateReturns(calculator, asset, any()) }
        coVerify { calculators.createWithValidators(chainAsset, listOf(target)) }
        coVerify { mixin.loadActiveRewardDestination(stash) }
        verify { mixin.invalidateReturns() }
    }

    @Test
    fun nextWithRestakeOpensTheConfirmation() {
        val payload = slot<ConfirmRewardDestinationPayload>()
        every { router.openConfirmRewardDestination(capture(payload)) } returns Unit
        val vm = viewModel()

        vm.nextClicked()

        verify(timeout = 5_000) { router.openConfirmRewardDestination(any()) }
        assertSame(RewardDestinationParcelModel.Restake, payload.captured.rewardDestination)
        assertEquals(0, BigDecimal.ONE.compareTo(payload.captured.fee))
        val checked = validated.single()
        assertSame(stash, checked.stashState)
        assertEquals(asset.availableForStaking, checked.availableControllerBalance)
        waitUntil { vm.showNextProgress.value == false }
    }

    @Test
    fun nextWithPayoutCarriesTheTargetAddress() {
        destination.value = RewardDestinationModel.Payout(AddressModel(payoutAddress, PictureDrawable(Picture())))
        val payload = slot<ConfirmRewardDestinationPayload>()
        every { router.openConfirmRewardDestination(capture(payload)) } returns Unit
        val vm = viewModel()

        vm.nextClicked()

        verify(timeout = 5_000) { router.openConfirmRewardDestination(any()) }
        val parcel = payload.captured.rewardDestination as RewardDestinationParcelModel.Payout
        assertEquals(payoutAddress, parcel.targetAccountAddress)
    }

    @Test
    fun aMissingControllerBlocksTheChange() {
        status = ValidationStatus.NotValid(DefaultFailureLevel.ERROR, RewardDestinationValidationFailure.MissingController("ctrl"))
        val vm = viewModel()

        vm.nextClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${R.string.common_error_general_title}", failure.title)
        assertEquals("f${R.string.staking_add_controller}:ctrl", failure.message)
        waitUntil { vm.showNextProgress.value == false }
        verify(exactly = 0) { router.openConfirmRewardDestination(any()) }
    }

    @Test
    fun feesThatCannotBePaidBlockTheChange() {
        status = ValidationStatus.NotValid(DefaultFailureLevel.ERROR, RewardDestinationValidationFailure.CannotPayFees)
        val vm = viewModel()

        vm.nextClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${R.string.common_not_enough_funds_title}", failure.title)
        assertEquals("s${R.string.common_not_enough_funds_message}", failure.message)
    }

    @Test
    fun missingFeeShowsTheFeeError() {
        every { fees.requireFee(any(), any()) } answers { secondArg<(String, String) -> Unit>().invoke("Fee", "pending") }
        val vm = viewModel()

        vm.nextClicked()

        assertEquals("Fee" to "pending", vm.errorWithTitleLiveData.value?.peekContent())
        assertTrue(validated.isEmpty())
    }

    @Test
    fun backAndRetryCancellationGoBack() {
        val vm = viewModel()

        vm.backClicked()
        retryCancelled.captured.invoke()

        verify(exactly = 2) { router.back() }
    }
}
