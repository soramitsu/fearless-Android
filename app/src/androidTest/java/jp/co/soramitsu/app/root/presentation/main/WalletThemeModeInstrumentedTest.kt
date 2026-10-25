package jp.co.soramitsu.app.root.presentation.main

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.tokens.NightThemeColors
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletThemeModeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    @Test fun bothHostsKeepDarkColorsThroughSystemModeAndFontChanges() {
        data class Mode(val legacy: Boolean, val night: Boolean, val font: Float)
        val modes = listOf(false, true).flatMap { legacy -> listOf(1f, 2f).flatMap { font ->
            listOf(true, false).map { night -> Mode(legacy, night, font) }
        } }
        var mode by mutableStateOf(modes.first())
        var page = 0
        var foreground = 0
        var materialLight = true
        val label = InstrumentationRegistry.getArguments().getString("themeEvidenceLabel") ?: "current"
        require(label.matches(Regex("[a-z0-9-]+")))
        val folder = compose.activity.getExternalFilesDir("theme-mode-$label")!!.apply { mkdirs() }
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (mode.night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, mode.font)
            ) {
                val fixture: @Composable () -> Unit = {
                    page = MaterialTheme.customColors.bgPage.toArgb()
                    foreground = MaterialTheme.customColors.fgPrimary.toArgb()
                    materialLight = MaterialTheme.colors.isLight
                    Column(Modifier.requiredWidth(320.dp).requiredHeight(430.dp)
                        .background(MaterialTheme.customColors.bgPage).testTag("theme-fixture").padding(16.dp)) {
                        Text("Send", color = MaterialTheme.customColors.fgPrimary, style = MaterialTheme.customTypography.headline2)
                        Text("Available: 100 XOR", color = MaterialTheme.customColors.fgSecondary,
                            style = MaterialTheme.customTypography.textM)
                        AmountInput(AmountInputViewState(tokenName = "XOR", totalBalance = "100 XOR", fiatAmount = "$10.00",
                            tokenAmount = BigDecimal.TEN, title = "Amount"))
                    }
                }
                if (mode.legacy) FearlessTheme(content = fixture) else FearlessAppTheme(content = fixture)
            }
        }
        val rows = JSONArray()
        val failures = mutableListOf<String>()
        for (next in modes) {
            compose.runOnIdle { mode = next }
            compose.waitForIdle()
            val name = "${if (next.legacy) "legacy" else "modern"}-${if (next.night) "night" else "day"}-font${(next.font * 100).toInt()}"
            compose.onNodeWithTag("theme-fixture").captureToImage().asAndroidBitmap().let { bitmap ->
                File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.runOnIdle {
                val pass = page == NightThemeColors.BgPage.toArgb() && foreground == NightThemeColors.FgPrimary.toArgb() && !materialLight
                rows.put(JSONObject().put("name", name).put("bgPage", page).put("fgPrimary", foreground)
                    .put("materialIsLight", materialLight).put("darkPalette", pass))
                if (!pass) failures.add(name)
            }
        }
        File(folder, "observations.json").writeText(JSONObject().put("cases", rows).toString(2))
        assertTrue("Light palette leaked in: $failures", failures.isEmpty())
    }
}
