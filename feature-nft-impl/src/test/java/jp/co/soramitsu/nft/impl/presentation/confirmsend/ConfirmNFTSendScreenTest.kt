package jp.co.soramitsu.nft.impl.presentation.confirmsend

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.nft.impl.presentation.confirmsend.contract.ConfirmNFTSendCallback
import jp.co.soramitsu.nft.impl.presentation.confirmsend.contract.ConfirmNFTSendScreenState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class ConfirmNFTSendScreenTest {
    @get:Rule val compose = createComposeRule()
    private val callback = mock<ConfirmNFTSendCallback>()

    @Before
    fun advanceClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun loadedConfirmationDisplaysTransferAndDispatchesConfirm() {
        val state = ConfirmNFTSendScreenState(
            thumbnailImageModel = Loadable.ReadyToRender(ImageModel.ResId(R.drawable.drawable_fearless_bird)),
            fromInfoItem = TitleValueViewState("From", "My wallet"),
            toInfoItem = TitleValueViewState("To", "Recipient"),
            collectionInfoItem = TitleValueViewState("Collection", "Birds"),
            feeInfoItem = TitleValueViewState("Fee", "0.001 ETH"),
            buttonState = ButtonViewState("Confirm transfer", true)
        )
        compose.setContent { FearlessAppTheme { ConfirmNFTSendScreen(state, callback) } }
        listOf("My wallet", "Recipient", "Birds", "0.001 ETH").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        assertEquals(4, state.tableItems.size)
        compose.onNodeWithText("Confirm transfer").performClick()
        verify(callback).onConfirmClick()
    }

    @Test
    fun initialStateShowsSendingAndWaitsForFee() {
        val state = ConfirmNFTSendScreenState.default
        compose.setContent { FearlessAppTheme { ConfirmNFTSendScreen(state, callback) } }
        compose.onNodeWithText("Sending").assertIsDisplayed()
        compose.onNodeWithText("Confirm transfer").assertDoesNotExist()
        assertEquals(emptyList<TitleValueViewState>(), state.tableItems)
        verifyNoInteractions(callback)
    }
}
