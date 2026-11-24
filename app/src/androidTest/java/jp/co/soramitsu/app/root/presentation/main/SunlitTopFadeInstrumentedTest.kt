package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.compose.theme.fw.fwTopFade
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SunlitTopFadeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    @Test fun contentFadesInFromTheTopEdgeAndIsUntouchedBelowTheFade() {
        compose.setContent {
            Box(Modifier.size(100.dp).background(Color.Black).testTag("page")) {
                Box(Modifier.fillMaxSize().fwTopFade(20.dp).background(Color.White))
            }
        }
        compose.waitForIdle()
        val image = compose.onNodeWithTag("page").captureToImage().asAndroidBitmap()
        val density = compose.activity.resources.displayMetrics.density
        val x = image.width / 2
        fun brightness(y: Int) = AndroidColor.red(image.getPixel(x, y))
        assertTrue("top edge ${brightness(0)}", brightness(0) < 30)
        assertTrue("middle of the fade ${brightness((10 * density).toInt())}", brightness((10 * density).toInt()) in 90..170)
        assertTrue("below the fade ${brightness((22 * density).toInt())}", brightness((22 * density).toInt()) == 255)
    }
}
