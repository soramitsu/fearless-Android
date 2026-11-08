package jp.co.soramitsu.app.root.presentation.main

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.NestedScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import jp.co.soramitsu.account.impl.presentation.pincode.view.PinCodeView
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SunlitPinLayoutInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    @Test fun keypadAndRecoveryHaveSeparateFullTouchTargetsAtSmallAndLargeTextSizes() {
        val modes = listOf(Triple(320, 600, 1f), Triple(320, 600, 2f), Triple(400, 800, 1f))
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
                    val themed = ContextThemeWrapper(context.createConfigurationContext(config), compose.activity.theme)
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
                val pin = root.findViewById<PinCodeView>(R.id.pinCodeView)
                val scroll = pin.parent.parent as NestedScrollView
                scroll.scrollTo(0, scroll.getChildAt(0).height)
            }
            compose.waitForIdle()
            val zero = root.findViewById<View>(R.id.btn0)
            val delete = root.findViewById<View>(R.id.btnDelete)
            val recovery = root.findViewById<View>(R.id.forgotPin)
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
}
