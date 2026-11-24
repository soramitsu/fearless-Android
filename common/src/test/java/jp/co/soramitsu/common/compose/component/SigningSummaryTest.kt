package jp.co.soramitsu.common.compose.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SigningSummaryTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun summaryShowsWarningsSectionTitlesAndEveryDetail() {
        val state = SigningSummaryViewState(
            headline = "Transfer · 2 messages",
            sections = listOf(
                SigningSummarySection(
                    title = "Message 1",
                    rows = listOf(
                        SigningDetailViewState("Recipient", "UQBvW8Z5huBkMJYdnfAEM5JqTNkuWX3diqYENkWsIL0XggGG"),
                        SigningDetailViewState("Amount", "1.5 TON")
                    )
                ),
                SigningSummarySection(
                    title = null,
                    rows = listOf(SigningDetailViewState("Payload", "Unknown contract call", isWarning = true))
                )
            ),
            warnings = listOf("This request could not be decoded in full."),
            showsRawData = true
        )
        compose.setContent { FearlessAppTheme { SigningSummary(state) } }

        compose.onNodeWithText("This request could not be decoded in full.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Message 1").assertIsDisplayed()
        compose.onNode(hasTestTag("signing_detail_Recipient") and hasAnyDescendant(hasText("UQBvW8Z5huBkMJYdnfAEM5JqTNkuWX3diqYENkWsIL0XggGG"))).assertExists()
        compose.onNode(hasTestTag("signing_detail_Amount") and hasAnyDescendant(hasText("1.5 TON"))).assertExists()
        compose.onNode(hasTestTag("signing_detail_Payload") and hasAnyDescendant(hasText("Unknown contract call"))).assertExists()
        assertFalse(state.blocksSigning)
    }

    @Test
    fun previewShowsTheTokenApproval() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.SigningSummaryKt", "SigningSummaryPreview") }
        }

        compose.onNode(hasTestTag("signing_detail_Spender") and hasAnyDescendant(hasText("0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45"))).assertExists()
        compose.onNode(hasTestTag("signing_detail_Amount") and hasAnyDescendant(hasText("Unlimited"))).assertExists()
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
