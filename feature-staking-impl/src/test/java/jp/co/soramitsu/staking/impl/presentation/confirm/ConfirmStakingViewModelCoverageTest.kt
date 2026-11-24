package jp.co.soramitsu.staking.impl.presentation.confirm

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicBoolean
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.staking.api.domain.model.CandidateCapacity
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.CollatorDelegation
import jp.co.soramitsu.staking.api.domain.model.DelegatorStateStatus
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.setup.SetupStakingInteractor
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit.SelectionMethod
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationModel
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConfirmStakingViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val id = StakingLaneFixtures.accountId
    private val controllerId = ByteArray(32) { 3 }
    private val payoutId = ByteArray(32) { 9 }
    private val address = chain.addressOf(id)
    private val amount = BigDecimal("12")

    private val router = mockk<StakingRouter>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val scenario = mockk<StakingScenarioInteractor>(relaxed = true)
    private val addressDisplay = mockk<AddressDisplayUseCase>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val shared = SetupStakingSharedState()
    private val setupInteractor = mockk<SetupStakingInteractor>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>(relaxed = true)
    private val feeLoader = mockk<FeeLoaderMixin.Presentation>(relaxed = true)
    private val externalActions = mockk<ExternalAccountActions.Presentation>(relaxed = true)
    private val validationExecutor = ValidationExecutor(resources)
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val feeConstructor = slot<suspend (Token) -> BigInteger>()
    private val returnedToMain = AtomicBoolean(false)

    private val validator = Validator(
        address = chain.addressOf(ByteArray(32) { 5 }),
        slashed = false,
        accountIdHex = "05".repeat(32),
        prefs = null,
        electedInfo = null,
        identity = null
    )

    private val collator = Collator(
        address = "0x" + "11".repeat(20),
        bond = BigInteger.ONE,
        delegationCount = BigInteger.TWO,
        totalCounted = BigInteger.TEN,
        lowestTopDelegationAmount = BigInteger.ONE,
        highestBottomDelegationAmount = BigInteger.ONE,
        lowestBottomDelegationAmount = BigInteger.ONE,
        topCapacity = CandidateCapacity.Partial,
        bottomCapacity = CandidateCapacity.Empty,
        request = null,
        status = CandidateInfoStatus.ACTIVE,
        identity = RootIdentity("Collator One", null, null, null, null, null, null, null),
        apy = BigDecimal("0.1")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { router.returnToMain() } answers { returnedToMain.set(true) }
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers { "f${firstArg<Int>()}" }
        every { resources.getQuantityString(any(), any(), *anyVararg()) } answers { "q${secondArg<Int>()}" }
        every { interactor.currentAssetFlow() } returns flowOf(asset)
        every { interactor.selectedChainFlow() } returns flowOf(chain)
        coEvery { interactor.getSelectedAccountProjection() } returns StakingAccount(address, "Me", false)
        coEvery { interactor.getAddressModel(any(), any(), any()) } answers {
            AddressModel(firstArg(), PictureDrawable(Picture()), thirdArg())
        }
        coEvery { addressDisplay.invoke(any()) } returns "Named"
        every { scenario.stakingStateFlow() } returns flowOf(StakingState.Stash.None(chain, id, controllerId, id))
        coEvery { scenario.maxStakersPerBlockProducer() } returns 16
        coEvery { scenario.unstakingPeriod() } returns 672
        coEvery { scenario.stakePeriodInHours() } returns 6
        coEvery { scenario.getSetupStakingValidationSystem() } returns passingSystem()
        coEvery { chainRegistry.getChain(any()) } returns chain
        every { feeLoader.loadFee(any(), capture(feeConstructor), any(), any()) } returns Unit
        every { feeLoader.requireFee(any(), any()) } answers { firstArg<(BigDecimal) -> Unit>().invoke(BigDecimal("0.01")) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun passingSystem() =
        ValidationSystem(CompositeValidation<SetupStakingPayload, SetupStakingValidationFailure>(emptyList()))

    private fun failingSystem(reason: SetupStakingValidationFailure) = ValidationSystem(
        object : Validation<SetupStakingPayload, SetupStakingValidationFailure> {
            override suspend fun validate(value: SetupStakingPayload): ValidationStatus<SetupStakingValidationFailure> =
                ValidationStatus.NotValid(DefaultFailureLevel.ERROR, reason)
        }
    )

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not reached", condition())
    }

    private fun fullStash(destination: RewardDestination = RewardDestination.Restake) = ReadyToSubmit.Stash(
        ReadyToSubmit.Payload.Full(amount, destination, address, listOf(validator), SelectionMethod.RECOMMENDED)
    )

    private fun parachain() = ReadyToSubmit.Parachain(
        ReadyToSubmit.Payload.Parachain(amount, address, listOf(collator), SelectionMethod.RECOMMENDED)
    )

    private fun viewModel(state: SetupStakingProcess): ConfirmStakingViewModel {
        shared.setupStakingProcess.value = state
        return ConfirmStakingViewModel(
            router, interactor, scenario, addressDisplay, resources, shared, setupInteractor, chainRegistry,
            feeLoader, externalActions, validationExecutor, clipboard
        )
    }

    @Test
    fun fullFlowShowsAmountAccountAndRestakeDestination() {
        val vm = viewModel(fullStash())
        vm.displayAmountLiveData.observeForever { }
        vm.currentAccountModelLiveData.observeForever { }
        vm.rewardDestinationLiveData.observeForever { }
        vm.assetModelLiveData.observeForever { }

        assertEquals(amount, vm.displayAmountLiveData.value)
        assertEquals(address, vm.currentAccountModelLiveData.value?.address)
        assertEquals("Named", vm.currentAccountModelLiveData.value?.name)
        waitUntil { vm.rewardDestinationLiveData.value != null && vm.assetModelLiveData.value != null }
        assertEquals(RewardDestinationModel.Restake, vm.rewardDestinationLiveData.value)
        assertEquals(chainAsset.chainId, vm.assetModelLiveData.value?.chainId)
    }

    @Test
    fun nominationsAndHintsUseScenarioPeriods() {
        every { resources.getString(R.string.staking_confirm_nominations, 1, 16) } returns "1 of 16"
        every { resources.getQuantityString(R.plurals.common_days_format, 28, 28) } returns "28 days"
        every { resources.getString(R.string.staking_hint_unstake_format, "28 days") } returns "unstake 28 days"
        every { resources.getQuantityString(R.plurals.common_hours_format, 6, 6) } returns "6 hours"
        every { resources.getString(R.string.staking_hint_rewards_format, "6 hours") } returns "every 6 hours"
        val vm = viewModel(fullStash())
        vm.nominationsLiveData.observeForever { }

        waitUntil {
            vm.nominationsLiveData.value != null && vm.unstakingTime.replayCache.isNotEmpty() &&
                vm.eraHoursLength.replayCache.isNotEmpty()
        }
        assertEquals("1 of 16", vm.nominationsLiveData.value)
        assertEquals("unstake 28 days", vm.unstakingTime.replayCache.first())
        assertEquals("every 6 hours", vm.eraHoursLength.replayCache.first())
    }

    @Test
    fun shortUnstakingPeriodIsFormattedInHours() {
        coEvery { scenario.unstakingPeriod() } returns 12
        every { resources.getQuantityString(R.plurals.common_hours_format, 12, 12) } returns "12 hours"
        every { resources.getString(R.string.staking_hint_unstake_format, "12 hours") } returns "unstake 12 hours"
        val vm = viewModel(fullStash())

        waitUntil { vm.unstakingTime.replayCache.isNotEmpty() }
        assertEquals("unstake 12 hours", vm.unstakingTime.replayCache.first())
    }

    @Test
    fun payoutDestinationIsResolvedAndOpensExplorerActions() {
        val vm = viewModel(fullStash(RewardDestination.Payout(payoutId)))
        vm.rewardDestinationLiveData.observeForever { }

        waitUntil { vm.rewardDestinationLiveData.value != null }
        val payout = vm.rewardDestinationLiveData.value as RewardDestinationModel.Payout
        assertEquals(chain.addressOf(payoutId), payout.destination.address)
        assertEquals("Named", payout.destination.name)

        val payload = slot<ExternalAccountActions.Payload>()
        every { externalActions.showExternalActions(capture(payload)) } returns Unit
        vm.payoutAccountClicked()
        waitUntil { payload.isCaptured }
        assertEquals(chain.addressOf(payoutId), payload.captured.value)
    }

    @Test
    fun originAccountClickOpensExternalActionsForSelectedAccount() {
        val payload = slot<ExternalAccountActions.Payload>()
        every { externalActions.showExternalActions(capture(payload)) } returns Unit
        val vm = viewModel(fullStash())

        vm.originAccountClicked()

        waitUntil { payload.isCaptured }
        assertEquals(address, payload.captured.value)
        assertTrue(payload.captured.explorers.isEmpty())
    }

    @Test
    fun backAndNominationsNavigate() {
        val vm = viewModel(fullStash())

        vm.nominationsClicked()
        vm.backClicked()

        verify { router.openConfirmNominations() }
        verify { router.back() }
        val previous = shared.setupStakingProcess.value as SetupStakingProcess.SelectBlockProducersStep.Validators
        assertTrue(previous.payload is SetupStakingProcess.SelectBlockProducersStep.Payload.Full)
    }

    @Test
    fun stashFeeIsCalculatedForController() {
        coEvery { setupInteractor.calculateSetupStakingFee(any(), any(), any()) } returns BigInteger.TEN
        viewModel(fullStash())

        val fee = runBlocking { feeConstructor.captured(StakingLaneFixtures.token(chainAsset)) }

        assertEquals(BigInteger.TEN, fee)
        coVerify { setupInteractor.calculateSetupStakingFee(address, listOf(validator.accountIdHex), any()) }
    }

    @Test
    fun confirmSubmitsStakingAndReturnsToMain() {
        coEvery { setupInteractor.setupStaking(any<String>(), any(), any()) } returns Result.success(Result.success("0xhash"))
        val vm = viewModel(fullStash())

        vm.confirmClicked()

        waitUntil { returnedToMain.get() }
        assertTrue(shared.setupStakingProcess.value is SetupStakingProcess.Initial)
        assertEquals(false, vm.showNextProgress.value)
        assertEquals("s${R.string.common_transaction_submitted}", vm.messageLiveData.value?.peekContent())
        coVerify { setupInteractor.setupStaking(address, listOf(validator.accountIdHex), any()) }
    }

    @Test
    fun failedSubmissionShowsError() {
        coEvery { setupInteractor.setupStaking(any<String>(), any(), any()) } returns Result.failure(IllegalStateException("submit failed"))
        val vm = viewModel(fullStash())

        vm.confirmClicked()

        waitUntil { vm.errorLiveData.value != null }
        assertEquals("submit failed", vm.errorLiveData.value?.peekContent())
        assertTrue(!returnedToMain.get())
    }

    @Test
    fun invalidPayloadRaisesValidationFailure() {
        coEvery { scenario.getSetupStakingValidationSystem() } returns failingSystem(SetupStakingValidationFailure.CannotPayFee)
        val vm = viewModel(fullStash())

        vm.confirmClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = vm.validationFailureEvent.value!!.peekContent()
        assertEquals("s${R.string.common_not_enough_funds_title}", failure.title)
        assertEquals(false, vm.showNextProgress.value)
        coVerify(exactly = 0) { setupInteractor.setupStaking(any<String>(), any(), any()) }
    }

    @Test
    fun relayChainPayloadHasNoDisplayedAmountOrDestination() {
        val vm = viewModel(
            ReadyToSubmit.Stash(
                ReadyToSubmit.Payload.RelayChain(amount, RewardDestination.Restake, address, listOf(validator), SelectionMethod.CUSTOM)
            )
        )
        vm.displayAmountLiveData.observeForever { }
        vm.rewardDestinationLiveData.observeForever { }
        vm.selectedCollatorLiveData.observeForever { }

        waitUntil { vm.currentAccountModelLiveData.value != null }
        assertNull(vm.displayAmountLiveData.value)
        assertNull(vm.rewardDestinationLiveData.value)
        assertNull(vm.selectedCollatorLiveData.value)
        assertEquals(address, vm.currentAccountModelLiveData.value?.address)
    }

    @Test
    fun existingStashUsesBondedAmountAndStashController() {
        val vm = viewModel(ReadyToSubmit.Stash(ReadyToSubmit.Payload.ExistingStash(listOf(validator), SelectionMethod.CUSTOM)))
        vm.displayAmountLiveData.observeForever { }
        vm.currentAccountModelLiveData.observeForever { }

        assertEquals(asset.bonded, vm.displayAmountLiveData.value)
        assertEquals(chain.addressOf(controllerId), vm.currentAccountModelLiveData.value?.address)
    }

    @Test
    fun parachainShowsCollatorAndCopiesItsAddress() {
        val vm = viewModel(parachain())
        vm.selectedCollatorLiveData.observeForever { }

        waitUntil { vm.selectedCollatorLiveData.value != null }
        assertEquals(collator.address, vm.selectedCollatorLiveData.value?.address)
        assertEquals("Collator One", vm.selectedCollatorLiveData.value?.name)

        vm.onSelectedCollatorCopyClicked()

        verify { clipboard.addToClipboard(collator.address, any()) }
        assertEquals("s${R.string.common_copied}", vm.messageLiveData.value?.peekContent())
    }

    @Test
    fun parachainFeeCountsExistingDelegations() {
        val delegation = CollatorDelegation(ByteArray(20) { 2 }, BigInteger.ONE, BigInteger.ZERO, DelegatorStateStatus.ACTIVE)
        every { scenario.stakingStateFlow() } returns flowOf(StakingState.Parachain.Delegator(chain, id, listOf(delegation), BigDecimal.ONE))
        coEvery { setupInteractor.estimateFinalParachainFee(any(), any(), any(), any()) } returns BigInteger.ONE
        viewModel(parachain())

        val fee = runBlocking { feeConstructor.captured(StakingLaneFixtures.token(chainAsset)) }

        assertEquals(BigInteger.ONE, fee)
        coVerify { setupInteractor.estimateFinalParachainFee(address, collator, StakingLaneFixtures.planks(12), 1) }
    }

    @Test
    fun parachainConfirmDelegatesToCollator() {
        every { scenario.stakingStateFlow() } returns flowOf(StakingState.Parachain.None(chain, id))
        coEvery { setupInteractor.setupStaking(any<Collator>(), any(), any(), any()) } returns Result.success(Result.success("0xhash"))
        val vm = viewModel(parachain())

        vm.confirmClicked()

        waitUntil { returnedToMain.get() }
        coVerify { setupInteractor.setupStaking(collator, StakingLaneFixtures.planks(12), 0, address) }
        assertTrue(shared.setupStakingProcess.value is SetupStakingProcess.Initial)
        assertNull(vm.rewardDestinationLiveData.value)
    }
}
