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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.domain.recovery.WalletRecoveryInventory
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryCenterScreen
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.BannerWalletRecovery
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletRecoveryUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    @Test fun bannerOpensRecoveryAndMatchingTargetActionsRemainReachableAtDoubleTextSize() {
        var opened by mutableStateOf(false)
        var restored: Pair<RecoveryTarget, ImportMode>? = null
        var keptViewOnly = false
        val target = RecoveryTarget.from(7, WalletSecretScope.Ethereum)
        val state = WalletRecoveryInventory.State(listOf(WalletRecoveryInventory.Wallet(
            7, "Public test wallet", listOf(WalletRecoveryInventory.Item(target, WalletRecoveryKind.MISSING_KEY)), emptyList(), false
        )), 0)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FearlessAppTheme {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(440.dp).background(Color.Black)) {
                        if (!opened) BannerWalletRecovery { opened = true }
                        else RecoveryCenterScreen(state, { keptViewOnly = true }, { selected, mode -> restored = selected to mode }, {}, {}, { _, _ -> })
                    }
                }
            }
        }
        compose.onNodeWithText(text(R.string.wallet_recovery_fix_now)).assertHasClickAction().performClick()
        compose.onNodeWithText(text(R.string.wallet_recovery_center)).assertIsDisplayed()
        for (mode in listOf(
            R.string.wallet_recovery_use_phrase to ImportMode.MnemonicPhrase,
            R.string.wallet_recovery_use_seed to ImportMode.RawSeed,
            R.string.wallet_recovery_use_json to ImportMode.Json
        )) {
            compose.onNodeWithText(text(mode.first)).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(target to mode.second, restored) }
        }
        compose.onNodeWithText(text(R.string.wallet_recovery_keep_view_only)).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, keptViewOnly) }
    }

    @Test fun malformedIdentityOffersSeparateImportAndBackupAdvisoryOffersWorkingKeyBackup() {
        val target = RecoveryTarget.from(8, WalletSecretScope.Substrate)
        var corrected: RecoveryTarget? = null
        var backupId: Long? = null
        val state = WalletRecoveryInventory.State(listOf(WalletRecoveryInventory.Wallet(
            8, "Public fixture", listOf(WalletRecoveryInventory.Item(target, WalletRecoveryKind.IDENTITY_MISMATCH)),
            listOf(WalletRecoveryInventory.Advisory(WalletSecretScope.Ethereum, false)), false
        )), 0)
        compose.setContent {
            FearlessAppTheme {
                RecoveryCenterScreen(state, {}, { _, _ -> error("Must import as a separate wallet") }, { corrected = it }, {}, { id, _ -> backupId = id })
            }
        }
        compose.onNodeWithText(text(R.string.wallet_recovery_import_corrected)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(target, corrected) }
        compose.onNodeWithText(text(R.string.wallet_recovery_backup_key)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(8L, backupId) }
    }

    private fun text(id: Int) = compose.activity.getString(id)
}
