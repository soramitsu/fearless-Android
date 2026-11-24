package jp.co.soramitsu.common.compose.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.rememberSwipeableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * What is painted beside a row. The actions behind a row used to wait just outside it, and on a page
 * with a gutter wider than that distance part of them showed beside every resting row.
 */
@OptIn(ExperimentalMaterialApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SwipeBoxGutterTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var hostView: View

    @Test
    fun atRestNothingOfTheActionsIsPaintedBesideTheRow() {
        show()

        val page = picture()
        assertEquals("pixels of the actions around a resting row", 0, page.count(ACTIONS))
        assertTrue("the row itself is painted", page.count(ROW) > 0)
    }

    @Test
    fun aRowSwipedRightShowsItsLeftActions() {
        show()

        compose.onNodeWithTag("SwipeBox").performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertTrue(picture().count(ACTIONS) > 0)
    }

    @Test
    fun aRowSwipedLeftShowsItsRightActions() {
        show()

        compose.onNodeWithTag("SwipeBox").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertTrue(picture().count(ACTIONS) > 0)
    }

    private fun show() {
        compose.setContent {
            hostView = LocalView.current
            // A gutter wider than the 10 dp the actions wait beyond the row, as the wallet home's is.
            Box(Modifier.fillMaxSize().background(PAGE).padding(horizontal = 32.dp)) {
                SwipeBox(
                    state = SwipeBoxViewState(leftStateWidth = 170.dp, rightStateWidth = 90.dp),
                    swipeableState = rememberSwipeableState(SwipeState.INITIAL),
                    initialContent = { Box(Modifier.fillMaxWidth().height(80.dp).background(ROW)) },
                    leftContent = { Box(Modifier.size(width = 170.dp, height = 60.dp).background(ACTIONS)) },
                    rightContent = { Box(Modifier.size(width = 90.dp, height = 60.dp).background(ACTIONS)) }
                )
            }
        }
    }

    private fun picture(): Bitmap = compose.runOnIdle {
        Bitmap.createBitmap(hostView.width, hostView.height, Bitmap.Config.ARGB_8888).also { hostView.draw(Canvas(it)) }
    }

    private fun Bitmap.count(colour: Color): Int {
        val argb = colour.toArgb()
        var found = 0
        for (y in 0 until height) for (x in 0 until width) if (getPixel(x, y) == argb) found++
        return found
    }

    private companion object {
        val PAGE = Color.DarkGray
        val ROW = Color.Green
        val ACTIONS = Color.Red
    }
}
