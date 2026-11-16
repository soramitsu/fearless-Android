package jp.co.soramitsu.staking.impl.presentation.staking.redeem

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
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.navigation.PendingNavigationAction
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_staking_impl.R as StakingR
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.redeem.RedeemConsequences
import jp.co.soramitsu.staking.impl.domain.staking.redeem.RedeemInteractor
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.api.presentation.model.AmountModel
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RedeemViewModelCoverageTest {
    private companion object {
        const val COLLATOR_HEX = "0x1111111111111111111111111111111111111111"
    }


    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<RedeemViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val fiatToken = StakingLaneFixtures.token(chainAsset, BigDecimal("2"))
    private val asset = StakingLaneFixtures.asset(chainAsset).copy(token = fiatToken)
    private val stash = StakingState.Stash.None(chain, StakingLaneFixtures.accountId, ByteArray(32) { 8 }, ByteArray(32) { 9 })
    private val builder = mockk<ExtrinsicBuilder>(relaxed = true)
    private val balance = StakingBalanceModel(
        AmountModel(BigDecimal.TEN, "10", null),
        AmountModel(BigDecimal.ONE, "1", null),
        AmountModel(BigDecimal("5"), "5", null)
    )

    private val router = mockk<StakingRouter>(relaxed = true)
    private val scenario = mockk<StakingScenarioInteractor>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val redeem = mockk<RedeemInteractor>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val icons = mockk<AddressIconGenerator>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>(relaxed = true)
    private val fees = mockk<FeeLoaderMixin.Presentation>(relaxed = true)
    private val external = mockk<ExternalAccountActions.Presentation>(relaxed = true)

    private val feeConstructor = slot<suspend (Token) -> BigInteger>()
    private val retryCancelled = slot<() -> Unit>()
    private val estimateForm = slot<suspend ExtrinsicBuilder.() -> Unit>()
    private val submitForm = slot<suspend ExtrinsicBuilder.() -> Unit>()
    private val validated = CopyOnWriteArrayList<RedeemValidationPayload>()

    @Volatile
    private var status: ValidationStatus<RedeemValidationFailure> = ValidationStatus.Valid()

    private val validationSystem = ValidationSystem(
        object : Validation<RedeemValidationPayload, RedeemValidationFailure> {
            override suspend fun validate(value: RedeemValidationPayload): ValidationStatus<RedeemValidationFailure> {
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
        coEvery { interactor.getProjectedAccount(any()) } answers { StakingAccount(firstArg(), "Controller", false) }
        every { scenario.stakingStateFlow() } returns flowOf(stash)
        every { scenario.getSelectedAccountAddress() } returns emptyFlow()
        every { scenario.getCollatorAddress(any()) } returns emptyFlow()
        every { scenario.getStakingBalanceFlow(any()) } returns flowOf(balance)
        every { scenario.overrideUnbondAvailableLabel() } returns null
        coEvery { scenario.getUnstakeAvailableAmount(any(), any()) } returns BigDecimal("5")
        every { scenario.provideRedeemValidationSystem() } returns validationSystem
        coEvery { redeem.estimateFee(any(), capture(estimateForm)) } returns BigInteger.TEN
        coEvery { redeem.redeem(any(), any(), capture(submitForm)) } returns
            Result.success(RedeemConsequences(willKillStash = false, hash = "0xhash"))
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

    private fun viewModel(
        collator: String? = null,
        finish: PendingNavigationAction<StakingRouter>? = null
    ) = RedeemViewModel(
        router = router,
        stakingScenarioInteractor = scenario,
        interactor = interactor,
        redeemInteractor = redeem,
        resourceManager = resources,
        validationExecutor = ValidationExecutor(resources),
        iconGenerator = icons,
        chainRegistry = chainRegistry,
        feeLoaderMixin = fees,
        externalAccountActions = external,
        savedStateHandle = SavedStateHandle(mapOf(PAYLOAD_KEY to RedeemPayload(collator, finish)))
    ).also(owned::add)

    @Test
    fun assetShowsTheUnstakeAvailableAmount() {
        val vm = viewModel()

        waitUntil { vm.assetModelFlow.value != null }
        val expected = "f${R.string.common_available_format}:${BigDecimal("5").formatCrypto("DOT")}"
        assertTrue(requireNotNull(vm.assetModelFlow.value).assetBalance.startsWith(expected))
        coVerify { scenario.getUnstakeAvailableAmount(asset, null) }
    }

    @Test
    fun enteredAmountIsShownInFiat() {
        val vm = viewModel()
        waitUntil { vm.stakingUnlockAmount.subscriptionCount.value >= 1 }

        runBlocking { vm.stakingUnlockAmount.emit("3") }

        val expected = fiatToken.fiatAmount(BigDecimal("3"))?.formatFiat(fiatToken.fiatSymbol)
        assertNotNull(expected)
        waitUntil { vm.enteredFiatAmountFlow.value == expected }
    }

    @Test
    fun feeIsEstimatedForTheRevokeCall() {
        viewModel(collator = "collator")

        val fee = runBlocking { feeConstructor.captured.invoke(fiatToken) }
        assertEquals(BigInteger.TEN, fee)
        coVerify { redeem.estimateFee(stash, any()) }
        runBlocking { estimateForm.captured.invoke(builder) }
        coVerify { scenario.confirmRevoke(builder, "collator", stash) }
    }

    @Test
    fun confirmRedeemsAndReturnsToTheBalance() {
        val vm = viewModel()

        vm.confirmClicked()

        verify(timeout = 5_000) { router.openOperationSuccess("0xhash", chainAsset.chainId, any(), any()) }
        verify { router.returnToStakingBalance() }
        verify(exactly = 0) { router.returnToMain() }
        assertEquals("s${R.string.common_transaction_submitted}", vm.messageLiveData.value?.peekContent())
        waitUntil { vm.showNextProgress.value == false }
        val payload = validated.single()
        assertSame(asset, payload.asset)
        assertEquals(0, BigDecimal.ONE.compareTo(payload.fee))
        runBlocking { submitForm.captured.invoke(builder) }
        coVerify { scenario.confirmRevoke(builder, null, stash) }
    }

    @Test
    fun redeemingEverythingReturnsToMain() {
        coEvery { redeem.redeem(any(), any(), any()) } returns
            Result.success(RedeemConsequences(willKillStash = true, hash = "0xkill"))
        val vm = viewModel()

        vm.confirmClicked()

        verify(timeout = 5_000) { router.openOperationSuccess("0xkill", chainAsset.chainId, any(), any()) }
        verify { router.returnToMain() }
        verify(exactly = 0) { router.returnToStakingBalance() }
    }

    @Test
    fun anOverriddenFinishActionReplacesTheDefaultNavigation() {
        val vm = viewModel(collator = COLLATOR_HEX, finish = { it.openPayouts() })

        vm.confirmClicked()

        verify(timeout = 5_000) { router.openOperationSuccess("0xhash", chainAsset.chainId, any(), any()) }
        verify { router.openPayouts() }
        verify(exactly = 0) { router.returnToStakingBalance() }
        assertEquals(COLLATOR_HEX, validated.single().collatorAddress)
    }

    @Test
    fun failedRedeemShowsTheError() {
        coEvery { redeem.redeem(any(), any(), any()) } returns Result.failure(IllegalStateException("rejected"))
        val vm = viewModel()

        vm.confirmClicked()

        waitUntil { vm.errorLiveData.value != null }
        assertEquals("rejected", vm.errorLiveData.value?.peekContent())
        waitUntil { vm.showNextProgress.value == false }
        verify(exactly = 0) { router.returnToStakingBalance() }
    }

    @Test
    fun invalidRedeemShowsTheValidationFailure() {
        status = ValidationStatus.NotValid(DefaultFailureLevel.ERROR, RedeemValidationFailure.CANNOT_PAY_FEES)
        val vm = viewModel()

        vm.confirmClicked()

        waitUntil { vm.validationFailureEvent.value != null }
        val failure = requireNotNull(vm.validationFailureEvent.value).peekContent()
        assertEquals("s${StakingR.string.common_not_enough_funds_title}", failure.title)
        assertEquals("s${StakingR.string.common_not_enough_funds_message}", failure.message)
        coVerify(exactly = 0) { redeem.redeem(any(), any(), any()) }
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
    fun originClickOpensTheExternalActions() {
        val vm = viewModel()
        val shown = slot<ExternalAccountActions.Payload>()
        every { external.showExternalActions(capture(shown)) } returns Unit

        waitUntil {
            if (!shown.isCaptured) vm.originAccountClicked()
            shown.isCaptured
        }

        assertEquals(stash.controllerAddress, shown.captured.value)
        assertTrue(shown.captured.explorers.isEmpty())
    }

    @Test
    fun backAndRetryCancellationGoBack() {
        val vm = viewModel()

        vm.backClicked()
        retryCancelled.captured.invoke()

        verify(exactly = 2) { router.back() }
    }
}
