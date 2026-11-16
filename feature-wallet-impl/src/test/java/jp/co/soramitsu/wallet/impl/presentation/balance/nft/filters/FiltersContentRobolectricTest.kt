package jp.co.soramitsu.wallet.impl.presentation.balance.nft.filters

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The NFT filter sheet: one switch per filter and a close button. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h1600dp")
class FiltersContentRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val picks = mutableListOf<Pair<NFTFilter, Boolean>>()
    private var closes = 0

    @Test
    fun anEnabledFilterCanBeSwitchedOffAndTheSheetClosed() {
        show(mapOf(NFTFilter.AirDrops to true))

        compose.onNodeWithText("Hide NFTs").assertIsDisplayed()
        compose.onNodeWithText(NFTFilter.AirDrops.name).assertIsDisplayed()
        compose.onNode(isToggleable()).assertIsOn().performClick()
        compose.onNode(hasClickAction() and !isToggleable()).performClick()

        assertEquals(listOf(NFTFilter.AirDrops to false), picks)
        assertEquals(1, closes)
    }

    @Test
    fun aDisabledFilterCanBeSwitchedOn() {
        show(mapOf(NFTFilter.AirDrops to false))

        compose.onNode(isToggleable()).assertIsOff().performClick()

        assertEquals(listOf(NFTFilter.AirDrops to true), picks)
        assertEquals(0, closes)
    }

    private fun show(state: Map<NFTFilter, Boolean>) {
        compose.setContent {
            FearlessAppTheme {
                FiltersContent(
                    state = state,
                    onSelectClick = { filter, checked -> picks += filter to checked },
                    onCloseClick = { closes++ }
                )
            }
        }
    }
}
