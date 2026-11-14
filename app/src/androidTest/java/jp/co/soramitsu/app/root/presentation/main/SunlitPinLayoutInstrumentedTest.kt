package jp.co.soramitsu.app.root.presentation.main

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import jp.co.soramitsu.account.impl.presentation.pincode.view.PinCodeView
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.FwLockup
import jp.co.soramitsu.common.compose.theme.fw.FwLockupHero
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SunlitPinLayoutInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    @Test fun lockupTakesAboutHalfThePinPageAndLeavesTheKeypadItsRoom() {
        val modes = listOf(Triple(320, 800, 1f), Triple(320, 800, 2f), Triple(360, 780, 1f), Triple(400, 800, 1f), Triple(800, 800, 1f), Triple(320, 600, 1f))
        var mode by mutableStateOf(modes.first())
        lateinit var root: View
        compose.setContent {
            key(mode) {
                AndroidView(modifier = Modifier.requiredSize(mode.first.dp, mode.second.dp), factory = { context ->
                    val config = Configuration(context.resources.configuration).apply {
                        fontScale = mode.third
                        screenWidthDp = mode.first
                        screenHeightDp = mode.second
                    }
                    val themed = ContextThemeWrapper(context.createConfigurationContext(config), jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)
                    LayoutInflater.from(themed).inflate(R.layout.fragment_pincode, null).also { view ->
                        root = view
                        view.findViewById<View>(R.id.pinRecoveryContent).visibility = View.GONE
                        view.findViewById<PinCodeView>(R.id.pinCodeView).applySunlitStyle(FwEffects(FwTier.Full, false, true), FwStandard)
                    }
                })
            }
        }
        for (next in modes) {
            compose.runOnIdle { mode = next }
            compose.waitForIdle()
            compose.runOnIdle {
                val pin = root.findViewById<PinCodeView>(R.id.pinCodeView)
                val density = pin.resources.displayMetrics.density
                val logo = pin.getChildAt(0) as? ImageView
                if (next.second < 680) {
                    assertTrue("$next has no room for the mark", logo == null)
                    return@runOnIdle
                }
                checkNotNull(logo) { "$next has room for the mark" }
                // About 56 % of the width, at most 280 dp, within what the keypad leaves (556 dp + 12 dp margins).
                val room = next.second - 556 - 2 * 12
                val width = FwLockup.widthDp(next.first.toFloat(), room.toFloat()) * density
                assertEquals("$next mark width", width, logo.width.toFloat(), 1f)
                assertTrue("$next stays near half the page", logo.width <= next.first * density * FwLockup.WIDTH_FRACTION + 1f)
                assertTrue("$next stays at most 280 dp", logo.width <= FwLockup.MAX_WIDTH_DP * density + 1f)
                assertEquals("$next mark is centred", pin.width / 2f, (logo.left + logo.right) / 2f, 1f)
                val artHeight = logo.height - logo.paddingTop - logo.paddingBottom
                assertTrue("$next leaves the keypad its room", artHeight <= room * density + 1f)
                assertEquals("$next artwork keeps its aspect", FwLockup.ASPECT, logo.width.toFloat() / artHeight, 0.05f)
                assertEquals("$next mark carries the brand name", "Fearless", logo.contentDescription)
                assertTrue("$next mark stays above the title", logo.bottom <= pin.findViewById<View>(R.id.pinCodeTitleTv).top)
                assertEquals(0, logo.paddingLeft + logo.paddingRight)
                val title = pin.findViewById<TextView>(R.id.pinCodeTitleTv)
                assertEquals("$next title uses the app's font", ResourcesCompat.getFont(title.context, jp.co.soramitsu.common.R.font.sora_bold), title.typeface)
            }
        }
    }

    @Test fun navigationBarShowsThePageInsteadOfAStrip() {
        val theme = ContextThemeWrapper(compose.activity, jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)
        val attributes = theme.obtainStyledAttributes(intArrayOf(android.R.attr.navigationBarColor, android.R.attr.enforceNavigationBarContrast))
        try {
            assertEquals(android.graphics.Color.TRANSPARENT, attributes.getColor(0, android.graphics.Color.BLACK))
            assertFalse("Three-button navigation must not lay a scrim over the page", attributes.getBoolean(1, true))
        } finally {
            attributes.recycle()
        }
    }

    @Test fun launchArtLockupKeepsItsAspectAndTheSystemSplashCrestPosition() {
        val resources = compose.activity.resources
        val density = resources.displayMetrics.density
        val width = resources.getDimension(jp.co.soramitsu.common.R.dimen.fw_launch_lockup_width)
        val height = resources.getDimension(jp.co.soramitsu.common.R.dimen.fw_launch_lockup_height)
        val drop = resources.getDimension(jp.co.soramitsu.common.R.dimen.fw_launch_lockup_drop)
        assertEquals(FwLockup.ASPECT, width / height, 0.01f)
        assertTrue("At most 280 dp", width <= FwLockup.MAX_WIDTH_DP * density + 0.5f)
        assertTrue("Fits the narrowest page", width <= 320 * density)
        // The crest's centre is 199.68 of the lockup's 505.30 units from its top; the drop moves it to the
        // window's centre, where the Android 12+ system splash drew the same crest.
        val crestCentre = height * 199.68f / 505.30324f
        assertEquals(height / 2f - crestCentre, drop / 2f, 0.75f)
        // The crest's reach (0.78 x 253.62 units) is within 2 % of the system splash crest's 88 dp.
        assertEquals(88f * density, width / 640f * 0.78f * 253.62f, 88f * density * 0.02f)
    }

    @Test fun lockupHeroTakesAboutHalfTheScreenAndKeepsItsAspectAndLabel() {
        compose.setContent {
            CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, false, true), LocalFwColors provides FwStandard) {
                Box(Modifier.requiredSize(360.dp, 400.dp).testTag("lockup-page")) {
                    FwLockupHero(Modifier.padding(horizontal = 20.dp).testTag("lockup"))
                }
            }
        }
        val screenWidth = compose.activity.resources.configuration.screenWidthDp.toFloat()
        // The tag follows the caller's padding in the modifier chain, so these are the mark's own bounds.
        val bounds = compose.onNodeWithTag("lockup").getUnclippedBoundsInRoot()
        assertEquals(FwLockup.widthDp(screenWidth), bounds.width.value, 1f)
        assertEquals(FwLockup.ASPECT, bounds.width.value / bounds.height.value, 0.05f)
        compose.onNodeWithContentDescription("Fearless").assertExists()
        val image = compose.onNodeWithTag("lockup-page").captureToImage().asAndroidBitmap()
        fun white(bitmap: Bitmap) = (0 until bitmap.height).sumOf { y ->
            (0 until bitmap.width).count { x ->
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.red(pixel) > 240 && android.graphics.Color.green(pixel) > 240 && android.graphics.Color.blue(pixel) > 240
            }
        }
        assertTrue("The white crest and wordmark are drawn", white(image) > image.width)
    }

    @Test fun keypadAndRecoveryHaveSeparateFullTouchTargetsAtSmallAndLargeTextSizes() {
        val modes = listOf(Triple(320, 600, 1f), Triple(320, 600, 2f), Triple(320, 800, 2f), Triple(400, 800, 1f))
        var mode by mutableStateOf(modes.first())
        lateinit var root: View
        var entered: String? = null
        var recoveryClicks = 0
        compose.setContent {
            key(mode) {
                AndroidView(modifier = Modifier.requiredSize(mode.first.dp, mode.second.dp).testTag("pin-layout"), factory = { context ->
                    val config = Configuration(context.resources.configuration).apply {
                        fontScale = mode.third
                        screenWidthDp = mode.first
                        screenHeightDp = mode.second
                    }
                    val themed = ContextThemeWrapper(context.createConfigurationContext(config), jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)
                    LayoutInflater.from(themed).inflate(R.layout.fragment_pincode, null).also { view ->
                        root = view
                        view.findViewById<View>(R.id.pinRecoveryContent).visibility = View.GONE
                        view.findViewById<View>(R.id.forgotPin).apply {
                            visibility = View.VISIBLE
                            setOnClickListener { recoveryClicks++ }
                        }
                        view.findViewById<PinCodeView>(R.id.pinCodeView).apply {
                            applySunlitStyle(FwEffects(FwTier.Reduced, false, true), FwStandard)
                            pinCodeEnteredListener = { entered = it }
                        }
                    }
                })
            }
        }
        for (next in modes) {
            compose.runOnIdle { mode = next; entered = null; recoveryClicks = 0 }
            compose.waitForIdle()
            compose.runOnIdle {
                settleInsetsAndLayout(root)
                val pin = root.findViewById<PinCodeView>(R.id.pinCodeView)
                val scroll = pin.parent.parent as NestedScrollView
                scroll.scrollTo(0, scroll.getChildAt(0).height)
            }
            compose.waitForIdle()
            val zero = root.findViewById<View>(R.id.btn0)
            val delete = root.findViewById<View>(R.id.btnDelete)
            val recovery = root.findViewById<TextView>(R.id.forgotPin)
            if (next.third == 2f) {
                val normalSp = 16 * recovery.resources.displayMetrics.density
                assertTrue("The recovery label must use enlarged native text", recovery.textSize > normalSp * 1.3f)
            }
            val viewport = Rect().also { assertTrue(root.getGlobalVisibleRect(it)) }
            val bounds = listOf(zero, delete, recovery).map { view ->
                Rect().also {
                    assertTrue("$next missing target", view.getGlobalVisibleRect(it))
                    assertEquals("$next clipped height", view.height, it.height())
                    assertEquals("$next clipped width", view.width, it.width())
                    assertTrue("$next target outside viewport", viewport.contains(it))
                    val minimum = (48 * view.resources.displayMetrics.density).toInt()
                    assertTrue("$next target is smaller than 48dp", it.width() >= minimum && it.height() >= minimum)
                }
            }
            assertTrue("$next PIN row overlaps recovery", bounds[0].bottom <= bounds[2].top && bounds[1].bottom <= bounds[2].top)
            val origin = IntArray(2).also(root::getLocationOnScreen)
            val zeroCenter = Offset(bounds[0].exactCenterX() - origin[0], bounds[0].exactCenterY() - origin[1])
            compose.onNodeWithTag("pin-layout").performTouchInput { repeat(6) { click(zeroCenter) } }
            compose.runOnIdle { assertEquals("000000", entered) }
            val recoveryCenter = Offset(bounds[2].exactCenterX() - origin[0], bounds[2].exactCenterY() - origin[1])
            compose.onNodeWithTag("pin-layout").performTouchInput { click(recoveryCenter) }
            compose.runOnIdle { assertEquals(1, recoveryClicks) }
            val image = compose.onNodeWithTag("pin-layout").captureToImage().asAndroidBitmap()
            val folder = compose.activity.getExternalFilesDir("pin-layout-evidence")!!.apply { mkdirs() }
            File(folder, "pin-${next.first}x${next.second}-font${(next.third * 100).toInt()}.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    /**
     * From API 35 the test window is edge to edge, so its system bar insets pad the page (its root fits system
     * windows), and they can reach a page hosted in an AndroidView after its first layout. The page then keeps its
     * unpadded layout while the padding clips it, which no real window shows: a padding change is laid out on the
     * next frame. Apply the insets and lay the page out now, so the targets are measured where the page draws them.
     */
    private fun settleInsetsAndLayout(root: View) {
        root.rootWindowInsets?.let(root::dispatchApplyWindowInsets)
        // A plain measure with unchanged specs is served from the measure cache and keeps the stale positions.
        root.forceLayout()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY)
        )
        root.layout(root.left, root.top, root.right, root.bottom)
    }
}
