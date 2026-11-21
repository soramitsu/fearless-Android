package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MultiToggleButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private data class Tab(override val titleResId: Int) : MultiToggleItem

    private val currencies = Tab(R.string.currencies_stub_text)
    private val nfts = Tab(R.string.nfts_stub)

    @Test
    fun segmentsShowTheSelectionAndReportTheTappedItem() {
        val selected = mutableListOf<Tab>()
        compose.setContent {
            FearlessAppTheme {
                MultiToggleButton(
                    state = MultiToggleButtonState(currentSelection = currencies, toggleStates = listOf(currencies, nfts)),
                    onToggleChange = { selected += it }
                )
            }
        }

        compose.onNodeWithTag("MultiToggleButton_Currencies").assertIsSelected()
        compose.onNodeWithTag("MultiToggleButton_NFTs").assertIsNotSelected().performClick()
        compose.waitForIdle()

        assertEquals(listOf(nfts), selected)
    }

    @Test
    fun stackedSegmentsWithAnUnknownSelectionSelectTheFirst() {
        val selected = mutableListOf<Tab>()
        compose.setContent {
            FearlessAppTheme {
                Column {
                    MultiToggleButton(
                        state = MultiToggleButtonState(currentSelection = Tab(R.string.staking_redeem), toggleStates = listOf(currencies, nfts)),
                        onToggleChange = { selected += it },
                        stacked = true
                    )
                }
            }
        }

        compose.onNodeWithTag("MultiToggleButton_Currencies").assertIsSelected().performClick()
        compose.onNodeWithText("NFTs").assertIsDisplayed()
        compose.waitForIdle()

        assertEquals(listOf(currencies), selected)
    }

    @Test
    fun previewShowsBothSegments() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.MultiToggleButtonKt", "PreviewMultiToggleButton") }
        }

        compose.onNodeWithText("Currencies").assertIsDisplayed()
        compose.onNodeWithText("NFTs").assertIsDisplayed()
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
