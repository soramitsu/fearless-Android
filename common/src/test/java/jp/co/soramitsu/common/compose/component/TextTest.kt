package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.white
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun plainStringStylesRenderTheirText() {
        var laidOut = false
        compose.setContent {
            FearlessAppTheme {
                Column {
                    B0(text = "b0 text")
                    B1(text = "b1 text", fontWeight = FontWeight.Bold, onTextLayout = { laidOut = true })
                    B2(text = "b2 text", textAlign = TextAlign.End, color = white)
                    B3(text = "b3 text", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    H1(text = "h1 text")
                    H2(text = "h2 text")
                    H3(text = "h3 text")
                    H4(text = "h4 text")
                    H5(text = "h5 text")
                    H6(text = "h6 text")
                    B1EllipsizeMiddle(text = "middle text", textAlign = TextAlign.Center, color = white)
                }
            }
        }

        listOf("b0 text", "b1 text", "b2 text", "b3 text", "h1 text", "h2 text", "h3 text", "h4 text", "h5 text", "h6 text")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onAllNodesWithText("middle text", substring = true, useUnmergedTree = true).fetchSemanticsNodes().let {
            assertTrue("middle-ellipsis text is shown", it.isNotEmpty())
        }
        assertTrue("B1 reports its text layout", laidOut)
    }

    @Test
    fun annotatedStylesRenderTheirText() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    B1(text = AnnotatedString("annotated b1"))
                    B2(text = AnnotatedString("annotated b2"))
                    H2(text = AnnotatedString("annotated h2"))
                    H3(text = AnnotatedString("annotated h3"))
                    H4(text = AnnotatedString("annotated h4"))
                    H5(text = AnnotatedString("annotated h5"))
                    H6(text = AnnotatedString("annotated h6"))
                }
            }
        }

        listOf("b1", "b2", "h2", "h3", "h4", "h5", "h6").forEach {
            compose.onNodeWithText("annotated $it").assertIsDisplayed()
        }
    }

    @Test
    fun capsTitlesUpperCaseTheirText() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    CapsTitle(text = "caps one")
                    CapsTitle2(text = "caps two", textAlign = TextAlign.Start, maxLines = 1)
                    CapsTitle(text = "Caps One")
                }
            }
        }

        compose.onAllNodesWithText("CAPS ONE").assertCountEquals(2)
        compose.onNodeWithText("CAPS TWO").assertIsDisplayed()
        compose.onAllNodesWithText("caps two").assertCountEquals(0)
    }
}
