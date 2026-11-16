package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScoreStarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun scoresShowTheirNumberAndFailuresShowNotAvailable() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    ScoreStar(score = 10)
                    ScoreStar(score = 50)
                    ScoreStar(score = 90)
                    ScoreStar(score = 150)
                    ScoreStar(score = -2)
                    // Any other negative score draws nothing.
                    ScoreStar(score = -7, modifier = Modifier.testTag("unknown"))
                }
            }
        }

        listOf("10", "50", "90", "150", "N/A").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onAllNodesWithTag("unknown").assertCountEquals(0)
    }

    @Test
    fun loadingScoreShowsOnlyTheShimmeringStar() {
        // The loading star shimmers forever, so the clock is driven by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.ScoreStarKt", "ScoreStarPreview") }
        }
        compose.mainClock.advanceTimeBy(300)

        listOf("0", "50", "100", "N/A").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onAllNodesWithText("-1").assertCountEquals(0)
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
