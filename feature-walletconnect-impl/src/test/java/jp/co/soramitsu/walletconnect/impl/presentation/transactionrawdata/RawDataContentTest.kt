package jp.co.soramitsu.walletconnect.impl.presentation.transactionrawdata

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class RawDataContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun rawTransactionIsReadOnlyAndCloseDispatches() {
        val callback = mock<RawDataScreenInterface>()
        val raw = "{\"method\":\"personal_sign\",\"id\":7}"
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        compose.setContent { FearlessAppTheme { RawDataContent(RawDataViewState(raw), callback) } }
        compose.onNodeWithText(raw).assertIsDisplayed().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
        compose.onNodeWithText("Close").performClick()
        verify(callback).onClose()
    }
}
