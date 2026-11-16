package jp.co.soramitsu.common.compose.component

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class DropDownTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        Settings.Global.putString(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, "0")
    }

    @Test
    fun activeDropDownShowsValueAndReportsClicks() {
        var clicks = 0
        compose.setContent {
            FearlessAppTheme {
                DropDown(state = DropDownViewState(text = "my best pool", hint = "Pool name"), onClick = { clicks++ })
            }
        }

        compose.onNodeWithText("Pool name").assertIsDisplayed()
        compose.onNodeWithText("my best pool").assertHasClickAction().performClick()
        compose.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun inactiveDropDownIsClickableOnlyWhenAlwaysClickable() {
        var always = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    InactiveDropDown(text = "Locked value", hint = R.string.staking_redeem)
                    InactiveDropDown(
                        state = DropDownViewState(
                            text = "Still clickable",
                            hint = "Validator",
                            clickableMode = DropDownViewState.ClickableMode.AlwaysClickable
                        ),
                        onClick = { always++ }
                    )
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.staking_redeem)).assertIsDisplayed()
        compose.onNodeWithText("Locked value").assertHasNoClickAction()
        compose.onNodeWithText("Still clickable").performClick()
        compose.waitForIdle()

        assertEquals(1, always)
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun loadingDropDownShowsOnlyItsHint() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    DropDown(state = DropDownViewState(text = null, hint = "Loading pool"), onClick = {})
                    InactiveDropDown(text = null, hint = R.string.staking_redeem)
                }
            }
        }

        compose.onNodeWithText("Loading pool").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText(context.getString(R.string.staking_redeem)).assertHasNoClickAction()
    }

    @Test
    fun previewShowsActiveInactiveAndLoadingDropDowns() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.DropDownKt", "DropDownPreview") }
        }

        compose.onNodeWithText("my best pool").assertIsDisplayed()
        compose.onNodeWithText("Inactive value").assertIsDisplayed()
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
