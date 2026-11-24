package jp.co.soramitsu.staking.impl.presentation.staking.bond.select

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
import java.math.BigInteger
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.common.navigation.PendingNavigationAction
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
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
import jp.co.soramitsu.staking.impl.domain.staking.bond.BondMoreInteractor
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm.ConfirmBondMorePayload
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SelectBondMoreViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private val owned = mutableListOf<SelectBondMoreViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val stash = StakingState.Stash.None(chain, StakingLaneFixtures.accountId, ByteArray(32) { 8 }, ByteArray(32) { 9 })
    private val builder = mockk<ExtrinsicBuilder>(relaxed = true)

    private val router = mockk<StakingRouter>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val scenario = mockk<StakingScenarioInteractor>(relaxed = true)
    private val bondMore = mockk<BondMoreInteractor>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val fees = mockk<FeeLoaderMixin.Presentation>(relaxed = true)
    private val quickInputs = mockk<QuickInputsUseCase>(relaxed = true)

    private val feeConstructor = slot<suspend (Token) -> BigInteger>()
    private val retryCancelled = slot<() -> Unit>()
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
        Dispatchers.setMain(dispatcher)
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers {
            "s${firstArg<Int>()}:${secondArg<Array<Any>>().joinToString()}"
        }
        every { interactor.currentAssetFlow() } returns flowOf(asset)
        every { scenario.selectedAccountStakingStateFlow() } returns flowOf(stash)
        every { scenario.getSelectedAccountAddress() } returns flowOf(Optional.empty())
        every { scenario.getCollatorAddress(any()) } returns flowOf(Optional.empty())
        coEvery { scenario.getAvailableForBondMoreBalance() } returns BigDecimal("40")
        coEvery { scenario.provideBondMoreValidationSystem() } returns validationSystem
        coEvery { scenario.stakeMore(any(), any(), any()) } returns builder
        coEvery { bondMore.estimateFee(any(), any()) } coAnswers {
            secondArg<suspend ExtrinsicBuilder.() -> Unit>().invoke(builder)
            BigInteger.TEN
        }
        coEvery { bondMore.bondMore(any(), capture(submitForm)) } returns Result.success("0xhash")
        coEvery { quickInputs.calculateStakingQuickInputs(any(), any(), any(), any()) } coAnswers {
            thirdArg<suspend () -> BigDecimal?>().invoke()
            arg<suspend (BigInteger) -> BigInteger>(3).invoke(BigInteger.ONE)
            mapOf(0.5 to BigDecimal("20"), 1.0 to BigDecimal("40"))
        }
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

    private fun viewModel(payload: SelectBondMorePayload = SelectBondMorePayload(null, null)) = SelectBondMoreViewModel(
        router = router,
        interactor = interactor,
        stakingScenarioInteractor = scenario,
        bondMoreInteractor = bondMore,
        resourceManager = resources,
        validationExecutor = ValidationExecutor(resources),
        feeLoaderMixin = fees,
        quickInputsUseCase = quickInputs,
        savedStateHandle = SavedStateHandle(mapOf(PAYLOAD_KEY to payload))
    ).also(owned::add)

    @Test
    fun feeIsLoadedAfterTheDebounceAndBuildsTheStakeMoreCall() {
        viewModel()
        assertFalse(feeConstructor.isCaptured)

        dispatcher.scheduler.advanceTimeBy(600)
        dispatcher.scheduler.runCurrent()

        assertTrue(feeConstructor.isCaptured)
        val fee = runBlocking { feeConstructor.captured.invoke(asset.token) }
        assertEquals(BigInteger.TEN, fee)
        coVerify { bondMore.estimateFee(stash.stashAddress, any()) }
        coVerify { scenario.stakeMore(builder, StakingLaneFixtures.planks(1), null) }
    }

    @Test
    fun quickInputsAreComputedFromTheAvailableBalanceAndFee() {
        val vm = viewModel()

        coVerify { scenario.getAvailableForBondMoreBalance() }
        coVerify { scenario.stakeMore(builder, BigInteger.ONE, null) }

        vm.onQuickAmountInput(0.5)
        assertEquals(BigDecimal("20").formatCryptoDetail(), vm.enteredAmountFlow.value)

        vm.onQuickAmountInput(0.75)
        assertEquals(BigDecimal("20").formatCryptoDetail(), vm.enteredAmountFlow.value)
    }

    @Test
    fun balanceAssetAndAccountStatesArePublished() {
        val vm = viewModel()

        waitUntil {
            vm.stakeCreatorBalanceFlow.replayCache.isNotEmpty() && vm.assetModelFlow.value != null &&
                vm.accountLiveData.value != null && vm.collatorLiveData.value != null
        }
        val balance = vm.stakeCreatorBalanceFlow.replayCache.first()
        assertTrue(balance, balance.startsWith("s${R.string.common_available_format}:${BigDecimal("40").formatCrypto("DOT")}"))
        assertEquals("dot-id", vm.assetModelFlow.value?.chainAssetId)
        assertEquals(chainAsset.chainId, vm.assetModelFlow.value?.chainId)
        assertFalse(requireNotNull(vm.accountLiveData.value).isPresent)
        assertFalse(requireNotNull(vm.collatorLiveData.value).isPresent)
        assertEquals("1", vm.enteredAmountFlow.value)
        assertFalse(vm.oneScreenConfirmation)
    }

    @Test
    fun unknownBalanceIsShownAsUnavailable() {
        coEvery { scenario.getAvailableForBondMoreBalance() } returns null
        val vm = viewModel()

        waitUntil { vm.stakeCreatorBalanceFlow.replayCache.isNotEmpty() }
        val unavailable = "s${jp.co.soramitsu.common.R.string.portfolio_balance_unavailable}"
        val balance = vm.stakeCreatorBalanceFlow.replayCache.first()
        assertTrue(balance, balance.startsWith("s${R.string.common_available_format}:$unavailable"))
    }

    @Test
    fun nextOpensTheConfirmationWithTheEnteredAmount() {
        val vm = viewModel(SelectBondMorePayload(null, "collator"))
        val confirm = slot<ConfirmBondMorePayload>()
        every { router.openConfirmBondMore(capture(confirm)) } returns Unit

        vm.enteredAmountFlow.value = "2.5"
        vm.nextClicked()

        verify(timeout = 5_000) { router.openConfirmBondMore(any()) }
        assertEquals(0, BigDecimal("2.5").compareTo(confirm.captured.amount))
        assertEquals(0, BigDecimal.ONE.compareTo(confirm.captured.fee))
        assertEquals(stash.stashAddress, confirm.captured.stashAddress)
        assertEquals("collator", confirm.captured.collatorAddress)
        assertNull(confirm.captured.overrideFinishAction)
        assertEquals(chainAsset, validated.single().chainAsset)
        assertEquals(false, vm.showNextProgress.value)
    }

    @Test
    fun quickInputThenNextCarriesTheQuickAmount() {
        val vm = viewModel()
        val confirm = slot<ConfirmBondMorePayload>()
        every { router.openConfirmBondMore(capture(confirm)) } returns Unit

        vm.onQuickAmountInput(1.0)
        vm.nextClicked()

        verify(timeout = 5_000) { router.openConfirmBondMore(any()) }
        assertEquals(0, BigDecimal("40").compareTo(confirm.captured.amount))
    }

    @Test
    fun invalidAmountShowsTheValidationFailure() {
        status = ValidationStatus.NotValid(DefaultFailureLevel.ERROR, BondMoreValidationFailure.ZERO_BOND)
        val vm = viewModel()

        vm.nextClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${R.string.common_error_general_title}", failure.title)
        assertEquals("s${R.string.staking_zero_bond_error}", failure.message)
        assertEquals(false, vm.showNextProgress.value)
        verify(exactly = 0) { router.openConfirmBondMore(any()) }
    }

    @Test
    fun missingFeeShowsTheFeeError() {
        every { fees.requireFee(any(), any()) } answers { secondArg<(String, String) -> Unit>().invoke("Fee", "not ready") }
        val vm = viewModel()

        vm.nextClicked()

        assertEquals("Fee" to "not ready", vm.errorWithTitleLiveData.value?.peekContent())
        assertTrue(validated.isEmpty())
    }

    @Test
    fun oneScreenConfirmationSubmitsAndReturnsToTheBalance() {
        val vm = viewModel(SelectBondMorePayload(null, "collator", oneScreenConfirmation = true))
        assertTrue(vm.oneScreenConfirmation)

        vm.confirmClicked()

        verify(timeout = 5_000) { router.returnToStakingBalance() }
        assertEquals("s${R.string.common_transaction_submitted}", vm.messageLiveData.value?.peekContent())
        assertEquals(false, vm.showNextProgress.value)
        coVerify { bondMore.bondMore(stash.stashAddress, any()) }
        runBlocking { submitForm.captured.invoke(builder) }
        coVerify { scenario.stakeMore(builder, StakingLaneFixtures.planks(1), "collator") }
    }

    @Test
    fun submissionRunsTheOverriddenFinishAction() {
        val finish: PendingNavigationAction<StakingRouter> = { it.openPayouts() }
        val vm = viewModel(SelectBondMorePayload(finish, null, oneScreenConfirmation = true))

        vm.confirmClicked()

        verify(timeout = 5_000) { router.openPayouts() }
        verify(exactly = 0) { router.returnToStakingBalance() }
    }

    @Test
    fun failedSubmissionShowsTheError() {
        coEvery { bondMore.bondMore(any(), any()) } returns Result.failure(IllegalStateException("rejected"))
        val vm = viewModel()

        vm.confirmClicked()

        waitUntil { vm.errorLiveData.value != null }
        assertEquals("rejected", vm.errorLiveData.value?.peekContent())
        verify(exactly = 0) { router.returnToStakingBalance() }
    }

    @Test
    fun focusBackAndRetryCancellationAreForwarded() {
        val vm = viewModel()
        dispatcher.scheduler.advanceTimeBy(600)
        dispatcher.scheduler.runCurrent()

        vm.onAmountInputFocusChanged(true)
        assertTrue(vm.isInputFocused.value)
        vm.onAmountInputFocusChanged(false)
        assertFalse(vm.isInputFocused.value)

        vm.backClicked()
        retryCancelled.captured.invoke()
        verify(exactly = 2) { router.back() }
    }
}
