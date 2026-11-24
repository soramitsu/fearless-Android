package jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.rewardDestination.ChangeRewardDestinationInteractor
import jp.co.soramitsu.staking.impl.domain.validations.rewardDestination.RewardDestinationValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.rewardDestination.RewardDestinationValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationModel
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.RewardDestinationParcelModel
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
class ConfirmRewardDestinationViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<ConfirmRewardDestinationViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val stash = StakingState.Stash.None(chain, StakingLaneFixtures.accountId, ByteArray(32) { 8 }, ByteArray(32) { 9 })
    private val payoutId = ByteArray(32) { 5 }
    private val payoutAddress = chain.addressOf(payoutId)
    private val fee = BigDecimal("0.1")

    private val router = mockk<StakingRouter>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val relay = mockk<StakingRelayChainScenarioInteractor>(relaxed = true)
    private val icons = mockk<AddressIconGenerator>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val changer = mockk<ChangeRewardDestinationInteractor>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>(relaxed = true)
    private val external = mockk<ExternalAccountActions.Presentation>(relaxed = true)
    private val display = mockk<AddressDisplayUseCase>(relaxed = true)
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
            "s${firstArg<Int>()}:${secondArg<Array<Any>>().joinToString()}"
        }
        every { interactor.currentAssetFlow() } returns flowOf(asset)
        coEvery { interactor.getProjectedAccount(any()) } answers { StakingAccount(firstArg(), "Controller", false) }
        every { relay.selectedAccountStakingStateFlow() } returns flowOf(stash)
        coEvery { icons.createAddressIcon(any<ByteArray>(), any(), any()) } returns PictureDrawable(Picture())
        coEvery { display.invoke(any()) } returns "Payout wallet"
        coEvery { chainRegistry.getChain(any()) } returns chain
        coEvery { changer.changeRewardDestination(any(), any()) } returns Result.success("0xhash")
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

    private fun viewModel(destination: RewardDestinationParcelModel) = ConfirmRewardDestinationViewModel(
        router = router,
        interactor = interactor,
        stakingRelayChainScenarioInteractor = relay,
        addressIconGenerator = icons,
        resourceManager = resources,
        validationSystem = validationSystem,
        rewardDestinationInteractor = changer,
        chainRegistry = chainRegistry,
        externalAccountActions = external,
        addressDisplayUseCase = display,
        validationExecutor = ValidationExecutor(resources),
        savedStateHandle = SavedStateHandle(mapOf(KEY_PAYLOAD to ConfirmRewardDestinationPayload(fee, destination)))
    ).also(owned::add).also { vm ->
        waitUntil { vm.rewardDestinationLiveData.value != null && vm.originAccountModelLiveData.value != null }
    }

    private fun capturedExternalAddress(action: () -> Unit): String {
        val payload = slot<ExternalAccountActions.Payload>()
        every { external.showExternalActions(capture(payload)) } returns Unit
        action()
        verify(timeout = 5_000) { external.showExternalActions(any()) }
        return payload.captured.value
    }

    @Test
    fun restakeReviewShowsControllerFeeAndDestination() {
        val vm = viewModel(RewardDestinationParcelModel.Restake)

        assertEquals(RewardDestinationModel.Restake, vm.rewardDestinationLiveData.value)
        assertEquals(stash.controllerAddress, vm.originAccountModelLiveData.value?.address)
        assertEquals("Controller", vm.originAccountModelLiveData.value?.name)
        waitUntil { vm.feeLiveData.value != null }
        assertEquals(0, fee.compareTo(requireNotNull(vm.feeLiveData.value).feeModel.fee))
        assertSame(asset.token, requireNotNull(vm.feeLiveData.value).feeModel.token)
    }

    @Test
    fun payoutReviewShowsTheNamedPayoutAccount() {
        val vm = viewModel(RewardDestinationParcelModel.Payout(payoutAddress))

        val model = vm.rewardDestinationLiveData.value as RewardDestinationModel.Payout
        assertEquals(payoutAddress, model.destination.address)
        assertEquals("Payout wallet", model.destination.name)
        assertEquals(payoutAddress, capturedExternalAddress { vm.payoutAccountClicked() })
    }

    @Test
    fun originClickShowsTheControllerExternalActions() {
        val vm = viewModel(RewardDestinationParcelModel.Restake)

        assertEquals(stash.controllerAddress, capturedExternalAddress { vm.originAccountClicked() })
    }

    @Test
    fun payoutClickWithRestakeDoesNothing() {
        val vm = viewModel(RewardDestinationParcelModel.Restake)

        vm.payoutAccountClicked()

        verify(exactly = 0) { external.showExternalActions(any()) }
    }

    @Test
    fun confirmRestakeSubmitsAndReturnsToMain() {
        val vm = viewModel(RewardDestinationParcelModel.Restake)

        vm.confirmClicked()

        verify(timeout = 5_000) { router.returnToMain() }
        coVerify { changer.changeRewardDestination(stash, RewardDestination.Restake) }
        assertEquals("s${R.string.common_transaction_submitted}", vm.messageLiveData.value?.peekContent())
        assertEquals(false, vm.showNextProgress.value)
        val payload = validated.single()
        assertEquals(asset.availableForStaking, payload.availableControllerBalance)
        assertEquals(0, fee.compareTo(payload.fee))
        assertSame(stash, payload.stashState)
    }

    @Test
    fun confirmPayoutSubmitsThePayoutAccount() {
        val vm = viewModel(RewardDestinationParcelModel.Payout(payoutAddress))
        val destination = slot<RewardDestination>()
        coEvery { changer.changeRewardDestination(any(), capture(destination)) } returns Result.success("0xhash")

        vm.confirmClicked()

        verify(timeout = 5_000) { router.returnToMain() }
        assertArrayEquals(payoutId, (destination.captured as RewardDestination.Payout).targetAccountId)
    }

    @Test
    fun failedSubmissionShowsTheError() {
        coEvery { changer.changeRewardDestination(any(), any()) } returns Result.failure(IllegalStateException("rejected"))
        val vm = viewModel(RewardDestinationParcelModel.Restake)

        vm.confirmClicked()

        waitUntil { vm.errorLiveData.value != null }
        assertEquals("rejected", vm.errorLiveData.value?.peekContent())
        assertEquals(false, vm.showNextProgress.value)
        verify(exactly = 0) { router.returnToMain() }
    }

    @Test
    fun missingControllerShowsTheValidationFailure() {
        status = ValidationStatus.NotValid(
            DefaultFailureLevel.ERROR,
            RewardDestinationValidationFailure.MissingController(stash.controllerAddress)
        )
        val vm = viewModel(RewardDestinationParcelModel.Restake)

        vm.confirmClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${R.string.common_error_general_title}", failure.title)
        assertEquals("s${R.string.staking_add_controller}:${stash.controllerAddress}", failure.message)
        coVerify(exactly = 0) { changer.changeRewardDestination(any(), any()) }
    }

    @Test
    fun backGoesBack() {
        val vm = viewModel(RewardDestinationParcelModel.Restake)

        vm.backClicked()

        verify { router.back() }
    }
}
