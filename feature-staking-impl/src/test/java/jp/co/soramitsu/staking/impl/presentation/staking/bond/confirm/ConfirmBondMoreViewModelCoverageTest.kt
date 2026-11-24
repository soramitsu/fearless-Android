package jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm

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
import jp.co.soramitsu.common.navigation.PendingNavigationAction
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.bond.BondMoreInteractor
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConfirmBondMoreViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<ConfirmBondMoreViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val stashAddress = chain.addressOf(ByteArray(32) { 9 })
    private val builder = mockk<ExtrinsicBuilder>(relaxed = true)
    private val amount = BigDecimal("3")
    private val fee = BigDecimal("0.2")

    private val router = mockk<StakingRouter>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val bondMore = mockk<BondMoreInteractor>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val icons = mockk<AddressIconGenerator>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>(relaxed = true)
    private val external = mockk<ExternalAccountActions.Presentation>(relaxed = true)
    private val scenario = mockk<StakingScenarioInteractor>(relaxed = true)
    private val submitForm = slot<suspend ExtrinsicBuilder.() -> Unit>()
    private val validated = CopyOnWriteArrayList<BondMoreValidationPayload>()

    @Volatile
    private var status: ValidationStatus<BondMoreValidationFailure> = ValidationStatus.Valid()

    private val validationSystem = ValidationSystem(
        object : Validation<BondMoreValidationPayload, BondMoreValidationFailure> {
            override suspend fun validate(value: BondMoreValidationPayload): ValidationStatus<BondMoreValidationFailure> {
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
        coEvery { interactor.getProjectedAccount(any()) } answers { StakingAccount(firstArg(), "Stash", false) }
        coEvery { scenario.getAvailableForBondMoreBalance() } returns BigDecimal("40")
        coEvery { scenario.provideBondMoreValidationSystem() } returns validationSystem
        coEvery { scenario.stakeMore(any(), any(), any()) } returns builder
        coEvery { bondMore.bondMore(any(), capture(submitForm)) } returns Result.success("0xhash")
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

    private fun viewModel(
        finish: PendingNavigationAction<StakingRouter>? = null,
        collator: String? = null
    ) = ConfirmBondMoreViewModel(
        router = router,
        interactor = interactor,
        bondMoreInteractor = bondMore,
        resourceManager = resources,
        validationExecutor = ValidationExecutor(resources),
        iconGenerator = icons,
        chainRegistry = chainRegistry,
        externalAccountActions = external,
        stakingScenarioInteractor = scenario,
        savedStateHandle = SavedStateHandle(
            mapOf(PAYLOAD_KEY to ConfirmBondMorePayload(amount, fee, stashAddress, finish, collator))
        )
    ).also(owned::add)

    @Test
    fun reviewShowsAmountFeeBalanceAndOrigin() {
        val vm = viewModel()
        vm.originAddressModelLiveData.observeForever { }

        assertEquals(amount.toString(), vm.amount)
        waitUntil {
            vm.assetModelFlow.value != null && vm.feeStatusLiveData.value != null &&
                vm.stakeCreatorBalanceFlow.replayCache.isNotEmpty() && vm.originAddressModelLiveData.value != null
        }
        assertEquals("dot-id", vm.assetModelFlow.value?.chainAssetId)
        assertEquals(0, fee.compareTo(requireNotNull(vm.feeStatusLiveData.value).feeModel.fee))
        assertEquals(BigDecimal("40").formatCryptoDetail("DOT"), vm.stakeCreatorBalanceFlow.replayCache.first())
        assertEquals(stashAddress, vm.originAddressModelLiveData.value?.address)
        assertEquals("Stash", vm.originAddressModelLiveData.value?.name)
    }

    @Test
    fun unknownBalanceIsShownAsUnavailable() {
        coEvery { scenario.getAvailableForBondMoreBalance() } returns null
        val vm = viewModel()

        waitUntil { vm.stakeCreatorBalanceFlow.replayCache.isNotEmpty() }
        assertEquals(
            "s${jp.co.soramitsu.common.R.string.portfolio_balance_unavailable}",
            vm.stakeCreatorBalanceFlow.replayCache.first()
        )
    }

    @Test
    fun originClickShowsTheStashExternalActions() {
        val vm = viewModel()
        val payload = slot<ExternalAccountActions.Payload>()
        every { external.showExternalActions(capture(payload)) } returns Unit

        vm.originAccountClicked()

        verify(timeout = 5_000) { external.showExternalActions(any()) }
        assertEquals(stashAddress, payload.captured.value)
    }

    @Test
    fun confirmSubmitsAndOpensTheSuccessScreen() {
        val vm = viewModel(collator = "collator")

        vm.confirmClicked()

        verify(timeout = 5_000) { router.openOperationSuccess("0xhash", chainAsset.chainId, any(), any()) }
        verify { router.returnToStakingBalance() }
        assertEquals("s${R.string.common_transaction_submitted}", vm.messageLiveData.value?.peekContent())
        assertEquals(false, vm.showNextProgress.value)
        val payload = validated.single()
        assertEquals(stashAddress, payload.stashAddress)
        assertEquals(0, amount.compareTo(payload.amount))
        assertEquals(0, fee.compareTo(payload.fee))
        assertSame(chainAsset, payload.chainAsset)
        coVerify { bondMore.bondMore(stashAddress, any()) }
        runBlocking { submitForm.captured.invoke(builder) }
        coVerify { scenario.stakeMore(builder, StakingLaneFixtures.planks(3), "collator") }
    }

    @Test
    fun confirmRunsTheOverriddenFinishAction() {
        val finish: PendingNavigationAction<StakingRouter> = { it.openPayouts() }
        val vm = viewModel(finish = finish)

        vm.confirmClicked()

        verify(timeout = 5_000) { router.openPayouts() }
        verify(exactly = 0) { router.returnToStakingBalance() }
        assertTrue(vm.messageLiveData.value != null)
    }

    @Test
    fun failedSubmissionShowsTheError() {
        coEvery { bondMore.bondMore(any(), any()) } returns Result.failure(IllegalStateException("rejected"))
        val vm = viewModel()

        vm.confirmClicked()

        waitUntil { vm.errorLiveData.value != null }
        assertEquals("rejected", vm.errorLiveData.value?.peekContent())
        assertEquals(false, vm.showNextProgress.value)
        verify(exactly = 0) { router.returnToStakingBalance() }
    }

    @Test
    fun notEnoughForFeesShowsTheValidationFailure() {
        status = ValidationStatus.NotValid(DefaultFailureLevel.ERROR, BondMoreValidationFailure.NOT_ENOUGH_TO_PAY_FEES)
        val vm = viewModel()

        vm.confirmClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${R.string.common_not_enough_funds_title}", failure.title)
        assertEquals("s${R.string.common_not_enough_funds_message}", failure.message)
        coVerify(exactly = 0) { bondMore.bondMore(any(), any()) }
    }

    @Test
    fun backGoesBack() {
        viewModel().backClicked()

        verify { router.back() }
    }
}
