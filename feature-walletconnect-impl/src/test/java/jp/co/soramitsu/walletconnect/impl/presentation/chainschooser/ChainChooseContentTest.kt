package jp.co.soramitsu.walletconnect.impl.presentation.chainschooser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChainChooseContentTest {
    @get:Rule val compose = createComposeRule()
    private val ethereum = ChainItemState("eip155:1", null, "Ethereum", true)
    private val polygon = ChainItemState("eip155:137", "", "Polygon", false)
    private var selected: ChainItemState? = null
    private var query = ""
    private var selectAll = 0
    private var done = 0

    @After
    fun restoreStyle() {
        unmockkObject(FwRestyle)
    }

    @Test
    @OptIn(androidx.compose.runtime.InternalComposeApi::class)
    fun rendersSelectChainScreenPreviewWithReadableContent() {
        val preview = Class.forName("jp.co.soramitsu.walletconnect.impl.presentation.chainschooser.ChainChooseContentKt")
            .getDeclaredMethod("SelectChainScreenPreview", androidx.compose.runtime.Composer::class.java, Int::class.javaPrimitiveType)
        preview.isAccessible = true
        compose.setContent {
            FearlessAppTheme {
                preview.invoke(null, androidx.compose.runtime.currentComposer, 0)
            }
        }
        compose.onNodeWithText("Kusama").assertExists()
    }

    @Test
    fun legacyChooserDisplaysSelectionAndForwardsActions() {
        show(ChainSelectScreenViewState(listOf(ethereum, polygon)), restyle = false)
        assertSelectionAndActions()
    }

    @Test
    fun restyledChooserDisplaysSelectionAndForwardsActions() {
        show(ChainSelectScreenViewState(listOf(ethereum, polygon)), restyle = true)
        assertSelectionAndActions()
    }

    @Test
    fun legacyChooserExplainsEmptySearchAndOffersDeselect() {
        show(ChainSelectScreenViewState(emptyList(), "missing"), restyle = false)
        compose.onNodeWithText("Selected: 0").assertIsDisplayed()
        compose.onNodeWithText("Deselect all").assertIsDisplayed()
        compose.onNodeWithText("Sumimasen!").assertIsDisplayed()
        compose.onNodeWithText("Nothing found for your request").assertIsDisplayed()
    }

    @Test
    fun restyledChooserExplainsEmptySearch() {
        show(ChainSelectScreenViewState(emptyList(), "missing"), restyle = true)
        compose.onNodeWithText("Sumimasen!").assertIsDisplayed()
        compose.onNodeWithText("Nothing found for your request").assertIsDisplayed()
    }

    @Test
    fun viewModeHidesBulkSelectionAndDoesNotChangeSelection() {
        show(ChainSelectScreenViewState(listOf(ethereum), isViewMode = true), restyle = false)
        compose.onNodeWithText("Select all").assertDoesNotExist()
        compose.onNodeWithText("Deselect all").assertDoesNotExist()
        compose.onNodeWithText("Ethereum").performClick()
        compose.runOnIdle { assertNull(selected) }
    }

    @Test
    fun restyledViewModeIgnoresTapsOnNetworks() {
        show(ChainSelectScreenViewState(listOf(ethereum), isViewMode = true), restyle = true)
        compose.onNodeWithText("Ethereum").performTouchInput { click() }
        compose.onNodeWithText("Deselect all").assertDoesNotExist()
        compose.runOnIdle { assertNull(selected) }
    }

    @Test
    fun loadingStateDoesNotShowAnEmptySearchWarning() {
        show(ChainSelectScreenViewState(null), restyle = false)
        compose.onNodeWithText("Selected: 0").assertIsDisplayed()
        compose.onNodeWithText("Nothing found for your request").assertDoesNotExist()
    }

    private fun assertSelectionAndActions() {
        compose.onNodeWithText("Selected: 1").assertIsDisplayed()
        compose.onNodeWithText("Polygon").performClick()
        compose.runOnIdle { assertSame(polygon, selected) }
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("eth")
        compose.runOnIdle {
            assertEquals(1, selectAll)
            assertEquals(1, done)
            assertEquals("eth", query)
        }
    }

    private fun show(state: ChainSelectScreenViewState, restyle: Boolean) {
        mockkObject(FwRestyle)
        every { FwRestyle.isEnabled(FwSurface.Pickers) } returns restyle
        compose.setContent {
            FearlessAppTheme {
                ChainSelectContent(state, { selected = it }, { query = it }, { selectAll++ }, { done++ })
            }
        }
    }
}
