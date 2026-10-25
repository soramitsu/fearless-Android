package jp.co.soramitsu.app.root.presentation.main

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import jp.co.soramitsu.account.impl.domain.recovery.WalletRecoveryInventory
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryCenterScreen
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.BannerWalletRecovery
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwHighContrast
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual recovery entry/back controls, not substitute preview buttons. */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
class WalletRecoveryFocusInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    @Test fun bannerHasVisibleKeyboardFocusWithOutsideGap() = qualify(banner = true)
    @Test fun backHasVisibleKeyboardFocusWithOutsideGap() = qualify(banner = false)

    private fun qualify(banner: Boolean) {
        check(FwRestyle.isEnabled(FwSurface.Recovery)) { "The focus regression requires the v1 Recovery artifact." }
        val modes = listOf(
            FwEffects(FwTier.Full, false, false), FwEffects(FwTier.Lite, false, false),
            FwEffects(FwTier.Reduced, false, false), FwEffects(FwTier.Full, true, false),
            FwEffects(FwTier.Full, false, true)
        )
        var current by mutableStateOf(0)
        var font by mutableStateOf(1f)
        var inputModeManager: InputModeManager? = null
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            val config = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(Locale.ENGLISH)) }
            val context = compose.activity.createConfigurationContext(config)
            CompositionLocalProvider(LocalConfiguration provides config, LocalContext provides context,
                LocalDensity provides Density(LocalDensity.current.density, font)) {
                FearlessTheme {
                    val fx = modes[current]
                    val colors = if (fx.highContrast) FwHighContrast else FwStandard
                    CompositionLocalProvider(LocalFwEffects provides fx, LocalFwColors provides colors) {
                        key(current, font) {
                            Box(Modifier.requiredWidth(320.dp).requiredHeight(640.dp).background(colors.bgBase)
                                .testTag("focus-host").padding(16.dp)) {
                                if (banner) BannerWalletRecovery {} else RecoveryCenterScreen(
                                    WalletRecoveryInventory.State(emptyList(), 0), {}, { _, _ -> }, {}, {}, { _, _ -> }
                                )
                            }
                        }
                    }
                }
            }
        }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocales(LocaleList(Locale.ENGLISH)) }
        val resources = compose.activity.createConfigurationContext(config).resources
        for (scale in listOf(1f, 2f)) for (index in modes.indices) {
            compose.runOnIdle { font = scale; current = index }
            val node = compose.onNodeWithText(resources.getString(if (banner) R.string.wallet_recovery_fix_now else R.string.ux_back))
                .assertIsDisplayed().assertHasClickAction()
            compose.runOnIdle { check(inputModeManager!!.requestInputMode(InputMode.Keyboard)) }
            node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            node.assertIsFocused()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val host = compose.onNodeWithTag("focus-host")
            val origin = host.fetchSemanticsNode().boundsInRoot
            val bitmap = host.captureToImage().asAndroidBitmap()
            val label = InstrumentationRegistry.getArguments().getString("recoveryFocusEvidenceLabel") ?: "default"
            val directory = compose.activity.getExternalFilesDir("recovery-focus-$label")!!.apply { mkdirs() }
            File(directory, "${if (banner) "banner" else "back"}-$scale-$index.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val density = compose.activity.resources.displayMetrics.density
            val y = (bounds.center.y - origin.top).toInt()
            val ringX = (bounds.left - origin.left - 3 * density).toInt()
            val gapX = (bounds.left - origin.left - density).toInt()
            for ((name, x, expected) in listOf(Triple("ring", ringX, FwStandard.focus), Triple("gap", gapX, FwStandard.bgBase))) {
                val actual = bitmap.getPixel(x, y)
                for (shift in listOf(0, 8, 16, 24)) assertTrue("${if (banner) "Banner" else "Back"} $scale/$index $name expected=${expected.toArgb()} actual=$actual",
                    kotlin.math.abs((expected.toArgb() ushr shift and 255) - (actual ushr shift and 255)) <= 1)
            }
        }
    }
}
