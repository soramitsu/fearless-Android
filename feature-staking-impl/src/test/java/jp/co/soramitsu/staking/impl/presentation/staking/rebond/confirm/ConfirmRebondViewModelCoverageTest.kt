package jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm

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
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoFull
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.rebond.RebondInteractor
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConfirmRebondViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<ConfirmRebondViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val stash = StakingState.Stash.None(chain, StakingLaneFixtures.accountId, ByteArray(32) { 8 }, ByteArray(32) { 9 })
    private val builder = mockk<ExtrinsicBuilder>(relaxed = true)
    private val amount = BigDecimal("2")

    private val router = mockk<StakingRouter>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val scenario = mockk<StakingScenarioInteractor>(relaxed = true)
    private val rebond = mockk<RebondInteractor>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val icons = mockk<AddressIconGenerator>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>(relaxed = true)
    private val external = mockk<ExternalAccountActions.Presentation>(relaxed = true)
    private val fees = mockk<FeeLoaderMixin.Presentation>(relaxed = true)

    private val feeConstructor = slot<suspend (Token) -> BigInteger>()
    private val retryCancelled = slot<() -> Unit>()
    private val estimateForm = slot<suspend ExtrinsicBuilder.() -> Unit>()
    private val submitForm = slot<suspend ExtrinsicBuilder.() -> Unit>()
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
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers {
            "s${firstArg<Int>()}:${secondArg<Array<Any>>().joinToString()}"
        }
        every { interactor.currentAssetFlow() } returns flowOf(asset)
        coEvery { interactor.getProjectedAccount(any()) } answers { StakingAccount(firstArg(), "Controller", false) }
        every { scenario.selectedAccountStakingStateFlow() } returns flowOf(stash)
        every { scenario.getRebondAvailableAmount(any(), any()) } returns BigDecimal("3")
        every { scenario.getRebondValidationSystem() } returns validationSystem
        every { scenario.rebond(any(), any(), any()) } returns builder
        coEvery { rebond.estimateFee(any(), capture(estimateForm)) } returns BigInteger.TEN
        coEvery { rebond.rebond(any(), capture(submitForm)) } returns Result.success("0xhash")
        coEvery { icons.createAddressIcon(any<ByteArray>(), any(), any()) } returns PictureDrawable(Picture())
        coEvery { chainRegistry.getChain(any()) } returns chain
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

    private fun viewModel(collator: String? = "collator") = ConfirmRebondViewModel(
        router = router,
        interactor = interactor,
        stakingScenarioInteractor = scenario,
        rebondInteractor = rebond,
        resourceManager = resources,
        validationExecutor = ValidationExecutor(resources),
        iconGenerator = icons,
        chainRegistry = chainRegistry,
        externalAccountActions = external,
        feeLoaderMixin = fees,
        savedStateHandle = SavedStateHandle(
            mapOf(ConfirmRebondFragment.PAYLOAD_KEY to ConfirmRebondPayload(amount, collator))
        )
    ).also(owned::add)

    @Test
    fun feeIsEstimatedForTheRebondCall() {
        viewModel()

        val fee = runBlocking { feeConstructor.captured.invoke(asset.token) }
        assertEquals(BigInteger.TEN, fee)
        coVerify { rebond.estimateFee(stash, any()) }
        runBlocking { estimateForm.captured.invoke(builder) }
        verify { scenario.rebond(builder, StakingLaneFixtures.planks(2), "collator") }
    }

    @Test
    fun amountAssetAndOriginAreShown() {
        val vm = viewModel()

        assertEquals(amount.formatCryptoFull(), vm.amount)
        waitUntil { vm.assetModelFlow.value != null && vm.originAddressModelLiveData.value != null }
        val expectedBalance = "s${R.string.staking_unbonding_format}:${BigDecimal("3").formatCrypto("DOT")}"
        assertEquals(expectedBalance, vm.assetModelFlow.value?.assetBalance)
        assertEquals(stash.executionAddress, vm.originAddressModelLiveData.value?.address)
        assertEquals("Controller", vm.originAddressModelLiveData.value?.name)
        verify { scenario.getRebondAvailableAmount(asset, amount) }
    }

    @Test
    fun originClickOpensTheExternalActions() {
        val vm = viewModel()
        val payload = slot<ExternalAccountActions.Payload>()
        every { external.showExternalActions(capture(payload)) } returns Unit
        waitUntil { vm.originAddressModelLiveData.value != null }

        vm.originAccountClicked()

        verify(timeout = 5_000) { external.showExternalActions(any()) }
        assertEquals(stash.executionAddress, payload.captured.value)
        assertTrue(payload.captured.explorers.isEmpty())
    }

    @Test
    fun confirmSubmitsTheRebondAndOpensTheSuccessScreen() {
        val vm = viewModel()

        vm.confirmClicked()

        verify(timeout = 5_000) { router.openOperationSuccess("0xhash", chainAsset.chainId, any(), any()) }
        verify { router.returnToStakingBalance() }
        assertEquals("s${R.string.common_transaction_submitted}", vm.messageLiveData.value?.peekContent())
        waitUntil { vm.showNextProgress.value == false }
        val payload = validated.single()
        assertSame(asset, payload.controllerAsset)
        assertEquals(0, BigDecimal.ONE.compareTo(payload.fee))
        assertEquals(0, amount.compareTo(payload.rebondAmount))
        runBlocking { submitForm.captured.invoke(builder) }
        verify { scenario.rebond(builder, StakingLaneFixtures.planks(2), "collator") }
    }

    @Test
    fun failedRebondShowsTheError() {
        coEvery { rebond.rebond(any(), any()) } returns Result.failure(IllegalStateException("rejected"))
        val vm = viewModel(collator = null)

        vm.confirmClicked()

        waitUntil { vm.errorLiveData.value != null }
        assertEquals("rejected", vm.errorLiveData.value?.peekContent())
        waitUntil { vm.showNextProgress.value == false }
        verify(exactly = 0) { router.returnToStakingBalance() }
    }

    @Test
    fun invalidRebondShowsTheValidationFailure() {
        status = ValidationStatus.NotValid(DefaultFailureLevel.ERROR, RebondValidationFailure.NOT_ENOUGH_UNBONDINGS)
        val vm = viewModel()

        vm.confirmClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${R.string.common_not_enough_funds_title}", failure.title)
        assertEquals("s${R.string.staking_rebond_insufficient_bondings}", failure.message)
        coVerify(exactly = 0) { rebond.rebond(any(), any()) }
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

        vm.backClicked()
        retryCancelled.captured.invoke()

        verify(exactly = 2) { router.back() }
    }
}
