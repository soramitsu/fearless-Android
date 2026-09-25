package jp.co.soramitsu.app.root.presentation.main

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.view.MnemonicContainerView
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.view.MnemonicWordView
import jp.co.soramitsu.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MnemonicConfirmationLayoutInstrumentedTest {
    @Test
    fun allWordsRemainReachableAboveFixedActionsAtNarrowWidthAndDoubleTextSize() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        for (scale in listOf(1f, 2f)) {
            ActivityScenario.launch<PolkaswapNavigationLayoutTestActivity>(
                Intent(target, PolkaswapNavigationLayoutTestActivity::class.java)
            ).use { scenario ->
                scenario.onActivity { activity ->
                    val configuration = Configuration(target.resources.configuration).apply { fontScale = scale }
                    val context = ContextThemeWrapper(target.createConfigurationContext(configuration), R.style.Theme_Soramitsu_Fearless)
                    val root = LayoutInflater.from(context).inflate(jp.co.soramitsu.feature_account_impl.R.layout.fragment_confirm_mnemonic, null)
                    activity.setContentView(root)
                    val density = context.resources.displayMetrics.density
                    val width = (320 * density).toInt()
                    val height = (640 * density).toInt()
                    val measureAndLayout = {
                        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, width, height)
                    }
                    val scroll = root.findViewById<ScrollView>(jp.co.soramitsu.feature_account_impl.R.id.mnemonicScroll)
                    val selected = root.findViewById<MnemonicContainerView>(jp.co.soramitsu.feature_account_impl.R.id.confirmationMnemonicView)
                    val source = root.findViewById<MnemonicContainerView>(jp.co.soramitsu.feature_account_impl.R.id.wordsMnemonicView)
                    val skip = root.findViewById<View>(jp.co.soramitsu.feature_account_impl.R.id.confirmMnemonicSkip)
                    val next = root.findViewById<View>(jp.co.soramitsu.feature_account_impl.R.id.nextBtn)
                    measureAndLayout()
                    val words = List(24) { index ->
                        MnemonicWordView(context).apply {
                            // Synthetic labels; no generated mnemonic is used or captured.
                            setWord("sample$index")
                            setOnClickListener { }
                            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                        }
                    }
                    source.populateWithMnemonic(words)
                    source.minimumHeight = source.getMinimumMeasuredHeight()
                    measureAndLayout()
                    assertTrue("Empty selection should leave source words in view", selected.height < scroll.height / 2)
                    assertTrue("Scroll viewport must have usable height", scroll.height > 48 * density)
                    assertTrue("Scrolling content must stop above Skip", scroll.bottom <= skip.top)
                    assertTrue("Actions must not overlap", skip.bottom <= next.top)
                    assertTrue("Continue must fit on screen", next.bottom <= height)
                    assertEquals(selected.parent, source.parent)
                    assertEquals(scroll, (selected.parent as View).parent)

                    for (word in words) {
                        assertTrue("Word target must be at least 48dp wide", word.width >= 48 * density)
                        assertTrue("Word target must be at least 48dp high", word.height >= 48 * density)
                        scroll.scrollTo(0, 0)
                        val bounds = Rect(0, 0, word.width, word.height)
                        scroll.offsetDescendantRectToMyCoords(word, bounds)
                        scroll.scrollTo(0, bounds.top)
                        val visible = Rect()
                        assertTrue("Every source word must be scrollable into view", word.getGlobalVisibleRect(visible))
                        assertEquals(word.height, visible.height())
                        assertTrue(word.performClick())
                    }

                    // Moving/removing/resetting words retains the existing container behavior.
                    for (word in words) {
                        source.removeWordView(word)
                        val copy = MnemonicWordView(context).apply {
                            setWord(word.getWord())
                            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                        }
                        selected.populateWord(copy)
                    }
                    measureAndLayout()
                    assertEquals(24, selected.childCount)
                    assertTrue("Full selection must be contained in scrolling content", scroll.bottom <= skip.top)
                    val last = selected.getChildAt(23)
                    scroll.scrollTo(0, 0)
                    val lastBounds = Rect(0, 0, last.width, last.height)
                    scroll.offsetDescendantRectToMyCoords(last, lastBounds)
                    scroll.scrollTo(0, lastBounds.top)
                    assertTrue(last.getGlobalVisibleRect(Rect()))
                    selected.removeLastWord()
                    source.restoreLastWord()
                    assertEquals(23, selected.childCount)
                    assertEquals(1, source.childCount)
                    selected.resetView()
                    source.restoreAllWords()
                    assertEquals(0, selected.childCount)
                    assertEquals(24, source.childCount)
                }
            }
        }
    }
}
