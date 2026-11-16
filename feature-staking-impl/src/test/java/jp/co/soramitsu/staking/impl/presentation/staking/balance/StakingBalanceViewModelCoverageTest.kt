package jp.co.soramitsu.staking.impl.presentation.staking.balance

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.DelegationAction
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.model.Unbonding
import jp.co.soramitsu.staking.impl.domain.staking.unbond.UnbondInteractor
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.balance.StakingBalanceFragment.Companion.KEY_COLLATOR_ADDRESS
import jp.co.soramitsu.staking.impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.staking.impl.presentation.staking.balance.rebond.RebondKind
import jp.co.soramitsu.staking.impl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm.ConfirmRebondPayload
import jp.co.soramitsu.staking.impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.select.SelectUnbondPayload
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.model.AmountModel
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StakingBalanceViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<StakingBalanceViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val stash = StakingState.Stash.None(chain, StakingLaneFixtures.accountId, ByteArray(32) { 8 }, ByteArray(32) { 9 })
    private val collator = "0x" + "01".repeat(20)
    private val older = Unbonding(StakingLaneFixtures.planks(2), 60_000L, 0L, DelegationAction.UNSTAKE)
    private val newer = Unbonding(StakingLaneFixtures.planks(1), 120_000L, 0L, DelegationAction.UNSTAKE)

    private val router = mockk<StakingRouter>(relaxed = true)
    private val unbond = mockk<UnbondInteractor>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val scenario = mockk<StakingScenarioInteractor>(relaxed = true)
    private val validated = CopyOnWriteArrayList<ManageStakingValidationPayload>()

    @Volatile
    private var status: ValidationStatus<ManageStakingValidationFailure> = ValidationStatus.Valid()

    private val validationSystem = ValidationSystem(
        object : Validation<ManageStakingValidationPayload, ManageStakingValidationFailure> {
            override suspend fun validate(value: ManageStakingValidationPayload): ValidationStatus<ManageStakingValidationFailure> {
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
        val amount = AmountModel(BigDecimal.ONE, "1", null)
        every { interactor.currentAssetFlow() } returns flowOf(asset)
        every { scenario.getStakingBalanceFlow(any()) } returns flowOf(StakingBalanceModel(amount, amount, amount))
        coEvery { scenario.currentUnbondingsFlow(any()) } returns flowOf(listOf(older, newer))
        coEvery { scenario.getRebondingUnbondings(any()) } returns listOf(older, newer)
        every { scenario.overrideRedeemActionTitle() } returns null
        every { scenario.getBondMoreValidation() } returns validationSystem
        every { scenario.getUnbondingValidation() } returns validationSystem
        every { scenario.getRedeemValidation() } returns validationSystem
        every { scenario.getRebondValidation() } returns validationSystem
        every { scenario.getRebondTypes() } returns setOf(RebondKind.ALL, RebondKind.CUSTOM)
        coEvery { scenario.getSelectedAccountStakingState() } returns stash
        every { unbond.newestUnbondingAmount(any()) } answers { firstArg<List<Unbonding>>().last().amount }
        every { unbond.allUnbondingsAmount(any()) } returns StakingLaneFixtures.planks(3)
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

    private fun viewModel(collatorAddress: String? = null) = StakingBalanceViewModel(
        router = router,
        validationExecutor = ValidationExecutor(resources),
        unbondingInteractor = unbond,
        resourceManager = resources,
        interactor = interactor,
        stakingScenarioInteractor = scenario,
        savedStateHandle = SavedStateHandle(
            collatorAddress?.let { mapOf(KEY_COLLATOR_ADDRESS to it) }.orEmpty()
        )
    ).also(owned::add)

    @Test
    fun bondMoreForACollatorOpensTheOneScreenFlow() {
        val payload = slot<SelectBondMorePayload>()
        every { router.openBondMore(capture(payload)) } returns Unit
        val vm = viewModel(collator)

        vm.bondMoreClicked()

        verify(timeout = 5_000) { router.openBondMore(any()) }
        assertEquals(collator, payload.captured.collatorAddress)
        assertTrue(payload.captured.oneScreenConfirmation)
        assertNull(payload.captured.overrideFinishAction)
        assertSame(stash, validated.single().stashState)
        waitUntil { vm.pendingAction.value == false }
    }

    @Test
    fun unbondWithoutACollatorOpensTheRegularFlow() {
        val payload = slot<SelectUnbondPayload>()
        every { router.openSelectUnbond(capture(payload)) } returns Unit
        val vm = viewModel()

        vm.unbondClicked()

        verify(timeout = 5_000) { router.openSelectUnbond(any()) }
        assertNull(payload.captured.collatorAddress)
        assertFalse(payload.captured.oneScreenConfirmation)
        waitUntil { vm.pendingAction.value == false }
    }

    @Test
    fun redeemOpensTheRedeemScreen() {
        val payload = slot<RedeemPayload>()
        every { router.openRedeem(capture(payload)) } returns Unit
        val vm = viewModel(collator)

        vm.redeemClicked()

        verify(timeout = 5_000) { router.openRedeem(any()) }
        assertEquals(collator, payload.captured.collatorAddress)
        assertNull(payload.captured.overrideFinishAction)
    }

    @Test
    fun aMissingControllerBlocksTheActionWithItsMessage() {
        status = ValidationStatus.NotValid(DefaultFailureLevel.ERROR, ManageStakingValidationFailure.ControllerRequired("ctrl"))
        val vm = viewModel()

        vm.bondMoreClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${R.string.common_error_general_title}", failure.title)
        assertEquals("f${R.string.staking_add_controller}:ctrl", failure.message)
        waitUntil { vm.pendingAction.value == false }
        verify(exactly = 0) { router.openBondMore(any()) }
    }

    @Test
    fun theUnbondingLimitBlocksUnbonding() {
        status = ValidationStatus.NotValid(DefaultFailureLevel.ERROR, ManageStakingValidationFailure.UnbondingRequestLimitReached(32))
        val vm = viewModel()

        vm.unbondClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${R.string.staking_unbonding_limit_reached_title}", failure.title)
        assertEquals("f${R.string.staking_unbonding_limit_reached_message}:32", failure.message)
        verify(exactly = 0) { router.openSelectUnbond(any()) }
    }

    @Test
    fun unbondingsMoreOffersTheAllowedRebondKinds() {
        val vm = viewModel()

        vm.unbondingsMoreClicked()

        waitUntil { vm.showRebondActionsEvent.value != null }
        assertEquals(setOf(RebondKind.ALL, RebondKind.CUSTOM), vm.showRebondActionsEvent.value?.peekContent())
        waitUntil { vm.pendingAction.value == false }
    }

    @Test
    fun rebondingTheLastUnbondingConfirmsItsAmount() {
        val payload = slot<ConfirmRebondPayload>()
        every { router.openConfirmRebond(capture(payload)) } returns Unit
        val vm = viewModel(collator)

        vm.rebondKindChosen(RebondKind.LAST)

        verify(timeout = 5_000) { router.openConfirmRebond(any()) }
        val expected = asset.token.amountFromPlanks(StakingLaneFixtures.planks(1))
        assertEquals(0, expected.compareTo(payload.captured.amount))
        assertEquals(collator, payload.captured.collatorAddress)
    }

    @Test
    fun rebondingAllUnbondingsConfirmsTheirSum() {
        val payload = slot<ConfirmRebondPayload>()
        every { router.openConfirmRebond(capture(payload)) } returns Unit
        val vm = viewModel()

        vm.rebondKindChosen(RebondKind.ALL)

        verify(timeout = 5_000) { router.openConfirmRebond(any()) }
        assertEquals(0, BigDecimal("3").compareTo(payload.captured.amount))
        assertNull(payload.captured.collatorAddress)
    }

    @Test
    fun customRebondAndBackNavigate() {
        val vm = viewModel()

        vm.rebondKindChosen(RebondKind.CUSTOM)
        vm.backClicked()

        verify { router.openCustomRebond() }
        verify { router.back() }
    }
}
