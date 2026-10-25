package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.data.network.send.PendingWalletSend
import jp.co.soramitsu.common.data.network.send.ReviewedWalletSend
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendRecoverySnapshot
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendRecoveryDialog
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendRecoveryUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeSendUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    @Test fun retainedOriginalPaymentAndAllRecoveryActionsRemainReachableAtDoubleTextSize() {
        var scale by mutableStateOf(compose.activity.resources.configuration.fontScale)
        var snapshot by mutableStateOf(payment())
        var checks = 0; var retries = 0; var explorers = 0; var closes = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                FearlessAppTheme {
                    key(scale) { NativeSendRecoveryDialog(NativeSendRecoveryUiState(snapshot), { closes++ }, { checks++ }, { retries++ }, { explorers++ }) }
                }
            }
        }
        val check = compose.activity.getString(jp.co.soramitsu.common.R.string.send_check_status)
        val retry = compose.activity.getString(jp.co.soramitsu.common.R.string.send_retry_same_payment)
        val explorer = compose.activity.getString(jp.co.soramitsu.common.R.string.send_view_explorer)
        val close = compose.activity.getString(jp.co.soramitsu.common.R.string.common_close)
        for (font in listOf(compose.activity.resources.configuration.fontScale)) {
            compose.runOnIdle { scale = font; snapshot = payment() }
            compose.onNodeWithText("Solana Devnet", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("1.25 USDC", substring = true).performScrollTo().assertIsDisplayed()
            save("native-recovery-original-${(font * 100).toInt()}")
            compose.onNodeWithText(check).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(retry).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(explorer).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(close).performScrollTo().assertIsDisplayed().performClick()
            save("native-recovery-actions-${(font * 100).toInt()}")
        }
        compose.runOnIdle { assertEquals(1, checks); assertEquals(1, retries); assertEquals(1, explorers); assertEquals(1, closes) }
    }

    @Test fun expiredAmbiguityHasNoRetryAndBusyStateDisablesAllActions() {
        var busy by mutableStateOf(false)
        compose.setContent { FearlessAppTheme { NativeSendRecoveryDialog(NativeSendRecoveryUiState(payment().copy(expired = true), busy), {}, {}, {}, {}) } }
        compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.common.R.string.send_retry_same_payment)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.common.R.string.send_pending_expired)).performScrollTo().assertIsDisplayed()
        save("native-recovery-expired-${(compose.activity.resources.configuration.fontScale * 100).toInt()}")
        compose.runOnIdle { busy = true }
        for (id in listOf(jp.co.soramitsu.common.R.string.send_check_status, jp.co.soramitsu.common.R.string.send_view_explorer,
            jp.co.soramitsu.common.R.string.common_close)) {
            compose.onNodeWithText(compose.activity.getString(id)).performScrollTo().assertIsNotEnabled()
        }
    }

    @Test fun legacyAndChangedNetworkRetainMetadataWithoutUnsafeActions() {
        var snapshot by mutableStateOf(payment().copy(solanaNetwork = null))
        var closes = 0
        compose.setContent { FearlessAppTheme {
            NativeSendRecoveryDialog(NativeSendRecoveryUiState(snapshot), { closes++ }, { error("Unsafe check") },
                { error("Unsafe retry") }, { error("Unsafe explorer") })
        } }
        for (legacy in listOf(true, false)) {
            compose.runOnIdle { snapshot = if (legacy) payment().copy(solanaNetwork = null) else payment().copy(
                currentSolanaGenesisHash = jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH) }
            compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.common.R.string.send_pending_network_unavailable)).performScrollTo().assertIsDisplayed()
            save("native-recovery-unbound-notice-$legacy-${(compose.activity.resources.configuration.fontScale * 100).toInt()}")
            compose.onNodeWithText("Solana Devnet", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("1.25 USDC", substring = true).performScrollTo().assertIsDisplayed()
            for (id in listOf(jp.co.soramitsu.common.R.string.send_check_status, jp.co.soramitsu.common.R.string.send_retry_same_payment,
                jp.co.soramitsu.common.R.string.send_view_explorer)) compose.onNodeWithText(compose.activity.getString(id)).assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(jp.co.soramitsu.common.R.string.common_close)).performScrollTo().assertIsDisplayed().performClick()
            save("native-recovery-unbound-$legacy-${(compose.activity.resources.configuration.fontScale * 100).toInt()}")
        }
        compose.runOnIdle { assertEquals(2, closes) }
    }

    private fun payment() = NativeSendRecoverySnapshot(1, "solana:devnet", "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk", 0,
        "1".repeat(64), PendingWalletSend.State.Pending, reviewed = ReviewedWalletSend("solana:devnet", "Solana Devnet", "mint", "mint",
            "USDC", 6, "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk", "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk", "1250000"),
        feeUnits = "5005", accountRentUnits = "2039280", kind = PendingWalletSend.Kind.Solana,
        solanaNetwork = jp.co.soramitsu.common.data.network.send.SolanaSendNetworkIdentity(
            jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_DEVNET_GENESIS_HASH,
            jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_DEVNET_GENESIS_HASH),
        currentSolanaGenesisHash = jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_DEVNET_GENESIS_HASH)

    private fun save(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(300)
        val bitmap = (0 until 5).firstNotNullOfOrNull {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also { image ->
                if (image == null) android.os.SystemClock.sleep(200)
            }
        } ?: error("Screenshot unavailable after five attempts")
        val directory = compose.activity.getExternalFilesDir("wp9-evidence")!!.apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
