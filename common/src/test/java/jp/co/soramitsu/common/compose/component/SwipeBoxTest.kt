package jp.co.soramitsu.common.compose.component

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.material.Text
import androidx.compose.material.rememberSwipeableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    private lateinit var scope: CoroutineScope

    private fun setSwipeBox() {
        compose.setContent {
            FearlessAppTheme {
                swipeState = rememberSwipeableState(SwipeState.INITIAL)
                scope = rememberCoroutineScope()
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
    fun atRestTheRowIsAloneItsActionsAreNotThereToBeDrawnOrTapped() {
        setSwipeBox()

        compose.onNodeWithText("Row content").assertIsDisplayed()
        compose.onNodeWithText("Left actions").assertDoesNotExist()
        compose.onNodeWithText("Right actions").assertDoesNotExist()
        assertEquals(SwipeState.INITIAL, swipeState.currentValue)
    }

    @Test
    fun swipingRightOpensTheLeftActions() {
        setSwipeBox()

        compose.onNodeWithTag("SwipeBox").performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertEquals(SwipeState.LEFT, swipeState.currentValue)
        compose.onNodeWithText("Left actions").assertIsDisplayed()
        compose.onNodeWithText("Right actions").assertDoesNotExist()
    }

    @Test
    fun swipingLeftOpensTheRightActions() {
        setSwipeBox()

        compose.onNodeWithTag("SwipeBox").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(SwipeState.RIGHT, swipeState.currentValue)
        compose.onNodeWithText("Right actions").assertIsDisplayed()
        compose.onNodeWithText("Left actions").assertDoesNotExist()
    }

    @Test
    fun closingTheRowTakesItsActionsAwayAgain() {
        setSwipeBox()
        compose.onNodeWithTag("SwipeBox").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("Left actions").assertIsDisplayed()

        // As the wallet does after an action is chosen.
        compose.runOnIdle { scope.launch { swipeState.snapTo(SwipeState.INITIAL) } }
        compose.waitForIdle()

        assertEquals(SwipeState.INITIAL, swipeState.currentValue)
        compose.onNodeWithText("Left actions").assertDoesNotExist()
        compose.onNodeWithText("Row content").assertIsDisplayed()
    }
}
