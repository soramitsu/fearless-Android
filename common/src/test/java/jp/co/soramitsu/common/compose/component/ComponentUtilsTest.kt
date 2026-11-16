package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil.decode.SvgDecoder
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComponentUtilsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun imageRequestsDecodeSvgFromTheGivenUrl() {
        val request = getImageRequest(context, "https://example.invalid/icon.svg")

        assertEquals("https://example.invalid/icon.svg", request.data)
        assertTrue(request.decoderFactory is SvgDecoder.Factory)
    }

    @Test
    fun vectorImagesAndMarginsTakeTheirSizes() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    Image(res = R.drawable.ic_vector, tint = Color.Red, contentDescription = "tinted", modifier = Modifier.size(24.dp))
                    Image(res = R.drawable.ic_vector, contentDescription = "plain", modifier = Modifier.size(16.dp))
                    Row(Modifier.testTag("row")) { MarginHorizontal(margin = 12.dp) }
                    Column(Modifier.testTag("column")) { MarginVertical(margin = 20.dp) }
                    GifImage(gifResource = R.drawable.ic_vector, modifier = Modifier.testTag("gif"))
                }
            }
        }

        compose.onNodeWithContentDescription("tinted").assertIsDisplayed().assertWidthIsEqualTo(24.dp)
        compose.onNodeWithContentDescription("plain").assertWidthIsEqualTo(16.dp)
        compose.onNodeWithTag("row").assertWidthIsEqualTo(12.dp)
        compose.onNodeWithTag("column").assertHeightIsEqualTo(20.dp)
        compose.onNodeWithTag("gif").assertExists()
    }
}
