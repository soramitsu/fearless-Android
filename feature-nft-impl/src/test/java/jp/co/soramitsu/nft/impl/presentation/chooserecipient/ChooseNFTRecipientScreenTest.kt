package jp.co.soramitsu.nft.impl.presentation.chooserecipient

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientCallback
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientScreenState
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import kotlinx.coroutines.flow.MutableStateFlow
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
class ChooseNFTRecipientScreenTest {
    @get:Rule val compose = createComposeRule()
    private val callback = mock<ChooseNFTRecipientCallback>()
    private val state = MutableStateFlow(
        ChooseNFTRecipientScreenState(
            null, AddressInputState("Send to", "", R.drawable.ic_address_placeholder),
            ButtonViewState("Preview transfer", true), FeeInfoViewState(feeAmount = "0.01 ETH", feeAmountFiat = "$20"), false
        )
    )

    @Before
    fun showRecipientScreen() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        compose.setContent {
            FearlessAppTheme {
                NavHost(rememberNavController(), NFTNavGraphRoute.ChooseNFTRecipientScreen.routeName) {
                    ChooseNFTRecipientNavComposable(state, callback)
                }
            }
        }
    }

    @Test
    fun recipientSourcesAndPreviewReachCallbacks() {
        compose.onNodeWithText("0.01 ETH").assertIsDisplayed()
        compose.onNodeWithText("Paste").performClick()
        verify(callback).onPasteClick()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Scan QR").performClick()
        verify(callback).onQrClick()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Contacts").performClick()
        verify(callback).onContactsClick()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("My wallets").performClick()
        verify(callback).onWalletsClick()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Preview transfer").performClick()
        verify(callback).onNextClick()
    }

    @Test
    fun addressEditsReachCallback() {
        compose.onNode(hasSetTextAction()).performTextInput("0x1234")
        verify(callback).onAddressInput("0x1234")
    }

    @Test
    fun disabledPreviewCannotAdvance() {
        compose.runOnIdle { state.value = state.value.copy(buttonState = ButtonViewState("Preview transfer", false)) }
        compose.onNodeWithText("Preview transfer").assertIsNotEnabled().performClick()
        verifyNoInteractions(callback)
    }
}
