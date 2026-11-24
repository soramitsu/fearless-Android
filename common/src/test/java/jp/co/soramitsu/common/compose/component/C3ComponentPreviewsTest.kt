package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Method
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The design previews of these components are private; each one is rendered as Android Studio would and must show
 * the sample content it was written for (a broken preview fails here instead of only in the IDE).
 */
@RunWith(RobolectricTestRunner::class)
class C3ComponentPreviewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun infoTableItemPreviewShowsEveryRowVariant() {
        preview("InfoTableItemKt", "InfoTableItemPreview")

        assertEquals(6, compose.onAllNodesWithText("From", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("Account for Join", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun infoTablePreviewShowsBothTables() {
        preview("InfoTableKt", "InfoTableItemPreview")

        compose.onNodeWithText("nominator", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(3, compose.onAllNodesWithText("Account for Join", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun infoItemPreviewsShowTheirTitles() {
        preview("InfoItemKt", "InfoItemPreview")

        compose.onNodeWithText("Info item title", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun infoItemSetPreviewShowsItsItems() {
        preview("InfoItemSetKt", "InfoItemSetPreview")

        compose.onNodeWithText("Some required chain name", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Methods", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Events", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun titleValuePreviewShowsValuesAndPlaceholders() {
        preview("TitleValueKt", "TitleValuePreview")

        assertEquals(2, compose.onAllNodesWithText("Total staked", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("2.5M KSM", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1,43% monthly", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun iconPreviewsTakeTheirSizes() {
        preview("GradientIconStateKt", "GradientIconPreview")

        compose.onNodeWithTag(TAG).assertHeightIsEqualTo(90.dp)
    }

    @Test
    fun haloIconPreviewStacksTwoHalos() {
        preview("HaloIconKt", "HaloIconPreview")

        // A 178 dp halo above a 149 dp box holding the second one.
        compose.onNodeWithTag(TAG).assertHeightIsEqualTo(327.dp)
    }

    @Test
    fun warningPreviewShowsItsExpandedDetails() {
        preview("WarningInfoKt", "WarningInfoPreview")

        compose.onNodeWithText("Name:", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Scam address name", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Phishing", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun attentionPreviewShowsTheVestingDisclaimer() {
        preview("AttentionMessageKt", "AttentionMessagePreview")

        compose.onNodeWithText(context.getString(R.string.vesting_claim_disclaimer_text), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun notificationPreviewShowsBothNotifications() {
        preview("NotificationKt", "Preview")

        assertEquals(2, compose.onAllNodesWithText("0.49191 KSM", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithText(context.getString(R.string.staking_unbond_v1_9_0), useUnmergedTree = true).assertExists()
    }

    @Test
    fun networkIssuesPreviewShowsTheBadge() {
        preview("NetworkIssuesBadgeKt", "NetworkIssuesBadgePreview")

        compose.onNodeWithText(context.getString(R.string.network_issue_stub)).assertIsDisplayed()
    }

    @Test
    fun alertSheetPreviewShowsTheUnavailableNetwork() {
        preview("AlertSheetKt", "NetworkUnavailableScreenPreview")

        compose.onNodeWithText(context.getString(R.string.network_issue_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.top_up)).assertIsDisplayed()
    }

    @Test
    fun shimmerPreviewStacksTwoPlaceholders() {
        preview("ShimmersKt", "ShimmerPreview")

        // 16 dp padding, 17 dp B0, 16 dp margin, 13 dp B2, 16 dp padding.
        compose.onNodeWithTag(TAG).assertHeightIsEqualTo(78.dp)
    }

    @Test
    fun actionPreviewsShowTheirActions() {
        preview("ActionBarKt", "ActionBarPreview")

        assertEquals(2, compose.onAllNodesWithText(context.getString(R.string.common_action_send)).fetchSemanticsNodes().size)
        assertEquals(2, compose.onAllNodesWithText(context.getString(R.string.common_action_receive)).fetchSemanticsNodes().size)
    }

    @Test
    fun actionCellPreviewShowsEnabledAndDisabledCells() {
        preview("ActionCellKt", "ActionSellPreview")

        assertEquals(2, compose.onAllNodesWithText("Send", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun validatorsVariantPreviewShowsBothPanels() {
        preview("SelectValidatorsVariantPanelKt", "SelectValidatorsVariantPanelPreview")

        compose.onNodeWithText("Select suggested").assertExists()
        compose.onNodeWithText("Select manual").assertExists()
        compose.onNodeWithText("Most profitable", useUnmergedTree = true).assertExists()
    }

    @Test
    fun loadingPreviewsShowTheLoader() {
        preview("FullScreenLoadingKt", "FullScreenLoadingPreview")

        compose.onNodeWithText(context.getString(R.string.fw_loading_in_progress)).assertIsDisplayed()
    }

    @Test
    fun progressPreviewShowsTheLoader() {
        preview("FearlessProgressKt", "FearlessProgressPreview")

        compose.onNodeWithText(context.getString(R.string.fw_loading_in_progress)).assertIsDisplayed()
    }



    private fun preview(fileClass: String, name: String) {
        val method: Method = Class.forName("jp.co.soramitsu.common.compose.component.$fileClass")
            .getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        compose.setContent {
            // Calm keeps loaders and shimmers still: previews wrap themselves in the app theme, which keeps only calm.
            CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, highContrast = false, reduceMotion = true, calm = true)) {
                FearlessAppTheme {
                    Box(Modifier.testTag(TAG)) {
                        val composer = currentComposer
                        remember(method) { method }.invoke(null, composer, 0)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val TAG = "preview"
    }
}
