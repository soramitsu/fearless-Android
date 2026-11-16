package jp.co.soramitsu.common.compose.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MnemonicLegacyLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Before fun useLegacyMnemonic() {
        mockkObject(FwRestyle)
        every { FwRestyle.isEnabled(FwSurface.Mnemonic) } returns false
    }
    @After fun restoreFlags() { unmockkObject(FwRestyle) }

    @Test
    fun shouldSplitAnOddPhraseIntoBalancedColumnsWithoutLosingWords() {
        val words = mapMnemonicToMnemonicWords(listOf("alpha", "beta", "gamma", "delta", "epsilon"))
        compose.setContent { FearlessAppTheme { MnemonicWords(words) } }
        words.forEach { compose.onNodeWithText(it.word).assertIsDisplayed() }
        val first = compose.onNodeWithText("alpha").getUnclippedBoundsInRoot()
        val fourth = compose.onNodeWithText("delta").getUnclippedBoundsInRoot()
        assertTrue(fourth.left > first.left)
        assertEquals(first.top, fourth.top)
        assertTrue(compose.onNodeWithText("gamma").getUnclippedBoundsInRoot().top > first.top)
    }

    @Test
    fun shouldRenderTheLegacyPreviewWithAllTwelveNumbers() {
        compose.setContent { FearlessAppTheme { MnemonicWordsPreview() } }
        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithText("poverty").assertIsDisplayed()
        compose.onNodeWithText("song").assertIsDisplayed()
    }
}
