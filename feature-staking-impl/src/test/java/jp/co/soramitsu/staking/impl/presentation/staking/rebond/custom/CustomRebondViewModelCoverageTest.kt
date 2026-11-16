package jp.co.soramitsu.staking.impl.presentation.staking.rebond.custom

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
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.rebond.RebondInteractor
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm.ConfirmRebondPayload
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.impl.domain.model.Token
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CustomRebondViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private val owned = mutableListOf<CustomRebondViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val stash = StakingState.Stash.None(chain, StakingLaneFixtures.accountId, ByteArray(32) { 8 }, ByteArray(32) { 9 })
    private val builder = mockk<ExtrinsicBuilder>(relaxed = true)

    private val router = mockk<StakingRouter>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val scenario = mockk<StakingScenarioInteractor>(relaxed = true)
    private val rebond = mockk<RebondInteractor>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val fees = mockk<FeeLoaderMixin.Presentation>(relaxed = true)

    private val feeConstructor = slot<suspend (Token) -> BigInteger>()
    private val retryCancelled = slot<() -> Unit>()
    private val estimateForm = slot<suspend ExtrinsicBuilder.() -> Unit>()
    private val validated = CopyOnWriteArrayList<RebondValidationPayload>()

    @Volatile
    private var status: ValidationStatus<RebondValidationFailure> = ValidationStatus.Valid()

    private val validationSystem = ValidationSystem(
        object : Validation<RebondValidationPayload, RebondValidationFailure> {
            override suspend fun validate(value: RebondValidationPayload): ValidationStatus<RebondValidationFailure> {
                validated += value
                return status
            }
        }
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers {
            "s${firstArg<Int>()}:${secondArg<Array<Any>>().joinToString()}"
        }
        every { interactor.currentAssetFlow() } returns flowOf(asset)
        every { scenario.selectedAccountStakingStateFlow() } returns flowOf(stash)
        every { scenario.getRebondValidationSystem() } returns validationSystem
        every { scenario.rebond(any(), any(), any()) } returns builder
        coEvery { rebond.estimateFee(any(), capture(estimateForm)) } returns BigInteger.TEN
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

    private fun viewModel() = CustomRebondViewModel(
        router = router,
        interactor = interactor,
        stakingScenarioInteractor = scenario,
        rebondInteractor = rebond,
        resourceManager = resources,
        validationExecutor = ValidationExecutor(resources),
        feeLoaderMixin = fees
    ).also(owned::add)

    private fun passDebounce() {
        dispatcher.scheduler.advanceTimeBy(600)
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun unbondingBalanceIsShown() {
        val vm = viewModel()

        waitUntil { vm.assetModelFlow.value != null }
        val expected = "s${R.string.staking_unbonding_format}:${requireNotNull(asset.unbonding).formatCrypto("DOT")}"
        assertEquals(expected, vm.assetModelFlow.value?.assetBalance)
        assertEquals("1", vm.enteredAmountFlow.value)
    }

    @Test
    fun feeIsLoadedForTheEnteredAmountAfterTheDebounce() {
        val vm = viewModel()
        assertFalse(feeConstructor.isCaptured)

        vm.enteredAmountFlow.value = "4"
        passDebounce()

        val fee = runBlocking { feeConstructor.captured.invoke(asset.token) }
        assertEquals(BigInteger.TEN, fee)
        coVerify { rebond.estimateFee(stash, any()) }
        runBlocking { estimateForm.captured.invoke(builder) }
        verify { scenario.rebond(builder, StakingLaneFixtures.planks(4), null) }
    }

    @Test
    fun confirmOpensTheRebondConfirmation() {
        val vm = viewModel()
        val confirm = slot<ConfirmRebondPayload>()
        every { router.openConfirmRebond(capture(confirm)) } returns Unit

        vm.enteredAmountFlow.value = "2.5"
        vm.confirmClicked()

        verify(timeout = 5_000) { router.openConfirmRebond(any()) }
        assertEquals(0, BigDecimal("2.5").compareTo(confirm.captured.amount))
        assertNull(confirm.captured.collatorAddress)
        assertEquals(false, vm.showNextProgress.value)
        val payload = validated.single()
        assertSame(asset, payload.controllerAsset)
        assertEquals(0, BigDecimal.ONE.compareTo(payload.fee))
    }

    @Test
    fun zeroAmountShowsTheValidationFailure() {
        status = ValidationStatus.NotValid(DefaultFailureLevel.ERROR, RebondValidationFailure.ZERO_AMOUNT)
        val vm = viewModel()

        vm.confirmClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${R.string.common_error_general_title}", failure.title)
        assertEquals("s${R.string.staking_zero_bond_error}", failure.message)
        assertEquals(false, vm.showNextProgress.value)
        verify(exactly = 0) { router.openConfirmRebond(any()) }
    }

    @Test
    fun cannotPayFeeShowsTheFundsFailure() {
        status = ValidationStatus.NotValid(DefaultFailureLevel.ERROR, RebondValidationFailure.CANNOT_PAY_FEE)
        val vm = viewModel()

        vm.confirmClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${R.string.common_not_enough_funds_title}", failure.title)
        assertEquals("s${R.string.common_not_enough_funds_message}", failure.message)
    }

    @Test
    fun missingFeeShowsTheFeeError() {
        every { fees.requireFee(any(), any()) } answers { secondArg<(String, String) -> Unit>().invoke("Fee", "pending") }
        val vm = viewModel()

        vm.confirmClicked()

        assertEquals("Fee" to "pending", vm.errorWithTitleLiveData.value?.peekContent())
        assertTrue(validated.isEmpty())
    }

    @Test
    fun backAndRetryCancellationGoBack() {
        val vm = viewModel()
        passDebounce()

        vm.backClicked()
        retryCancelled.captured.invoke()

        verify(exactly = 2) { router.back() }
    }
}
