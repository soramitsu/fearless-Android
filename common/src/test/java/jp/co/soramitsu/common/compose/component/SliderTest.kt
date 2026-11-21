package jp.co.soramitsu.common.compose.component

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SliderTest {

    @get:Rule
    val compose = createComposeRule()

    private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun sliderReportsProgressSetByAccessibility() {
        val values = mutableListOf<Float>()
        compose.setContent {
            FearlessAppTheme {
                Slider(value = 2f, valueRange = 0f..10f, step = 1f, onValueChange = { values += it })
            }
        }

        val node = compose.onNode(slider)
        node.assert(SemanticsMatcher("starts at 2") { it.config[SemanticsProperties.ProgressBarRangeInfo].current == 2f })
        // The range end is always a stop, whatever the step count.
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        compose.waitForIdle()

        assertEquals(10f, values.last())
    }

    @Test
    fun previewSliderStartsAtZero() {
        compose.setContent { FearlessAppTheme { SliderPreview() } }

        compose.onNode(slider)
            .assert(SemanticsMatcher("at zero of one") {
                val info = it.config[SemanticsProperties.ProgressBarRangeInfo]
                info.current == 0f && info.range.endInclusive == 1f
            })
    }
}
