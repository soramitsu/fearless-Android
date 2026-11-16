package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.TextView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import jp.co.soramitsu.common.PLAY_MARKET_APP_URI
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.assetRow
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/** The asset search page renders its results, offers the app update for unsupported networks and opens the store. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h2000dp")
class SearchAssetsFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val unsupportedChain = MutableLiveData<Event<Unit>>()
    private val playMarket = MutableLiveData<Event<Unit>>()
    private val viewModel = mockk<SearchAssetsViewModel>(relaxed = true)
    private var host: ActivityController<StubHiltActivity>? = null

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        every { viewModel.state } returns MutableStateFlow(
            SearchAssetState(
                assets = AssetsLoadingState.Loaded(listOf(assetRow(symbol = "dot", chainAssetId = "dot"))),
                searchQuery = "d"
            )
        )
        every { viewModel.showUnsupportedChainAlert } returns unsupportedChain
        every { viewModel.openPlayMarket } returns playMarket
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
        unmockkObject(FwRestyle)
    }

    @Test
    fun resultsRenderAndCancelGoesBack() {
        host = hostSearch()

        compose.onNodeWithTag(PORTFOLIO_SECTION).assertExists()
        compose.onNodeWithText(string(R.string.common_cancel)).performClick()
        settle()

        verify { viewModel.backClicked() }
    }

    @Test
    fun unsupportedNetworkAlertOffersTheUpdate() {
        host = hostSearch()

        unsupportedChain.value = Event(Unit)
        ShadowLooper.idleMainLooper()
        val alert = ShadowDialog.getLatestDialog() as AlertBottomSheet

        assertEquals(
            string(R.string.update_needed_text),
            alert.findViewById<TextView>(jp.co.soramitsu.common.R.id.alertTitle)!!.text.toString()
        )
        alert.findViewById<View>(jp.co.soramitsu.common.R.id.alertConfirmButton)!!.performClick()
        ShadowLooper.idleMainLooper()

        verify { viewModel.updateAppClicked() }
        assertFalse(alert.isShowing)
    }

    @Test
    fun playMarketEventOpensTheStoreListing() {
        host = hostSearch()

        playMarket.value = Event(Unit)
        ShadowLooper.idleMainLooper()
        val intent = shadowOf(host!!.get()).nextStartedActivity

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse(PLAY_MARKET_APP_URI), intent.data)
    }

    @Test
    fun legacyPaletteDrawsThePageImageBehindTheResults() {
        mockkObject(FwRestyle)
        every { FwRestyle.paletteEnabled } returns false
        every { FwRestyle.isEnabled(any()) } returns false

        host = hostSearch()

        compose.onNodeWithTag(PORTFOLIO_SECTION).assertExists()
        compose.onNodeWithText(string(R.string.common_cancel)).assertExists()
    }

    private fun hostSearch(): ActivityController<StubHiltActivity> {
        val controller = HiltFragmentHost.host(SearchAssetsFragment(), mapOf(SearchAssetsViewModel::class.java to viewModel))
        settle()
        return controller
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private companion object {
        const val PORTFOLIO_SECTION = "SearchAssetsSection_portfolio"
    }
}
