package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class InfoItemTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun infoItemsShowTitleAndSubtitleWithAnyIconSource() {
        compose.show {
            Column {
                InfoItem(InfoItemViewState(title = "dApp", subtitle = "app.example", imageUrl = MISSING_SVG))
                InfoItem(InfoItemViewState(title = "Account", subtitle = "Alice", singleLine = true))
                InfoItem(InfoItemViewState.default.copy(title = "Placeholder", subtitle = "With icon"))
                InfoItem(InfoItemViewState(title = null, subtitle = null, imageUrl = " "))
            }
        }

        compose.onNodeWithText("dApp", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("app.example", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("ConnectionItem_image_dApp", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Account", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Alice", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Placeholder", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("With icon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun defaultInfoItemUsesTheConnectionPlaceholder() {
        assertNull(InfoItemViewState.default.title)
        assertNull(InfoItemViewState.default.subtitle)
        assertEquals(R.drawable.ic_dapp_connection, InfoItemViewState.default.placeholderIcon)
    }

    @Test
    fun infoItemSetListsItsItemsUnderAnOptionalTitle() {
        compose.show {
            Column {
                InfoItemSet(
                    InfoItemSetViewState(
                        title = "Polkadot",
                        infoItems = listOf(
                            InfoItemViewState(title = "Methods", subtitle = "eth_sendTransaction"),
                            InfoItemViewState(title = "Events", subtitle = "accountsChanged")
                        )
                    )
                )
                InfoItemSet(InfoItemSetViewState(title = null, infoItems = listOf(InfoItemViewState("Only", "item"))))
            }
        }

        listOf("Polkadot", "Methods", "eth_sendTransaction", "Events", "accountsChanged", "Only", "item").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun assetRowShowsItsValueWithOrWithoutAnIcon() {
        compose.show {
            Column {
                InfoTableItemAsset(TitleIconValueState(title = "Receive", iconUrl = MISSING_SVG, value = "PSWAP"))
                InfoTableItemAsset(TitleIconValueState(title = "Pay", value = "XOR"))
                InfoTableItemAsset(TitleIconValueState(title = "Empty"))
            }
        }

        listOf("Receive", "PSWAP", "Pay", "XOR", "Empty").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun validatorsVariantPanelShowsItsCopyExtrasAndButton() {
        var clicks = 0
        compose.show {
            Column {
                SelectValidatorsVariantPanel(
                    state = SelectValidatorsVariantPanelViewState(
                        title = "Suggested",
                        description = "Picked by the algorithm",
                        buttonText = "Select suggested",
                        additionalInfo = listOf("Most profitable", "Not slashed")
                    ),
                    AdditionalInfo = { RecommendedValidatorsAdditionalInfo(it) },
                    onButtonClick = { clicks++ }
                )
                SelectValidatorsVariantPanel(
                    state = SelectValidatorsVariantPanelViewState<Nothing>(
                        title = "Manual",
                        description = "Your own choice",
                        buttonText = "Select manual"
                    ),
                    onButtonClick = { clicks += 10 }
                )
            }
        }

        listOf("Suggested", "Picked by the algorithm", "Most profitable", "Not slashed", "Manual", "Your own choice").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
        compose.onNodeWithText("Select suggested").tap()
        compose.onNodeWithText("Select manual").tap()

        assertEquals(11, clicks)
    }

    private fun SemanticsNodeInteraction.tap() {
        performClick()
        compose.waitForIdle()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    private fun ComposeContentTestRule.show(content: @Composable () -> Unit) = setContent {
        FearlessAppTheme {
            CompositionLocalProvider(
                LocalFwEffects provides FwEffects(FwTier.Full, highContrast = false, reduceMotion = true),
                content = content
            )
        }
    }

    private companion object {
        const val MISSING_SVG = "file:///android_asset/missing-icon.svg"
    }
}
