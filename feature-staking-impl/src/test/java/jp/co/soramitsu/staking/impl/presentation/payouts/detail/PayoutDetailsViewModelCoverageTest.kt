package jp.co.soramitsu.staking.impl.presentation.payouts.detail

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.SoraStakingRewardsScenario
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.staking.impl.presentation.payouts.model.PendingPayoutParcelable
import jp.co.soramitsu.wallet.impl.domain.model.Asset
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
import org.junit.Assert.assertNotNull
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
class PayoutDetailsViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<PayoutDetailsViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val fiatToken = StakingLaneFixtures.token(chainAsset, BigDecimal("2"))
    private val validatorAddress = chain.addressOf(ByteArray(32) { 3 })
    private val payout = PendingPayoutParcelable(
        validatorInfo = PendingPayoutParcelable.ValidatorInfoParcelable(validatorAddress, "Validator"),
        era = BigInteger.valueOf(42),
        amountInPlanks = StakingLaneFixtures.planks(2),
        createdAt = 1_000L,
        timeLeft = 0L,
        closeToExpire = false
    )

    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val router = mockk<StakingRouter>(relaxed = true)
    private val icons = mockk<AddressIconGenerator>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>(relaxed = true)
    private val external = mockk<ExternalAccountActions.Presentation>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val sora = mockk<SoraStakingRewardsScenario>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers {
            "f${firstArg<Int>()}:${secondArg<Array<Any>>().joinToString()}"
        }
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

    private fun viewModel(asset: Asset) = PayoutDetailsViewModel(
        interactor = interactor.also { every { it.currentAssetFlow() } returns flowOf(asset) },
        router = router,
        addressModelGenerator = icons,
        chainRegistry = chainRegistry,
        externalAccountActions = external,
        resourceManager = resources,
        savedStateHandle = SavedStateHandle(mapOf(PayoutDetailsFragment.KEY_PAYOUT to payout)),
        soraRewardScenario = sora
    ).also(owned::add)

    private fun PayoutDetailsViewModel.awaitDetails(): PayoutDetailsModel {
        waitUntil { payoutDetails.value != null }
        return requireNotNull(payoutDetails.value)
    }

    @Test
    fun detailsShowTheValidatorEraRewardAndFiat() {
        val asset = StakingLaneFixtures.asset(chainAsset).copy(token = fiatToken)
        val details = viewModel(asset).awaitDetails()

        assertEquals(validatorAddress, details.validatorAddressModel.address)
        assertEquals("Validator", details.validatorAddressModel.name)
        assertEquals(1_000L, details.createdAt)
        assertEquals("f${R.string.staking_era_index_no_prefix}:42", details.eraDisplay)
        val reward = fiatToken.amountFromPlanks(StakingLaneFixtures.planks(2))
        assertEquals(reward.formatCryptoDetail("DOT"), details.reward)
        val fiat = fiatToken.fiatAmount(reward)?.formatFiat(fiatToken.fiatSymbol)
        assertNotNull(fiat)
        assertEquals(fiat, details.rewardFiat)
    }

    @Test
    fun withoutAPriceTheFiatRewardIsAbsent() {
        val details = viewModel(StakingLaneFixtures.asset(chainAsset)).awaitDetails()

        assertNull(details.rewardFiat)
    }

    @Test
    fun soraRewardsAreShownInTheRewardAsset() {
        val soraAsset = StakingLaneFixtures.chainAsset(chainId = soraMainChainId, symbol = "XOR", precision = 10)
        val rewardAsset = StakingLaneFixtures.chainAsset(chainId = soraMainChainId, symbol = "VAL", precision = 10)
        coEvery { sora.getRewardAsset() } returns StakingLaneFixtures.token(rewardAsset)

        val details = viewModel(StakingLaneFixtures.asset(soraAsset)).awaitDetails()

        assertTrue(details.reward, details.reward.contains("VAL"))
    }

    @Test
    fun payoutOpensTheConfirmationWithThisPayout() {
        val vm = viewModel(StakingLaneFixtures.asset(chainAsset))
        val payload = slot<ConfirmPayoutPayload>()
        every { router.openConfirmPayout(capture(payload)) } returns Unit

        vm.payoutClicked()

        assertSame(payout, payload.captured.payouts.single())
        assertEquals(StakingLaneFixtures.planks(2), payload.captured.totalRewardInPlanks)
    }

    @Test
    fun validatorActionsOpenTheExternalActions() {
        val vm = viewModel(StakingLaneFixtures.asset(chainAsset))
        val payload = slot<ExternalAccountActions.Payload>()
        every { external.showExternalActions(capture(payload)) } returns Unit

        vm.validatorExternalActionClicked()

        verify(timeout = 5_000) { external.showExternalActions(any()) }
        assertEquals(validatorAddress, payload.captured.value)
        assertTrue(payload.captured.explorers.isEmpty())
    }

    @Test
    fun backGoesBack() {
        viewModel(StakingLaneFixtures.asset(chainAsset)).backClicked()

        verify { router.back() }
    }
}
