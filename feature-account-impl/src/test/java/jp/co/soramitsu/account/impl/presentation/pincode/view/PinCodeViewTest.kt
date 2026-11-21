package jp.co.soramitsu.account.impl.presentation.pincode.view

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import java.util.concurrent.TimeUnit
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwNeuDrawable
import jp.co.soramitsu.common.compose.theme.fw.FwPinDotDrawable
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PinCodeViewTest {
    private lateinit var view: PinCodeView
    private val entered = mutableListOf<String>()
    private var fingerprintClicks = 0

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(activity, R.style.Theme_Soramitsu_Fearless)
        view = PinCodeView(context).apply {
            pinCodeEnteredListener = { entered += it }
            fingerprintClickListener = { fingerprintClicks++ }
        }
        activity.setContentView(view)
    }

    private fun tap(vararg digits: Int) = digits.forEach { digit ->
        view.findViewById<View>(DIGIT_IDS[digit]).performClick()
    }

    private fun dots() = view.findViewById<DotsProgressView>(R.id.dotsProgressView)

    private fun dotBackgrounds() = (0 until DotsProgressView.MAX_PROGRESS).map { dots().getChildAt(it).background }

    @Test
    fun `six digits are reported once and further digits are ignored until reset`() {
        tap(1, 2, 3, 4, 5)
        assertTrue(entered.isEmpty())
        tap(6)
        tap(7, 8)

        assertEquals(listOf("123456"), entered)
        view.resetInput()
        tap(9, 0, 9, 0, 9, 0)
        assertEquals(listOf("123456", "909090"), entered)
    }

    @Test
    fun `delete removes the last digit and does nothing on an empty input`() {
        view.findViewById<View>(R.id.btnDelete).performClick()
        tap(1, 2, 3)
        view.findViewById<View>(R.id.btnDelete).performClick()
        tap(4, 5, 6, 7)

        assertEquals(listOf("124567"), entered)
    }

    @Test
    fun `entered digits fill the matching number of dots`() {
        tap(1, 2)
        val backgrounds = dotBackgrounds()
        assertSame(backgrounds[0], backgrounds[1])
        assertSame(backgrounds[2], backgrounds[5])
        assertNotSame(backgrounds[0], backgrounds[2])

        dots().setProgress(DotsProgressView.MAX_PROGRESS + 1)
        assertTrue(dotBackgrounds().all { it === backgrounds[5] })
    }

    @Test
    fun `fingerprint button visibility and clicks, and the title text`() {
        view.changeFingerPrintButtonVisibility(false)
        assertEquals(View.INVISIBLE, view.findViewById<View>(R.id.fingerprintBtn).visibility)
        view.changeFingerPrintButtonVisibility(true)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.fingerprintBtn).visibility)
        view.findViewById<View>(R.id.fingerprintBtn).performClick()
        assertEquals(1, fingerprintClicks)

        view.setTitle("Enter PIN")
        assertEquals("Enter PIN", view.findViewById<TextView>(R.id.pinCodeTitleTv).text.toString())
    }

    @Test
    fun `a wrong pin clears the classic dots and shakes them`() {
        tap(1, 2, 3)
        view.pinCodeMatchingError()

        val backgrounds = dotBackgrounds()
        assertTrue(backgrounds.all { it === backgrounds[0] })
        assertNotNull(dots().animation)
        tap(4, 5, 6, 7, 8, 9)
        assertEquals(listOf("456789"), entered)
    }

    @Test
    fun `the sun-lit pad adds the lockup, raised keys and light dots, and a wrong pin flashes then clears`() {
        val before = view.childCount
        view.applySunlitStyle(FwEffects(tier = FwTier.Full, highContrast = false, reduceMotion = false), FwStandard)

        assertEquals(before + 1, view.childCount)
        assertTrue(view.findViewById<View>(R.id.btn5).background is FwNeuDrawable)
        assertEquals(null, view.findViewById<View>(R.id.btnDelete).background)
        val dots = dotBackgrounds().map { it as FwPinDotDrawable }

        tap(1, 2)
        view.pinCodeMatchingError()
        assertTrue(dots.all { it.error })
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        assertTrue(dots.none { it.error })
        tap(3, 4, 5, 6, 7, 8)
        assertEquals(listOf("345678"), entered)
    }

    @Test
    fun `under reduced motion the wrong pin waits without shaking before clearing`() {
        view.applySunlitStyle(FwEffects(tier = FwTier.Reduced, highContrast = false, reduceMotion = true), FwStandard)
        val dots = dotBackgrounds().map { it as FwPinDotDrawable }
        assertEquals(null, view.findViewById<View>(R.id.btn1).stateListAnimator)

        tap(1)
        view.pinCodeMatchingError()
        assertTrue(dots.all { it.error })
        assertEquals(0f, dots().translationX)
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        assertFalse(dots.any { it.error })
    }

    private companion object {
        val DIGIT_IDS = listOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9)
    }
}
