package jp.co.soramitsu.success.presentation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class SuccessContentTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calls = mutableListOf<String>()
    private val callback = object : SuccessScreenInterface {
        override fun onClose() { calls += "close" }
        override fun onItemClick(code: Int) { calls += "item:$code" }
        override fun onExplorerClick() { calls += "explorer" }
        override fun onShareClick() { calls += "share" }
    }
    private val state = SuccessViewState("Transfer sent", "Your transfer was accepted", emptyList(), null)

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    @Test fun successWithoutExplorerShowsMessageAndCloseAction() {
        compose.setContent { FearlessAppTheme { SuccessContent(state, callback) } }
        compose.onNodeWithText("Transfer sent").assertIsDisplayed()
        compose.onNodeWithText("Your transfer was accepted").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_close)).performScrollTo().performClick()
        assertEquals(listOf("close"), calls)
        compose.onNodeWithTag("success_solar_ignite").assertDoesNotExist()
    }

    @Test fun knownExplorerOffersIndependentExplorerAndShareActions() {
        compose.setContent { FearlessAppTheme { SuccessContent(state.copy(explorer = Chain.Explorer.Type.SUBSCAN to "https://example.test/tx"), callback) } }
        compose.onNodeWithText(Chain.Explorer.Type.SUBSCAN.capitalizedName).performScrollTo().performClick()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(context.getString(R.string.common_share)).performScrollTo().performClick()
        assertEquals(listOf("explorer", "share"), calls)
        compose.onNodeWithText(context.getString(R.string.common_close)).assertDoesNotExist()
    }

    @Test fun unknownExplorerUsesCloseAndInfoTableForwardsItemCode() {
        val item = TitleValueViewState("Hash", "0x123", clickState = TitleValueViewState.ClickState.Value(R.drawable.ic_copy_filled_24, 2))
        compose.setContent { FearlessAppTheme { SuccessContent(state.copy(tableItems = listOf(item), explorer = Chain.Explorer.Type.UNKNOWN to "url"), callback) } }
        compose.onNodeWithText("0x123").performScrollTo().assertIsDisplayed()
        compose.onNode(hasClickAction() and hasParent(hasText("0x123"))).performClick()
        assertEquals(listOf("item:2"), calls)
        compose.onNodeWithText(context.getString(R.string.common_close)).assertExists()
    }

    @Test fun acceptedSealDisplaysTheSuccessArtwork() {
        val seal = TransferSeal.Record(readyAtMs = 1, signedAtMs = 2, sentAtMs = 3, acceptedAtMs = 4)
        compose.setContent { FearlessAppTheme { SuccessContent(state, callback, seal = seal) } }
        compose.onNodeWithTag("success_solar_ignite").assertIsDisplayed()
        compose.onNodeWithText("Transfer sent").assertIsDisplayed()
    }

    @Test fun igniteDisplaysArtworkForSuccessWithoutASeal() {
        compose.setContent { FearlessAppTheme { SuccessContent(state, callback, ignite = true) } }
        compose.onNodeWithTag("success_solar_ignite").assertIsDisplayed()
        compose.onNodeWithText("Transfer sent").assertIsDisplayed()
    }
}
