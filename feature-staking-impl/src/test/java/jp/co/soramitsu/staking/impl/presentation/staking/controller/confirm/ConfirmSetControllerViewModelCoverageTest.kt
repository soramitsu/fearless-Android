package jp.co.soramitsu.staking.impl.presentation.staking.controller.confirm

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
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.controller.ControllerInteractor
import jp.co.soramitsu.staking.impl.domain.validations.controller.SetControllerValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.controller.SetControllerValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConfirmSetControllerViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<ConfirmSetControllerViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val stashAddress = chain.addressOf(ByteArray(32) { 9 })
    private val controllerAddress = chain.addressOf(ByteArray(32) { 8 })
    private val fee = BigDecimal("0.3")
    private val transferable = BigDecimal("12")

    private val router = mockk<StakingRouter>(relaxed = true)
    private val controller = mockk<ControllerInteractor>(relaxed = true)
    private val icons = mockk<AddressIconGenerator>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>(relaxed = true)
    private val external = mockk<ExternalAccountActions.Presentation>(relaxed = true)
    private val validated = CopyOnWriteArrayList<SetControllerValidationPayload>()

    @Volatile
    private var status: ValidationStatus<SetControllerValidationFailure> = ValidationStatus.Valid()

    private val validationSystem = ValidationSystem(
        object : Validation<SetControllerValidationPayload, SetControllerValidationFailure> {
            override suspend fun validate(
                value: SetControllerValidationPayload
            ): ValidationStatus<SetControllerValidationFailure> {
                validated += value
                return status
            }
        }
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { interactor.currentAssetFlow() } returns flowOf(asset)
        coEvery { interactor.getUtilityAsset(any<String>()) } returns asset
        coEvery { interactor.getProjectedAccount(any()) } answers {
            StakingAccount(firstArg(), if (firstArg<String>() == stashAddress) "Stash" else "Controller", false)
        }
        coEvery { controller.setController(any(), any(), any()) } returns Result.success("0xhash")
        coEvery { icons.createAddressIcon(any<ByteArray>(), any(), any()) } returns PictureDrawable(Picture())
        coEvery { chainRegistry.getChain(any()) } returns chain
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

    private fun viewModel(chainId: String? = null) = ConfirmSetControllerViewModel(
        router = router,
        controllerInteractor = controller,
        addressIconGenerator = icons,
        interactor = interactor,
        resourceManager = resources,
        chainRegistry = chainRegistry,
        externalActions = external,
        validationExecutor = ValidationExecutor(resources),
        validationSystem = validationSystem,
        savedStateHandle = SavedStateHandle(
            mapOf(PAYLOAD_KEY to ConfirmSetControllerPayload(fee, stashAddress, controllerAddress, transferable, chainId))
        )
    ).also(owned::add)

    private fun externalAddress(action: () -> Unit): String {
        val payload = slot<ExternalAccountActions.Payload>()
        every { external.showExternalActions(capture(payload)) } returns Unit
        action()
        verify(timeout = 5_000) { external.showExternalActions(any()) }
        return payload.captured.value
    }

    @Test
    fun reviewShowsFeeAndBothAccounts() {
        val vm = viewModel()
        vm.stashAddressLiveData.observeForever { }
        vm.controllerAddressLiveData.observeForever { }

        waitUntil {
            vm.feeStatusLiveData.value != null && vm.stashAddressLiveData.value != null &&
                vm.controllerAddressLiveData.value != null
        }
        assertEquals(0, fee.compareTo(requireNotNull(vm.feeStatusLiveData.value).feeModel.fee))
        assertEquals(stashAddress, vm.stashAddressLiveData.value?.address)
        assertEquals("Stash", vm.stashAddressLiveData.value?.name)
        assertEquals(controllerAddress, vm.controllerAddressLiveData.value?.address)
        assertEquals("Controller", vm.controllerAddressLiveData.value?.name)
    }

    @Test
    fun explicitChainReadsItsUtilityAsset() {
        val vm = viewModel(chainId = chainAsset.chainId)

        waitUntil { vm.feeStatusLiveData.value != null }
        assertSame(asset.token, requireNotNull(vm.feeStatusLiveData.value).feeModel.token)
        coVerify { interactor.getUtilityAsset(chainAsset.chainId) }
        verify(exactly = 0) { interactor.currentAssetFlow() }
    }

    @Test
    fun accountClicksShowTheirExternalActions() {
        val vm = viewModel()

        assertEquals(stashAddress, externalAddress { vm.openStashExternalActions() })
        assertEquals(controllerAddress, externalAddress { vm.openControllerExternalActions() })
    }

    @Test
    fun confirmSetsTheControllerAndReturnsToMain() {
        val vm = viewModel()

        vm.confirmClicked()

        verify(timeout = 5_000) { router.returnToMain() }
        assertEquals("s${R.string.staking_controller_change_success}", vm.messageLiveData.value?.peekContent())
        coVerify { controller.setController(stashAddress, controllerAddress, any()) }
        val payload = validated.single()
        assertEquals(stashAddress, payload.stashAddress)
        assertEquals(controllerAddress, payload.controllerAddress)
        assertEquals(0, fee.compareTo(payload.fee))
        assertEquals(0, transferable.compareTo(requireNotNull(payload.transferable)))
    }

    @Test
    fun failedSubmissionStaysOnTheScreen() {
        coEvery { controller.setController(any(), any(), any()) } returns Result.failure(IllegalStateException("rejected"))
        val vm = viewModel()

        vm.confirmClicked()

        coVerify(timeout = 5_000) { controller.setController(stashAddress, controllerAddress, any()) }
        verify(exactly = 0) { router.returnToMain() }
        assertNull(vm.messageLiveData.value)
    }

    @Test
    fun alreadyControllerShowsTheValidationFailure() {
        status = ValidationStatus.NotValid(DefaultFailureLevel.ERROR, SetControllerValidationFailure.ALREADY_CONTROLLER)
        val vm = viewModel()

        vm.confirmClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${R.string.staking_already_controller_title}", failure.title)
        assertEquals("s${R.string.staking_account_is_used_as_controller}", failure.message)
        coVerify(exactly = 0) { controller.setController(any(), any(), any()) }
    }

    @Test
    fun confirmedZeroBalanceWarningStillSetsTheController() {
        status = ValidationStatus.NotValid(DefaultFailureLevel.WARNING, SetControllerValidationFailure.ZERO_CONTROLLER_BALANCE)
        val vm = viewModel()

        vm.confirmClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${R.string.common_confirmation_title}", failure.title)
        assertEquals("s${R.string.staking_controller_account_zero_balance}", failure.message)

        failure.confirmWarning()

        verify(timeout = 5_000) { router.returnToMain() }
    }

    @Test
    fun backGoesBack() {
        viewModel().back()

        verify { router.back() }
    }
}
