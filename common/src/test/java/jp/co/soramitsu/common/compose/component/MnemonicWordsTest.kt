package jp.co.soramitsu.common.compose.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MnemonicWordsTest {

    @get:Rule
    val compose = createComposeRule()

    private val phrase = listOf("song", "toss", "odor", "click", "blouse", "lesson")

    @Test
    fun wordsAreNumberedFromOne() {
        val words = mapMnemonicToMnemonicWords(phrase)

        assertEquals(listOf("1", "2", "3", "4", "5", "6"), words.map { it.numberToShow })
        assertEquals(phrase, words.map { it.word })
    }

    @Test
    fun phraseIsShownInTwoColumns() {
        compose.setContent {
            FearlessAppTheme { MnemonicWords(mnemonicWords = mapMnemonicToMnemonicWords(phrase)) }
        }

        phrase.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("6").assertIsDisplayed()
        // The second half starts a new column to the right of the first.
        val first = compose.onNodeWithText("song").getUnclippedBoundsInRoot()
        val fourth = compose.onNodeWithText("click").getUnclippedBoundsInRoot()
        assertTrue(fourth.left > first.left)
        assertEquals(first.top, fourth.top)
    }

    @Test
    fun largeTextFoldsThePhraseIntoOneColumn() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                FearlessAppTheme { MnemonicWords(mnemonicWords = mapMnemonicToMnemonicWords(phrase)) }
            }
        }

        val first = compose.onNodeWithText("song").getUnclippedBoundsInRoot()
        compose.onNodeWithText("lesson").assertExists().assertLeftPositionInRootIsEqualTo(first.left)
    }

    @Test
    fun previewShowsTwelveWords() {
        compose.setContent { FearlessAppTheme { MnemonicWordsPreview() } }

        compose.onNodeWithText("poverty").assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
    }
}
