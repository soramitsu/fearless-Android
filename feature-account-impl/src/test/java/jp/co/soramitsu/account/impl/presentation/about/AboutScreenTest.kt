package jp.co.soramitsu.account.impl.presentation.about

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import jp.co.soramitsu.account.impl.presentation.about.model.AboutItemModel
import jp.co.soramitsu.account.impl.presentation.about.model.AboutSectionModel
import jp.co.soramitsu.account.impl.testutil.PrivateComposable
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
class AboutScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersSectionsAndItemsAndReportsItemClicks() {
        val clicked = mutableListOf<String>()
        val items = listOf(
            AboutSectionModel(R.string.community),
            AboutItemModel(
                iconResId = R.drawable.ic_about_telegram,
                titleResId = R.string.about_telegram,
                text = "https://t.me/fearless",
                onClick = { clicked += "telegram" }
            ),
            AboutItemModel(
                iconResId = R.drawable.ic_terms_conditions_24,
                titleResId = R.string.about_terms,
                showDivider = false,
                onClick = { clicked += "terms" }
            )
        )
        compose.setContent { FearlessAppTheme { AboutScreenContent(items = items, backClick = {}) } }

        compose.onNodeWithText(string(R.string.about_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.community), ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("https://t.me/fearless").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.about_telegram)).tap()
        compose.onNodeWithText(string(R.string.about_terms)).tap()
        compose.waitForIdle()

        assertEquals(listOf("telegram", "terms"), clicked)
    }

    @Test
    fun backArrowInTheTopCornerCallsBack() {
        var backs = 0
        compose.setContent { FearlessAppTheme { AboutScreenContent(items = emptyList(), backClick = { backs++ }) } }

        // The 44 dp back target sits 6 dp from the start edge and 30 dp from the top.
        val density = compose.density.density
        compose.onRoot().performTouchInput { click(Offset(28f * density, 52f * density)) }
        compose.waitForIdle()

        assertEquals(1, backs)
    }

    @Test
    fun fontLicencesButtonOpensAndClosesTheLicencesDialog() {
        compose.setContent { FearlessAppTheme { AboutScreenContent(items = emptyList(), backClick = {}) } }

        compose.onNodeWithText(string(jp.co.soramitsu.common.R.string.fw_font_licenses)).tap()
        compose.onNodeWithTag("fw-font-licenses-dialog").assertExists()

        compose.onNodeWithTag("fw-font-licenses-close").tap()
        compose.waitForIdle()
        compose.onNodeWithTag("fw-font-licenses-dialog").assertDoesNotExist()
    }

    @Test
    fun previewListsEveryAboutLink() {
        compose.setContent {
            FearlessAppTheme {
                PrivateComposable("jp.co.soramitsu.account.impl.presentation.about.AboutScreenKt", "PreviewAboutScreen")
            }
        }

        compose.onNodeWithText(string(R.string.about_website)).assertExists()
        compose.onNodeWithText(string(R.string.about_contact_us)).assertExists()
        compose.onNodeWithText(string(R.string.support_and_feedback), ignoreCase = true).assertExists()
    }

    @Test
    fun backgroundPreviewRendersThePageLight() {
        compose.setContent { FearlessAppTheme { PreviewAboutBackground() } }

        compose.onRoot().assertExists()
        assertEquals(0, compose.onRoot().fetchSemanticsNode().children.size)
    }
}
