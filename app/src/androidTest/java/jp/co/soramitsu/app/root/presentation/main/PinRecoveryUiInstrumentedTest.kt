package jp.co.soramitsu.app.root.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery
import jp.co.soramitsu.account.impl.presentation.pincode.PinCodeViewModel
import jp.co.soramitsu.account.impl.presentation.pincode.PinRecoveryScreen
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryCenterScreen
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.account.impl.domain.recovery.WalletRecoveryInventory
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinRecoveryUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    @Test fun ownershipAndDeviceUnlockActionsRemainSeparateAndReachableAtDoubleTextSize() {
        val target = RecoveryTarget(7, "ethereum")
        var proof: Pair<RecoveryTarget, ImportMode>? = null
        var biometrics = 0
        var viewOnly = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FearlessTheme {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(440.dp)) {
                        PinRecoveryScreen(PinCodeViewModel.RecoveryState(loading = false, strongBiometric = true,
                            deviceCredential = true, choices = listOf(WalletPinRecovery.Choice("Public fixture", target))),
                            { biometrics++ }, { viewOnly++ }, { selected, mode -> proof = selected to mode }, {})
                    }
                }
            }
        }
        capture("pin-recovery-intro.png")
        compose.onNodeWithText(text(R.string.pin_recovery_biometric)).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, biometrics); assertEquals(0, viewOnly) }
        for ((label, mode) in listOf(R.string.wallet_recovery_use_phrase to ImportMode.MnemonicPhrase,
            R.string.wallet_recovery_use_seed to ImportMode.RawSeed, R.string.wallet_recovery_use_json to ImportMode.Json)) {
            compose.onNodeWithText(text(label)).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(target to mode, proof) }
        }
        compose.onNodeWithText(text(R.string.pin_recovery_view_only)).performScrollTo().assertIsDisplayed()
        capture("pin-recovery-view-only.png")
        compose.onNodeWithText(text(R.string.pin_recovery_view_only)).performClick()
        compose.runOnIdle { assertEquals(1, viewOnly); assertEquals(1, biometrics) }
    }

    @Test fun unavailableProofPathsAreHiddenAndExpiredProofOffersRetry() {
        var retried = false
        compose.setContent {
            FearlessTheme {
                PinRecoveryScreen(PinCodeViewModel.RecoveryState(loading = false, error = true), {}, {}, { _, _ -> }, { retried = true })
            }
        }
        compose.onNodeWithText(text(R.string.pin_recovery_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.pin_recovery_biometric)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.pin_recovery_view_only)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.pin_recovery_retry)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, retried) }
    }

    @Test fun viewOnlyRecoveryCenterHasAWorkingPinResetAction() {
        var opened = false
        var retriedStorage = false
        compose.setContent {
            FearlessTheme {
                RecoveryCenterScreen(WalletRecoveryInventory.State(emptyList(), 0), {}, { _, _ -> }, {}, {}, { _, _ -> },
                    pinUnreadable = true, resetPin = { opened = true },
                    previousSecureStorage = { retriedStorage = true })
            }
        }
        compose.onNodeWithText(text(R.string.pin_recovery_reset)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, opened) }
        compose.onNodeWithText(text(R.string.wallet_keyloss_retry)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, retriedStorage) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun text(id: Int) = compose.activity.getString(id)
}
