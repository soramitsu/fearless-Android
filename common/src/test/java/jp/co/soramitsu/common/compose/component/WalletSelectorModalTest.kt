package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class WalletSelectorModalTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun wallet(id: Long, title: String) = WalletItemViewState(
        id = id,
        balance = "$id.00",
        assetSymbol = "$",
        title = title,
        walletIcon = R.drawable.ic_wallet,
        isSelected = false
    )

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun chooserMarksTheSelectedWalletAndReportsPicksAndBack() {
        val first = wallet(1, "First wallet")
        val second = wallet(2, "Second wallet")
        val picked = mutableListOf<WalletItemViewState>()
        var backs = 0
        compose.setContent {
            FearlessAppTheme {
                WalletSelectorScreen(
                    state = WalletSelectorViewState(wallets = listOf(first, second), selectedWallet = second),
                    onWalletSelected = { picked += it },
                    onBackClicked = { backs++ }
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.common_title_select_wallet)).assertIsDisplayed()
        compose.onNodeWithText("First wallet").assert(isSelected().not())
        compose.onNodeWithText("Second wallet").assertIsSelected()

        compose.onNodeWithText("First wallet").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_back)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(1L), picked.map { it.id })
        assertEquals(false, picked.single().isSelected)
        assertEquals(1, backs)
    }
}
