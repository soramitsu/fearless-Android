package jp.co.soramitsu.wallet.impl.presentation.balance.detail.legacy

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import jp.co.soramitsu.common.compose.component.ChainSelectorViewState
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The legacy crowdloan page shows its evidence under the network toolbar and opens the locked balance. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h1600dp")
class LegacyCrowdloanFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private var host: ActivityController<StubHiltActivity>? = null
    private var restyleMocked = false
    private lateinit var viewModel: LegacyCrowdloanViewModel

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
        viewModel = mockk(relaxed = true) {
            every { state } returns MutableStateFlow<LoadingState<LegacyCrowdloanScreenState>>(
                LoadingState.Loaded(
                    LegacyCrowdloanScreenState(
                        hasEvidence = true,
                        description = "Your contribution is locked",
                        evidenceRows = listOf(TitleValueViewState("Parachain", "Acala")),
                        canOpenLockedBalance = true
                    )
                )
            )
            every { toolbarState } returns MutableStateFlow(
                MainToolbarViewState(
                    title = "Legacy crowdloan",
                    homeIconState = ToolbarHomeIconState.Navigation(jp.co.soramitsu.common.R.drawable.ic_arrow_back_24dp),
                    selectorViewState = ChainSelectorViewState(selectedChainName = "Polkadot", selectedChainId = "polkadot")
                )
            )
        }
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
        if (restyleMocked) unmockkObject(FwRestyle)
    }

    @Test
    fun pageShowsTheEvidenceAndOpensTheLockedBalance() {
        hostLegacyCrowdloan()

        compose.onNodeWithText("Legacy crowdloan").assertExists()
        compose.onNodeWithText("Your contribution is locked").assertExists()
        compose.onNodeWithText("Acala").assertExists()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.legacy_crowdloan_view_locked)).performClick()
        compose.waitForIdle()

        verify { viewModel.openLockedBalance() }
    }

    @Test
    fun toolbarBackReachesTheViewModel() {
        hostLegacyCrowdloan()

        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithContentDescription(string(jp.co.soramitsu.common.R.string.ux_back)).performClick()
        compose.waitForIdle()

        verify { viewModel.backClicked() }
    }

    @Test
    fun legacyPaletteDrawsThePageImageBehindTheEvidence() {
        mockkObject(FwRestyle)
        restyleMocked = true
        every { FwRestyle.paletteEnabled } returns false

        hostLegacyCrowdloan()

        compose.onNodeWithText("Your contribution is locked").assertExists()
        compose.onNodeWithText("Legacy crowdloan").assertExists()
    }

    @Test
    @Suppress("DEPRECATION")
    fun bundleCarriesTheAsset() {
        val payload = AssetPayload("polkadot", "dot")

        assertEquals(payload, LegacyCrowdloanFragment.getBundle(payload).get(LegacyCrowdloanFragment.KEY_ASSET_PAYLOAD))
    }

    private fun hostLegacyCrowdloan() {
        host = HiltFragmentHost.host(LegacyCrowdloanFragment(), mapOf(LegacyCrowdloanViewModel::class.java to viewModel))
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }
}
