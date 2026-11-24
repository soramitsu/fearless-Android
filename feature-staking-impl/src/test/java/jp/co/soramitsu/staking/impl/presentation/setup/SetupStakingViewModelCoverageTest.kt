package jp.co.soramitsu.staking.impl.presentation.setup

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.setup.SetupStakingInteractor
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.SelectBlockProducersStep
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationMixin
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationModel
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.core.models.Asset as ChainAsset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SetupStakingViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val relayAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(relayAsset)
    private val address = chain.addressOf(StakingLaneFixtures.accountId)

    private val router = mockk<StakingRouter>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val scenario = mockk<StakingScenarioInteractor>(relaxed = true)
    private val rewardFactory = mockk<RewardCalculatorFactory>(relaxed = true)
    private val calculator = mockk<RewardCalculator>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val setupInteractor = mockk<SetupStakingInteractor>(relaxed = true)
    private val shared = SetupStakingSharedState()
    private val validationExecutor = ValidationExecutor(resources)
    private val feeLoader = mockk<FeeLoaderMixin.Presentation>(relaxed = true)
    private val rewardMixin = mockk<RewardDestinationMixin.Presentation>(relaxed = true)
    private val iconGenerator = mockk<AddressIconGenerator>(relaxed = true)
    private val stakingShared = mockk<StakingSharedState>(relaxed = true)
    private val quickInputs = mockk<QuickInputsUseCase>(relaxed = true)
    private val selection = MutableStateFlow<StakingAssetSelection>(
        StakingAssetSelection.RelayChainStaking(relayAsset.chainId, relayAsset.id)
    )
    private val feeConstructor = slot<suspend (Token) -> BigInteger>()
    private val quickFee = AtomicReference<BigInteger?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers { "f${firstArg<Int>()}" }
        coEvery { interactor.getSelectedAccountProjection() } returns StakingAccount(address, "Me", false)
        coEvery { scenario.getMinimumStake(any()) } returns StakingLaneFixtures.planks(1)
        coEvery { scenario.getSetupStakingValidationSystem() } returns
            ValidationSystem(CompositeValidation<SetupStakingPayload, SetupStakingValidationFailure>(emptyList()))
        coEvery { rewardFactory.create(any<ChainAsset>()) } returns calculator
        every { stakingShared.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(chain, relayAsset))
        every { stakingShared.selectionItem } returns selection
        every { rewardMixin.rewardDestinationModelFlow } returns flowOf(RewardDestinationModel.Restake)
        coEvery { setupInteractor.estimateMaxSetupStakingFee(any()) } returns BigInteger.valueOf(5)
        coEvery { setupInteractor.estimateParachainFee(any()) } returns BigInteger.valueOf(7)
        coEvery { iconGenerator.createEthereumAddressIcon(any<ByteArray>(), any(), any()) } returns PictureDrawable(Picture())
        every { feeLoader.loadFee(any(), capture(feeConstructor), any(), any()) } returns Unit
        every { feeLoader.requireFee(any(), any()) } answers { firstArg<(BigDecimal) -> Unit>().invoke(BigDecimal("0.01")) }
        coEvery { quickInputs.calculateStakingQuickInputs(any(), any(), any(), any()) } coAnswers {
            quickFee.set(arg<suspend (BigInteger) -> BigInteger>(3).invoke(BigInteger.ONE))
            mapOf(0.5 to BigDecimal("40"))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not reached", condition())
    }

    private fun viewModel(
        state: SetupStakingProcess = SetupStakingProcess.SetupStep.Stash(BigDecimal("12")),
        asset: Asset = StakingLaneFixtures.asset(relayAsset)
    ): SetupStakingViewModel {
        every { interactor.currentAssetFlow() } returns flowOf(asset)
        shared.setupStakingProcess.value = state
        return SetupStakingViewModel(
            router, interactor, scenario, rewardFactory, resources, setupInteractor, shared, validationExecutor,
            feeLoader, rewardMixin, iconGenerator, stakingShared, quickInputs
        )
    }

    @Test
    fun setupStepPrefillsAmountAndUpdatesReturns() {
        val vm = viewModel()

        assertEquals("12", vm.enteredAmountFlow.value)
        val model = runBlocking { vm.assetModelsFlow.first() }
        assertEquals(relayAsset.chainId, model.chainId)
        assertEquals(ChainAsset.StakingType.RELAYCHAIN, runBlocking { vm.currentStakingType.first() })
        coVerify(timeout = 5_000) { rewardMixin.updateReturns(calculator, any(), BigDecimal("12")) }
    }

    @Test
    fun enteredAmountIsConvertedToFiat() {
        val chainAsset = relayAsset
        val withFiat = StakingLaneFixtures.asset(chainAsset).copy(token = StakingLaneFixtures.token(chainAsset, BigDecimal("2")))
        val vm = viewModel(asset = withFiat)
        vm.enteredFiatAmountFlow.observeForever { }

        waitUntil { vm.enteredFiatAmountFlow.value != null }
        assertTrue(vm.enteredFiatAmountFlow.value!!, vm.enteredFiatAmountFlow.value!!.contains("24"))
    }

    @Test
    fun nextWithEnoughAmountOpensValidatorSelection() {
        val vm = viewModel()

        vm.nextClicked()

        waitUntil { shared.setupStakingProcess.value is SelectBlockProducersStep.Validators }
        val next = shared.setupStakingProcess.value as SelectBlockProducersStep.Validators
        val payload = next.payload as SelectBlockProducersStep.Payload.Relaychain
        assertEquals(BigDecimal("12"), payload.amount)
        assertEquals(address, payload.controllerAddress)
        assertEquals(RewardDestination.Restake, payload.rewardDestination)
        coVerify(timeout = 5_000) { router.openStartChangeValidators() }
        assertEquals(false, vm.showNextProgress.value)
    }

    @Test
    fun amountBelowMinimumShowsAlertAndConfirmationContinues() {
        coEvery { scenario.getMinimumStake(any()) } returns StakingLaneFixtures.planks(20)
        val vm = viewModel()

        vm.nextClicked()

        waitUntil { vm.showMinimumStakeAlert.value != null }
        val alert = vm.showMinimumStakeAlert.value!!.peekContent()
        assertTrue(alert, alert.contains("20") && alert.contains("DOT"))
        assertTrue(shared.setupStakingProcess.value is SetupStakingProcess.SetupStep.Stash)

        vm.minimumStakeConfirmed()

        waitUntil { shared.setupStakingProcess.value is SelectBlockProducersStep.Validators }
        coVerify(timeout = 5_000) { router.openStartChangeValidators() }
    }

    @Test
    fun parachainAmountOpensCollatorSelection() {
        val paraAsset = StakingLaneFixtures.chainAsset(staking = ChainAsset.StakingType.PARACHAIN)
        val vm = viewModel(SetupStakingProcess.SetupStep.Parachain(BigDecimal("12")), StakingLaneFixtures.asset(paraAsset))

        vm.nextClicked()

        waitUntil { shared.setupStakingProcess.value is SelectBlockProducersStep.Collators }
        val next = shared.setupStakingProcess.value as SelectBlockProducersStep.Collators
        assertEquals(BigDecimal("12"), next.payload.amount)
        assertEquals(address, next.payload.accountAddress)
        coVerify(timeout = 5_000) { router.openStartChangeCollators() }
    }

    @Test
    fun minimumStakeFailureOffersRetry() {
        coEvery { scenario.getMinimumStake(any()) } throws IllegalStateException("min failed") andThen StakingLaneFixtures.planks(1)
        val vm = viewModel()

        val dialog = vm.errorDialogStateLiveData.value!!.peekContent()
        assertEquals("s${CommonR.string.common_error_general_title}", dialog.title)
        assertEquals("min failed", dialog.message)
        assertEquals("s${CommonR.string.common_retry}", dialog.positiveButtonText)

        dialog.positiveClick()

        coVerify(exactly = 2) { scenario.getMinimumStake(relayAsset) }
    }

    @Test
    fun backReturnsToPreviousStepOrSelectedType() {
        viewModel().backClicked()

        assertEquals(StakingType.RELAYCHAIN, (shared.setupStakingProcess.value as SetupStakingProcess.Initial).stakingType)
        coVerify { router.back() }

        selection.value = StakingAssetSelection.ParachainStaking(relayAsset.chainId, relayAsset.id)
        viewModel(SetupStakingProcess.Initial(StakingType.RELAYCHAIN)).backClicked()

        assertEquals(StakingType.PARACHAIN, (shared.setupStakingProcess.value as SetupStakingProcess.Initial).stakingType)
    }

    @Test
    fun feeDependsOnSelectedStakingType() {
        viewModel()
        val token = StakingLaneFixtures.token(relayAsset)

        assertEquals(BigInteger.valueOf(5), runBlocking { feeConstructor.captured(token) })
        selection.value = StakingAssetSelection.ParachainStaking(relayAsset.chainId, relayAsset.id)
        assertEquals(BigInteger.valueOf(7), runBlocking { feeConstructor.captured(token) })
        selection.value = StakingAssetSelection.Pool(relayAsset.chainId, relayAsset.id)
        assertEquals(BigInteger.ZERO, runBlocking { feeConstructor.captured(token) })
    }

    @Test
    fun quickInputFillsAmountFromCalculatedValues() {
        val vm = viewModel()

        vm.onQuickAmountInput(0.5)

        waitUntil { vm.enteredAmountFlow.value == "40" }
        assertEquals(BigInteger.valueOf(5), quickFee.get())
        coVerify { quickInputs.calculateStakingQuickInputs(relayAsset.chainId, relayAsset.id, any(), any()) }
    }

    @Test
    fun focusAndAccountModelAreExposed() {
        coEvery { interactor.getSelectedAccountProjection() } returns StakingAccount("0x" + "ab".repeat(20), "Eth", true)
        val vm = viewModel()
        vm.currentAccountAddressModel.observeForever { }

        vm.onAmountInputFocusChanged(true)

        assertEquals(true, vm.isInputFocused.value)
        waitUntil { vm.currentAccountAddressModel.value != null }
        assertEquals("Eth", vm.currentAccountAddressModel.value?.name)
    }
}
