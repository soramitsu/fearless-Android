package jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.view

import android.animation.LayoutTransition
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MnemonicContainerViewTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Soramitsu_Fearless)

    private fun container(width: Int = CONTAINER_WIDTH): MnemonicContainerView {
        val attrs = Robolectric.buildAttributeSet().addAttribute(android.R.attr.animateLayoutChanges, "true").build()
        return MnemonicContainerView(context, attrs).apply {
            measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST))
            layout(0, 0, width, 2000)
        }
    }

    private fun word(text: String) = MnemonicWordView(context).apply {
        setWord(text)
        measure(View.MeasureSpec.makeMeasureSpec(WORD_WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(WORD_HEIGHT, View.MeasureSpec.EXACTLY))
    }

    private fun MnemonicContainerView.relayout() {
        measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST))
        layout(0, 0, width, measuredHeight)
    }

    @Test
    fun `words fill a line and wrap to the next one, then lines are centred`() {
        val view = container()
        val words = WORDS.map(::word)
        view.populateWithMnemonic(words)

        assertEquals(WORDS.size, view.childCount)
        val tops = words.map { (it.layoutParams as android.widget.FrameLayout.LayoutParams).topMargin }
        assertEquals(tops[0], tops[1])
        assertTrue("the fourth word starts a new line", tops[3] > tops[2])
        assertTrue(view.getMinimumMeasuredHeight() >= tops.last() + WORD_HEIGHT)

        view.relayout()
        val firstLeft = words[0].left
        assertTrue("a short line is centred", firstLeft > 0)
        assertTrue(words[1].left > words[0].left)
        assertTrue(words[3].left > 0)
        assertTrue(words[4].left > words[3].left)
        assertTrue(words[3].left < words[2].left)
    }

    @Test
    fun `removed words come back last first or all together`() {
        val view = container()
        val words = WORDS.take(4).map(::word)
        view.populateWithMnemonic(words)

        view.removeWordView(words[1])
        view.removeWordView(words[2])
        assertEquals(2, view.childCount)
        view.restoreLastWord()
        assertEquals(words[2], view.getChildAt(view.childCount - 1))
        view.removeWordView(words[2])
        view.restoreAllWords()
        assertEquals(4, view.childCount)
        view.restoreAllWords()
        view.restoreLastWord()
        assertEquals(4, view.childCount)
    }

    @Test
    fun `remove last word and reset empty the container`() {
        val view = container()
        view.removeLastWord()
        view.populateWithMnemonic(WORDS.take(3).map(::word))
        view.removeLastWord()
        assertEquals(2, view.childCount)

        view.resetView()
        assertEquals(0, view.childCount)
        view.relayout()
        view.populateWithMnemonic(emptyList())
        assertEquals(0, view.getMinimumMeasuredHeight())
    }

    @Test
    fun `the sun-lit container is an inset surface whose words appear without moving`() {
        val view = container()
        assertNotNull(view.background)
        assertFalse(view.layoutTransition.isTransitionTypeEnabled(LayoutTransition.APPEARING))
        view.disableWordDisappearAnimation()
        assertFalse(view.layoutTransition.isTransitionTypeEnabled(LayoutTransition.DISAPPEARING))
    }

    @Test
    fun `a word view keeps its text and gets a chip or a well`() {
        val light = word("alpha").apply { setColorMode(MnemonicWordView.ColorMode.LIGHT) }
        val dark = word("beta").apply { setColorMode(MnemonicWordView.ColorMode.DARK) }

        assertEquals("alpha", light.getWord())
        assertEquals("beta", dark.getWord())
        assertNotNull(light.findViewById<TextView>(R.id.wordTv).background)
        assertTrue(light.findViewById<TextView>(R.id.wordTv).isDuplicateParentStateEnabled)
    }

    @Test
    fun `xml attributes set the word and its mode`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.wordText, "gamma")
            .addAttribute(R.attr.colorMode, "dark")
            .build()
        val view = MnemonicWordView(context, attrs)

        assertEquals("gamma", view.getWord())
        assertNotNull(view.findViewById<TextView>(R.id.wordTv).background)
    }

    private companion object {
        const val CONTAINER_WIDTH = 400
        const val WORD_WIDTH = 100
        const val WORD_HEIGHT = 40
        val WORDS = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
    }
}
