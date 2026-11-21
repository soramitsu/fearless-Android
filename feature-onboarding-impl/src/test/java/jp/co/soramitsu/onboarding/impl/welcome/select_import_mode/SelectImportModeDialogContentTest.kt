package jp.co.soramitsu.onboarding.impl.welcome.select_import_mode

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import jp.co.soramitsu.onboarding.impl.testutil.allowNextTap
import jp.co.soramitsu.onboarding.impl.testutil.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dialog is a Hilt entry point and cannot be attached here, so its content is rendered directly with a real view
 * model installed in place of the `by viewModels()` delegate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SelectImportModeDialogContentTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val router = mockk<OnboardingRouter>(relaxed = true)
    private val preinstalled = mockk<PendulumPreInstalledAccountsScenario> { every { isFeatureEnabled() } returns true }

    private fun dialogWith(viewModel: SelectImportModeViewModel) = SelectImportModeDialog().also { dialog ->
        SelectImportModeDialog::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }
            .set(dialog, lazyOf(viewModel))
    }

    @Test
    fun contentOffersTheWalletImportsWithoutGoogleWhenDetached() {
        val viewModel = SelectImportModeViewModel(router, SavedStateHandle(), preinstalled)
        val dialog = dialogWith(viewModel)
        allowNextTap()
        compose.setContent {
            val host = remember { dialog }
            FearlessAppTheme { host.Content(PaddingValues(0.dp)) }
        }
        compose.waitForIdle()

        // A detached dialog has no context to check for a browser, so Google sign-in is not offered.
        compose.onAllNodesWithText(text(R.string.select_import_mode_btn_google)).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.onboarding_preinstalled_wallet_button_text)).assertExists()
        compose.onNodeWithText(text(R.string.select_import_mode_btn_json)).performClick()
        compose.waitForIdle()

        verify(exactly = 1) { router.backWithResult(SelectImportModeDialog.RESULT_IMPORT_MODE to ImportMode.Json) }
    }

    @Test
    fun theSheetOpensFullyAndClosesWithoutAHalfState() {
        val dialog = dialogWith(SelectImportModeViewModel(router, SavedStateHandle(), preinstalled))
        val behavior = BottomSheetBehavior<FrameLayout>()

        SelectImportModeDialog::class.java.getDeclaredMethod("setupBehavior", BottomSheetBehavior::class.java)
            .apply { isAccessible = true }
            .invoke(dialog, behavior)

        assertEquals(BottomSheetBehavior.STATE_EXPANDED, behavior.state)
        assertTrue(behavior.isHideable)
        assertTrue(behavior.skipCollapsed)
    }
}
