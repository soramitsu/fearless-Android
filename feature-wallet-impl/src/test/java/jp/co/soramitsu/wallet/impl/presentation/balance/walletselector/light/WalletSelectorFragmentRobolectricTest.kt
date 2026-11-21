package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The light wallet picker reports the chosen wallet and Back to its view model. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h1600dp")
class WalletSelectorFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val viewModel = mockk<WalletSelectorViewModel>(relaxed = true)
    private var host: ActivityController<StubHiltActivity>? = null

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        val main = WalletItemViewState(id = 1L, balance = "10", assetSymbol = "$", title = "Main wallet", walletIcon = R.drawable.ic_wallet, isSelected = true)
        val savings = WalletItemViewState(id = 2L, balance = "3", assetSymbol = "$", title = "Savings", walletIcon = R.drawable.ic_wallet, isSelected = false)
        every { viewModel.state } returns MutableStateFlow(WalletSelectorViewState(listOf(main, savings), main))
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun pickerReportsTheChosenWalletAndBack() {
        val fragment = WalletSelectorFragment()
        fragment.arguments = WalletSelectorFragment.buildArguments("send")
        host = HiltFragmentHost.host(fragment, mapOf(WalletSelectorViewModel::class.java to viewModel))
        settle()

        compose.onNodeWithText(string(R.string.common_title_select_wallet)).assertExists()
        compose.onNodeWithText("Savings").performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithContentDescription(string(R.string.ux_back)).performClick()
        settle()

        verify { viewModel.onWalletSelected(match { it.id == 2L }) }
        verify { viewModel.onBackClicked() }
        assertTrue((fragment.dialog as BottomSheetDialog).behavior.skipCollapsed)
    }

    @Test
    fun argumentsCarryTheTagWalletAndMode() {
        val arguments = WalletSelectorFragment.buildArguments("send", 7L, WalletSelectionMode.ExternalSelectedWallet)

        assertEquals("send", arguments.getString(WalletSelectorFragment.TAG_ARGUMENT_KEY))
        assertEquals(7L, arguments.getLong(WalletSelectorFragment.SELECTED_WALLET_ID))
        @Suppress("DEPRECATION")
        val mode = arguments.get(WalletSelectorFragment.WALLET_SELECTION_MODE)
        assertEquals(WalletSelectionMode.ExternalSelectedWallet, mode)
    }

    @Test
    fun argumentsWithoutAWalletLeaveItAndTheModeEmpty() {
        val arguments = WalletSelectorFragment.buildArguments("receive")

        assertTrue(arguments.containsKey(WalletSelectorFragment.SELECTED_WALLET_ID))
        @Suppress("DEPRECATION")
        val wallet = arguments.get(WalletSelectorFragment.SELECTED_WALLET_ID)
        assertNull(wallet)
        @Suppress("DEPRECATION")
        val mode = arguments.get(WalletSelectorFragment.WALLET_SELECTION_MODE)
        assertNull(mode)
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }
}
