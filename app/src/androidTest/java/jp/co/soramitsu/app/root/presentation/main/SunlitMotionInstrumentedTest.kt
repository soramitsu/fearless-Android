package jp.co.soramitsu.app.root.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.fwSheet
import jp.co.soramitsu.common.compose.component.FearlessProgress
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.theme.fw.FwRollingText
import jp.co.soramitsu.common.compose.theme.fw.FwPrimaryButton
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.common.compose.theme.fw.LocalFwSecureSurface
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.resources.ResourceManagerImpl
import jp.co.soramitsu.common.utils.QrCodeGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SunlitMotionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    @Test fun secureBalancesAndLiveReducedMotionSettleWithoutMovement() {
        var text by mutableStateOf("1234.56")
        var secure by mutableStateOf(true)
        var reduced by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalFwSecureSurface provides secure,
                LocalFwEffects provides FwEffects(FwTier.Full, highContrast = false, reduceMotion = reduced)
            ) {
                Column(Modifier.background(FwStandard.bgBase).padding(20.dp).testTag("amount")) {
                    FwRollingText(text, TextStyle(fontSize = 32.sp))
                    FwPrimaryButton("Loading", onClick = {}, loading = true, textStyle = TextStyle(fontSize = 16.sp))
                    FearlessProgress()
                    Shimmer(Modifier.width(200.dp).height(24.dp))
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { text = "2345.67" }
        compose.mainClock.advanceTimeBy(64)
        val secureFrame = compose.onNodeWithTag("amount").captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(1000)
        assertTrue("Secure surfaces must not roll or flash", secureFrame.sameAs(compose.onNodeWithTag("amount").captureToImage().asAndroidBitmap()))

        compose.runOnIdle { secure = false; text = "3456.78" }
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle { reduced = true }
        compose.mainClock.advanceTimeBy(64)
        val reducedFrame = compose.onNodeWithTag("amount").captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(1000)
        assertTrue("Changing motion preference settles the ongoing roll", reducedFrame.sameAs(compose.onNodeWithTag("amount").captureToImage().asAndroidBitmap()))
    }

    @Test fun brandedReceiveQrCodesDecodeToTheExactPayload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resources = ResourceManagerImpl(ContextManager.getInstanceOrInit(context, LanguagesHolder()))
        val generator = QrCodeGenerator(android.graphics.Color.BLACK, android.graphics.Color.WHITE, resources)
        for (payload in listOf(
            "0x0123456789012345678901234567890123456789",
            "5GrwvaEF5zXb26Fz9rcQpDWS64sd35Xg6Dc9brjiqW2xjEvx",
            "bitcoin:bc1qexample00000000000000000000000000000000?amount=0.0001",
            "https://example.invalid/receive?address=" + "a".repeat(256)
        )) {
            val image = generator.generateQrBitmap(payload)
            val pixels = IntArray(image.width * image.height)
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
            assertEquals(payload, MultiFormatReader().decode(bitmap).text)
        }
    }

    @Test fun wrappedAmountsKeepTheirFullLayoutWhileValuesChange() {
        var text by mutableStateOf("$12,814.22")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, false, false)) {
                Box(Modifier.requiredWidth(240.dp).background(FwStandard.bgBase).padding(8.dp).testTag("wrapped-amount")) {
                    FwRollingText(text, TextStyle(fontSize = 48.sp))
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        val before = compose.onNodeWithTag("wrapped-amount").fetchSemanticsNode().boundsInRoot
        assertTrue("The large-text fixture must wrap", before.height > 90 * compose.activity.resources.displayMetrics.density)
        compose.runOnIdle { text = "$12,815.22" }
        compose.mainClock.advanceTimeBy(64)
        val changing = compose.onNodeWithTag("wrapped-amount").fetchSemanticsNode().boundsInRoot
        assertEquals("A balance update must retain all wrapped lines", before.height, changing.height, 1f)
    }

    @Test fun unmarkedSharedSheetsStayStillWithoutCallerOptOut() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalFwSecureSurface provides false,
                LocalFwEffects provides FwEffects(FwTier.Full, false, false)
            ) {
                Box(Modifier.requiredSize(240.dp, 120.dp).fwSheet().testTag("unmarked-rim"))
            }
        }
        compose.mainClock.advanceTimeBy(16)
        val rim = compose.onNodeWithTag("unmarked-rim").captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(500)
        assertTrue("Shared rims stay static even without a secure caller", rim.sameAs(compose.onNodeWithTag("unmarked-rim").captureToImage().asAndroidBitmap()))

        val sheet = UnmarkedSheet()
        try {
            compose.runOnUiThread { sheet.showNow(compose.activity.supportFragmentManager, "unmarked-sheet") }
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle {
                assertTrue("The actual shared sheet host must default to secure motion", sheet.observedSecure)
                sheet.amount = "2345.67"
            }
            compose.mainClock.advanceTimeBy(64)
            val frame = compose.onNodeWithTag("unmarked-host").captureToImage().asAndroidBitmap()
            compose.mainClock.advanceTimeBy(1000)
            assertTrue("Unmarked sheet contents must stay still", frame.sameAs(compose.onNodeWithTag("unmarked-host").captureToImage().asAndroidBitmap()))
        } finally {
            compose.runOnUiThread { sheet.dismissNow() }
        }
    }


    // A public no-argument fragment exercises the real host/lifecycle and can be recreated by Android.
    class UnmarkedSheet : BaseComposeBottomSheetDialogFragment<BaseViewModel>() {
        override val viewModel = BaseViewModel()
        var observedSecure = false
        var amount by mutableStateOf("1234.56")

        @Composable override fun Content(padding: PaddingValues) {
            observedSecure = LocalFwSecureSurface.current
            Column(Modifier.requiredSize(240.dp, 200.dp).fwSheet().padding(16.dp).testTag("unmarked-host")) {
                FwRollingText(amount, TextStyle(fontSize = 32.sp))
                FwPrimaryButton("Loading", {}, loading = true, textStyle = TextStyle(fontSize = 16.sp))
                FearlessProgress()
            }
        }
    }

}
