package jp.co.soramitsu.polkaswap.impl.presentation.swap_preview

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsParcelModel
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.math.BigInteger
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class SwapPreviewContentTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val callbacks = mockk<SwapPreviewCallbacks>(relaxed = true)
    private val icon = GradientIconState.Local(R.drawable.ic_fearless_logo)
    private val details = SwapDetailsViewState(
        "0x01", "0x02", "XOR", "VAL", icon, icon, "2 XOR", "7.5 VAL", "7 VAL", "$14", "3.75", "0.26",
        "Minimum received", "XOR ➝ VAL"
    )
    private val fee = SwapDetailsViewState.NetworkFee("XOR", "0.01 XOR", "$0.02")

    @Before fun setUp() {
        TransferSeal.clear()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @After fun clearSeal() { TransferSeal.clear() }

    @Test fun previewSummarisesTheSwapAndConfirmsIt() {
        compose.setContent { FearlessAppTheme { SwapPreviewContent(SwapPreviewState(details, fee, isLoading = false), callbacks) } }

        compose.onNodeWithText(context.getString(R.string.polkaswap_preview_title)).assertIsDisplayed()
        compose.onNodeWithText("2 XOR").assertIsDisplayed()
        compose.onNodeWithText("7.5 VAL").assertIsDisplayed()
        listOf("Minimum received", "7 VAL", "$14", context.getString(R.string.common_route), "XOR ➝ VAL", "XOR / VAL", "3.75",
            "VAL / XOR", "0.26", context.getString(R.string.common_network_fee), "0.01 XOR", "$0.02").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }

        compose.onNodeWithContentDescription(context.getString(R.string.ux_back)).performClick()
        compose.onNodeWithText(context.getString(R.string.common_confirm)).performClick()

        verify(exactly = 1) { callbacks.onBackClick(); callbacks.onConfirmClick() }
    }

    @Test fun submittingHidesTheSummaryBehindTheLoader() {
        // The loader animates forever: frames are driven by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent { FearlessAppTheme { SwapPreviewContent(SwapPreviewState(details, fee, isLoading = true), callbacks) } }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText(context.getString(R.string.polkaswap_preview_title)).assertExists()
        compose.onNodeWithText("Minimum received").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.common_confirm)).assertDoesNotExist()
        verify(exactly = 0) { callbacks.onConfirmClick() }
    }

    @Suppress("DEPRECATION")
    @Test fun fragmentBundleCarriesTheQuoteAndItsExactAmounts() {
        val parcel = SwapDetailsParcelModel(BigInteger("200"), Market.XYK, WithDesired.INPUT, 1, BigInteger("190"), fee)

        val bundle = SwapPreviewFragment.getBundle(details, parcel)

        assertSame(details, bundle.get(SwapPreviewFragment.KEY_SWAP_DETAILS))
        assertSame(parcel, bundle.get(SwapPreviewFragment.KEY_SWAP_DETAILS_PARCEL))
    }

    @Test fun previewFixtureRendersTheRouteAndFee() {
        compose.setContent { FearlessAppTheme { SwapPreviewContentPreview() } }

        compose.onNodeWithText("USD -> XOR -> DOT -> VAL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0.0007").performScrollTo().assertIsDisplayed()
    }
}
