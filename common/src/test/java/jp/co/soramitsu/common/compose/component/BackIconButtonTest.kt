package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackIconButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun navigationButtonIsNamedAfterItsIconAndReportsClicks() {
        var clicks = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    NavigationIconButton(onNavigationClick = { clicks++ })
                    NavigationIconButton(navigationIconResId = R.drawable.ic_cross_24, contentDescription = "Dismiss sheet")
                }
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.ux_back)).performClick()
        compose.onNodeWithContentDescription("Dismiss sheet").assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun previewShowsTheBackButton() {
        compose.setContent { NavigationIconButtonPreview() }

        compose.onNodeWithContentDescription(context.getString(R.string.ux_back)).assertIsDisplayed()
    }
}
