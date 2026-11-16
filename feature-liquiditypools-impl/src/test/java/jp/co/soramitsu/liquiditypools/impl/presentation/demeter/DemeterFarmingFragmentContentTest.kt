package jp.co.soramitsu.liquiditypools.impl.presentation.demeter

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.pswap
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.walletAsset
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.math.BigDecimal
import java.time.Duration

/** The fragment's composable content with a stand-in view model; the Hilt lifecycle around it is not exercised. */
@RunWith(RobolectricTestRunner::class)
class DemeterFarmingFragmentContentTest {
    @get:Rule val compose = createComposeRule()

    private val farm = DemeterFarmingBasicPool(walletAsset(xor), walletAsset(valToken), walletAsset(pswap), 5.0, BigDecimal("10"), 0.5)
    private val position = DemeterFarmingPool(walletAsset(xor), walletAsset(valToken), walletAsset(pswap), 40.0, BigDecimal("10"), BigDecimal("1"))
    private val state = MutableStateFlow(DemeterFarmingState(loading = false, positions = listOf(position), availablePools = listOf(farm)))
    private val stubViewModel = mockk<DemeterFarmingViewModel>(relaxed = true) {
        every { state } returns this@DemeterFarmingFragmentContentTest.state
    }

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    @OptIn(ExperimentalMaterialApi::class)
    private fun show() {
        val fragment = DemeterFarmingFragment().apply {
            DemeterFarmingFragment::class.java.getDeclaredField("viewModel\$delegate").also { it.isAccessible = true }
                .set(this, lazyOf(stubViewModel))
        }
        compose.setContent {
            FearlessAppTheme {
                fragment.Content(PaddingValues(), rememberScrollState(), rememberModalBottomSheetState(ModalBottomSheetValue.Hidden))
            }
        }
    }

    @Test
    fun positionsAndFarmsShowTheirTermsAndSubmitTheEnteredAmounts() {
        show()

        compose.onNodeWithText("APR 40.00% · Deposited 10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rewards 1 PSWAP").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("APR 5.00% · TVL 10 · Deposit fee 0.50%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Amount to withdraw").performScrollTo().performTextInput("1.5")
        compose.onNodeWithText("Withdraw").performScrollTo().performClick()
        verify { stubViewModel.withdraw(position, "1.5") }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Claim").performScrollTo().assertIsEnabled().performClick()
        verify { stubViewModel.claim(position) }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("LP amount to deposit").performScrollTo().performTextInput("2x")
        compose.onNodeWithText("LP amount to deposit").performTextInput("2")
        compose.onNodeWithText("Deposit").performScrollTo().performClick()
        verify { stubViewModel.deposit(farm, "2") }
    }

    @Test
    fun loadingDisablesRefreshAndHidesEmptyNotices() {
        state.value = DemeterFarmingState(loading = true)
        show()

        compose.onNodeWithText("Refreshing…").assertIsNotEnabled()
        compose.onNodeWithText("No active farming positions.").assertDoesNotExist()
        compose.onNodeWithText("Demeter farming").assertIsDisplayed()
    }

    @Test
    fun unavailableActionsExplainWhyAndRefreshStillWorks() {
        state.value = DemeterFarmingState(
            loading = false,
            depositCapabilityReason = "runtime missing deposit",
            withdrawCapabilityReason = "runtime missing withdraw",
            claimCapabilityReason = "runtime missing claim",
            message = "Transaction submitted: 0x1"
        )
        show()

        compose.onNodeWithText("Deposit unavailable: runtime missing deposit").assertIsDisplayed()
        compose.onNodeWithText("Withdraw unavailable: runtime missing withdraw").assertIsDisplayed()
        compose.onNodeWithText("Claim unavailable: runtime missing claim").assertIsDisplayed()
        compose.onNodeWithText("Transaction submitted: 0x1").assertIsDisplayed()
        compose.onNodeWithText("No active farming positions.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No active Demeter farms are available on SORA.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Refresh pools").performScrollTo().performClick()
        verify { stubViewModel.refresh(true) }
    }

    @Test
    fun aRunningActionOrMissingRewardsDisableTheButtons() {
        state.value = DemeterFarmingState(
            loading = false,
            positions = listOf(position.copy(amountReward = BigDecimal.ZERO)),
            availablePools = listOf(farm),
            actionKey = "deposit:x"
        )
        show()

        compose.onNodeWithText("Withdraw").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Claim").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Deposit").performScrollTo().assertIsNotEnabled()
    }
}
