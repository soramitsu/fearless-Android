package jp.co.soramitsu.nft.impl.presentation.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import java.time.Duration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class NftDetailsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val callbacks = mock<NftDetailsScreenInterface>()

    @Before
    fun advanceClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun ownedTokenShowsDetailsAndDispatchesActions() {
        val state = NftDetailsScreenState(
            isTokenUserOwned = true,
            description = "A rare bird",
            collectionName = "Fearless Birds",
            owner = "Owner address",
            tokenId = "12345",
            creator = "Creator address",
            network = "Polygon",
            tokenType = "ERC1155",
            dateTime = "17 December 2022",
            price = "0.5 MATIC"
        )
        compose.setContent { FearlessAppTheme { NftDetailsScreen(state, callbacks) } }

        compose.onNodeWithText("Send").performScrollTo().performClick()
        verify(callbacks).sendClicked()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Share").performScrollTo().performClick()
        verify(callbacks).shareClicked()
        listOf("A rare bird", "Fearless Birds", "Polygon", "ERC1155", "17 December 2022", "0.5 MATIC").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Owner address").performScrollTo().performClick()
        verify(callbacks).ownerClicked()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("12345").performScrollTo().performClick()
        verify(callbacks).tokenIdClicked()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Creator address").performScrollTo().performClick()
        verify(callbacks).creatorClicked()
    }

    @Test
    fun unownedTokenOmitsSendAndMissingOptionalDetails() {
        compose.setContent {
            FearlessAppTheme { NftDetailsScreen(NftDetailsScreenState(creator = "Artist", network = "Ethereum"), callbacks) }
        }
        compose.onNodeWithText("Send").assertDoesNotExist()
        listOf("Collection", "Owned", "Price", "Date").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithText("Artist").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Share").performScrollTo().performClick()
        verify(callbacks).shareClicked()
    }

    @Test
    fun loadingDetailsDisableActionsUntilCreatorIsKnown() {
        compose.setContent {
            FearlessAppTheme { NftDetailsScreen(NftDetailsScreenState(isTokenUserOwned = true), callbacks) }
        }
        compose.onNodeWithText("Send").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Share").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Creator").performScrollTo().assertIsDisplayed()
        verifyNoInteractions(callbacks)
    }
}
