package jp.co.soramitsu.app.root.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.account.impl.domain.recovery.ArchivedWalletRecovery
import jp.co.soramitsu.account.impl.presentation.recovery.ArchivedWalletRecoveryScreen
import jp.co.soramitsu.account.impl.presentation.recovery.ArchivedWalletRecoveryViewModel
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Public fixture only; verifies actual hit targets and scroll reachability at narrow double text size. */
@RunWith(AndroidJUnit4::class)
class ArchivedWalletUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private val record = ArchivedWalletRecovery.Record("wallet_secret_archive:v1:7:ACCESS_SECRETS",
        listOf(ArchivedWalletRecovery.Address(WalletSecretScope.Ethereum, "0x7777777777777777777777777777777777777777")),
        1, true, EncryptedPreferenceSnapshot.unencrypted("public fixture exact value"))

    @Test fun restoredAddressAndExactExportRemainReachableAtDoubleTextSize() {
        var state by mutableStateOf(ArchivedWalletRecoveryViewModel.State(records = listOf(record), busy = false))
        var restored: WalletSecretScope? = null
        var shared: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FearlessTheme {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(440.dp).background(Color.Black)) {
                        ArchivedWalletRecoveryScreen(state, {}, {}, {},
                            { selected, scope -> state = state.copy(confirmation = ArchivedWalletRecoveryViewModel.Confirmation(selected, scope)) }, {},
                            { restored = state.confirmation?.scope; state = state.copy(confirmation = null) },
                            { state = state.copy(confirmation = null) },
                            { state = state.copy(export = "public fixture exact value") },
                            { state = state.copy(export = null) }, { shared = it })
                    }
                }
            }
        }
        compose.onNode(hasText(text(R.string.wallet_recovery_restore)) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.wallet_archive_restore_confirm)).assertIsDisplayed()
        capture("archive-restore-confirm.png")
        compose.onNode(hasText(text(R.string.wallet_recovery_restore)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(WalletSecretScope.Ethereum, restored) }
        compose.onNodeWithText(text(R.string.wallet_archive_exact_data)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.wallet_archive_export_warning)).assertIsDisplayed()
        capture("archive-exact-data.png")
        compose.onNodeWithText(text(R.string.wallet_archive_share)).assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals("public fixture exact value", shared) }
        compose.onNodeWithText(text(R.string.common_close)).performClick()
    }

    @Test fun permanentDeletionShowsTwoDistinctConfirmationsAndSupportsCancellation() {
        var state by mutableStateOf(ArchivedWalletRecoveryViewModel.State(records = listOf(record), busy = false))
        var deleted = false
        compose.setContent {
            FearlessTheme {
                ArchivedWalletRecoveryScreen(state, {}, {}, {}, { _, _ -> },
                    { state = state.copy(confirmation = ArchivedWalletRecoveryViewModel.Confirmation(it, purgeStep = 1)) },
                    {
                        val request = state.confirmation!!
                        if (request.purgeStep == 1) state = state.copy(confirmation = ArchivedWalletRecoveryViewModel.Confirmation(record, purgeStep = 2))
                        else { deleted = true; state = state.copy(confirmation = null, records = emptyList()) }
                    }, { state = state.copy(confirmation = null) }, {}, {}, {})
            }
        }
        compose.onNodeWithText(text(R.string.wallet_archive_delete)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.wallet_archive_delete_first)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.wallet_archive_continue_delete)).performClick()
        compose.runOnIdle { assertEquals(false, deleted) }
        compose.onNodeWithText(text(R.string.wallet_archive_delete_final)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        compose.onNodeWithText(text(R.string.wallet_archive_delete)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.wallet_archive_continue_delete)).performClick()
        capture("archive-final-delete.png")
        compose.onNode(hasText(text(R.string.wallet_archive_delete)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(true, deleted) }
        compose.onNodeWithText(text(R.string.wallet_archive_empty)).performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Compose idleness precedes the platform dialog-window animation. Let
        // SurfaceFlinger present the settled window before collecting pixels.
        android.os.SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun text(id: Int) = compose.activity.getString(id)
}
