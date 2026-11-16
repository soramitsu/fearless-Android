package jp.co.soramitsu.polkaswap.impl.presentation.swap_preview

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsParcelModel
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.polkaswap.impl.FragmentHiltShim
import jp.co.soramitsu.polkaswap.impl.ShimApplication
import jp.co.soramitsu.polkaswap.impl.idleMainUntil
import jp.co.soramitsu.polkaswap.impl.launchHost
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigInteger

@RunWith(RobolectricTestRunner::class)
@Config(application = ShimApplication::class)
class SwapPreviewFragmentTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val icon = GradientIconState.Local(R.drawable.ic_fearless_logo)
    private val details = SwapDetailsViewState(
        "0x01", "0x02", "XOR", "VAL", icon, icon, "2 XOR", "7.5 VAL", "7 VAL", "$14", "3.75", "0.26", "Minimum received", "XOR ➝ VAL"
    )
    private val parcel = SwapDetailsParcelModel(
        BigInteger.TEN, Market.SMART, WithDesired.INPUT, 0, BigInteger.ONE, SwapDetailsViewState.NetworkFee("XOR", "0.01 XOR", null)
    )

    @After fun tearDown() {
        FragmentHiltShim.reset()
        TransferSeal.clear()
    }

    @Test fun previewShowsTheQuoteFromItsArgumentsAndBackCancels() {
        FragmentHiltShim.viewModel(SwapPreviewViewModel::class) { handle -> SwapPreviewViewModel(router, mockk(), handle) }
        val host = launchHost()
        val fragment = SwapPreviewFragment().apply { arguments = SwapPreviewFragment.getBundle(details, parcel) }
        host.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        idleMainUntil { fragment.view != null }

        compose.onNodeWithText("7.5 VAL").assertExists()
        compose.onNodeWithText(context.getString(R.string.common_confirm)).assertExists()

        host.onBackPressedDispatcher.onBackPressed()

        verify(exactly = 1) { router.backWithResult(SwapPreviewFragment.KEY_SWAP_DETAILS_RESULT to Activity.RESULT_CANCELED) }
        assertTrue(fragment.viewModel.isClosing)
    }
}
