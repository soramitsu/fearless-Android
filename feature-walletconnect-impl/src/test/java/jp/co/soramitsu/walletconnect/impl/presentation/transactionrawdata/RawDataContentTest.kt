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
    @OptIn(androidx.compose.runtime.InternalComposeApi::class)
    fun rendersRawDataPreviewWithReadableContent() {
        val preview = Class.forName("jp.co.soramitsu.walletconnect.impl.presentation.transactionrawdata.RawDataContentKt")
            .getDeclaredMethod("RawDataPreview", androidx.compose.runtime.Composer::class.java, Int::class.javaPrimitiveType)
        preview.isAccessible = true
        compose.setContent {
            FearlessAppTheme {
                preview.invoke(null, androidx.compose.runtime.currentComposer, 0)
            }
        }
        compose.onNodeWithText("Transaction raw data").assertExists()
        compose.onNodeWithText("Lorem ipsum", substring = true, ignoreCase = true).assertExists()
    }

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
