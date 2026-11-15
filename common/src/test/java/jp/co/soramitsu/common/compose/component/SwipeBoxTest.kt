package jp.co.soramitsu.common.compose.component

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.material.Text
import androidx.compose.material.rememberSwipeableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalMaterialApi::class)
@RunWith(RobolectricTestRunner::class)
class SwipeBoxTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var swipeState: SwipeableState<SwipeState>

    private fun setSwipeBox() {
        compose.setContent {
            FearlessAppTheme {
                swipeState = rememberSwipeableState(SwipeState.INITIAL)
                SwipeBox(
                    state = SwipeBoxViewState(leftStateWidth = 120.dp, rightStateWidth = 80.dp),
                    swipeableState = swipeState,
                    initialContent = { Text("Row content") },
                    leftContent = { Text("Left actions") },
                    rightContent = { Text("Right actions") }
                )
            }
        }
    }

    @Test
    fun rowStartsInTheMiddleWithAllThreeLayersComposed() {
        setSwipeBox()

        compose.onNodeWithText("Row content").assertIsDisplayed()
        compose.onNodeWithText("Left actions").assertExists()
        compose.onNodeWithText("Right actions").assertExists()
        assertEquals(SwipeState.INITIAL, swipeState.currentValue)
    }

    @Test
    fun swipingRightOpensTheLeftActions() {
        setSwipeBox()

        compose.onNodeWithTag("SwipeBox").performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertEquals(SwipeState.LEFT, swipeState.currentValue)
        compose.onNodeWithText("Left actions").assertIsDisplayed()
    }

    @Test
    fun swipingLeftOpensTheRightActions() {
        setSwipeBox()

        compose.onNodeWithTag("SwipeBox").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(SwipeState.RIGHT, swipeState.currentValue)
        compose.onNodeWithText("Right actions").assertIsDisplayed()
    }
}
