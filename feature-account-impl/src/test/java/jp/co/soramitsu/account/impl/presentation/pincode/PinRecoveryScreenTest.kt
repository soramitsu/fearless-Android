package jp.co.soramitsu.account.impl.presentation.pincode

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h3000dp")
class PinRecoveryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val network = WalletSecretScope.ChainAccount("ef".repeat(32))

    private fun render(state: PinCodeViewModel.RecoveryState) {
        compose.setContent {
            FearlessAppTheme {
                PinRecoveryScreen(
                    state = state,
                    biometric = { calls += "biometric" },
                    deviceCredential = { calls += "device" },
                    verifyBackup = { target, mode -> calls += "verify:${target.scopeId}:$mode" },
                    retry = { calls += "retry" }
                )
            }
        }
    }

    @Test
    fun forgottenPinOffersBiometricBackupsAndViewOnlyAccess() {
        render(
            PinCodeViewModel.RecoveryState(
                loading = false,
                strongBiometric = true,
                deviceCredential = true,
                forgotten = true,
                choices = listOf(
                    WalletPinRecovery.Choice("Ton wallet", RecoveryTarget.from(4, WalletSecretScope.Ton)),
                    WalletPinRecovery.Choice("Network wallet", RecoveryTarget.from(5, network))
                )
            )
        )

        compose.onNodeWithText(string(R.string.pin_recovery_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.pin_recovery_forgot_intro)).assertIsDisplayed()
        compose.onNodeWithText("Ton wallet").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_emergency_kind_ton)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_emergency_kind_network)).assertIsDisplayed()
        compose.onNodeWithText("0x" + "ef".repeat(32)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.pin_recovery_no_identity)).assertDoesNotExist()

        compose.onNodeWithText(string(R.string.pin_recovery_biometric)).tap()
        val phrase = compose.onAllNodesWithText(string(R.string.wallet_recovery_use_phrase))
        phrase[0].tap()
        phrase[1].tap()
        compose.onNodeWithText(string(R.string.wallet_recovery_use_seed)).tap()
        compose.onNodeWithText(string(R.string.wallet_recovery_use_json)).tap()
        compose.onNodeWithText(string(R.string.pin_recovery_view_only)).tap()
        compose.waitForIdle()

        val chain = "chain:" + "ef".repeat(32)
        assertEquals(
            listOf(
                "biometric",
                "verify:ton:${ImportMode.MnemonicPhrase}",
                "verify:$chain:${ImportMode.MnemonicPhrase}",
                "verify:$chain:${ImportMode.RawSeed}",
                "verify:$chain:${ImportMode.Json}",
                "device"
            ),
            calls
        )
    }

    @Test
    fun noBackupIdentityAndNoBiometricSaysSo() {
        render(PinCodeViewModel.RecoveryState(loading = false))

        compose.onNodeWithText(string(R.string.pin_recovery_intro)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.pin_recovery_no_identity)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.pin_recovery_biometric)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.pin_recovery_view_only)).assertDoesNotExist()
    }

    @Test
    fun loadingShowsNoActions() {
        render(PinCodeViewModel.RecoveryState(loading = true, strongBiometric = true))

        compose.onNodeWithText(string(R.string.pin_recovery_backup_intro)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.pin_recovery_biometric)).assertDoesNotExist()
    }

    @Test
    fun failedRecoveryOffersRetry() {
        render(PinCodeViewModel.RecoveryState(loading = false, error = true, strongBiometric = true))

        compose.onNodeWithText(string(R.string.pin_recovery_failed)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.pin_recovery_biometric)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.pin_recovery_retry)).tap()
        compose.waitForIdle()

        assertEquals(listOf("retry"), calls)
    }
}
