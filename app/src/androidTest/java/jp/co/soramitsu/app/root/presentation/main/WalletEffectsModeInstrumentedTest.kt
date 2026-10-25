package jp.co.soramitsu.app.root.presentation.main

import android.Manifest
import android.app.ActivityManager
import android.app.UiModeManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.fw.FwAppearance
import jp.co.soramitsu.common.compose.theme.fw.FwAppearancePreferences
import jp.co.soramitsu.common.compose.theme.fw.FwCalm
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwHighContrast
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.FwTierChoice
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.common.compose.theme.tokens.NightThemeColors
import jp.co.soramitsu.ui_core.theme.customColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletEffectsModeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    private data class Observation(val effects: FwEffects, val secondary: Int, val page: Int)

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun bothHostsObserveActualSystemContrastAndPowerSaveChanges() {
        val context = compose.activity
        val appearance = FwAppearancePreferences(context)
        val raw = context.getSharedPreferences(FwAppearancePreferences.STORE, 0)
        val originalPreferences = raw.all.toMap()
        // AOSP/CTS uses this secure setting to exercise UiModeManager's actual contrast callback.
        val contrastKey = "contrast_level"
        val power = context.getSystemService(PowerManager::class.java)
        val originalPowerSave = power.isPowerSaveMode
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
        val originalContrast = shell("settings get secure $contrastKey")
        check(originalContrast == "null" || originalContrast.matches(Regex("[-+0-9.eE]+")))
        assertFalse("This qualification device must support the Full tier", context.getSystemService(ActivityManager::class.java).isLowRamDevice)
        var legacy by mutableStateOf(false)
        val observed = AtomicReference<Observation>()
        fun awaitEffects(tier: FwTier, highContrast: Boolean) {
            compose.waitUntil(10_000) {
                observed.get()?.let { it.effects.tier == tier && it.effects.highContrast == highContrast } == true
            }
            compose.runOnIdle {
                assertEquals(NightThemeColors.BgPage.toArgb(), observed.get().page)
                assertFalse(observed.get().effects.liveBlur)
                assertEquals((if (highContrast) FwHighContrast else FwStandard).textSecondary.toArgb(), observed.get().secondary)
            }
        }
        fun setPowerSave(enabled: Boolean) {
            shell("cmd power set-mode ${if (enabled) 1 else 0}")
            compose.waitUntil(10_000) { power.isPowerSaveMode == enabled }
            // Battery saver also changes system night mode. The empty debug host handles uiMode
            // so this test observes the live callbacks instead of losing its injected content.
            assertFalse(context.isDestroyed)
            assertEquals(context, compose.activity)
        }
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        try {
            assertTrue(appearance.save(FwAppearance()))
            shell("settings put secure $contrastKey 0")
            setPowerSave(false)
            compose.setContent {
                val body: @Composable () -> Unit = {
                    val effects = LocalFwEffects.current
                    observed.set(Observation(effects, LocalFwColors.current.textSecondary.toArgb(), MaterialTheme.customColors.bgPage.toArgb()))
                    Text("System effects: ${effects.tier}; contrast: ${effects.highContrast}")
                }
                if (legacy) FearlessTheme(content = body) else FearlessAppTheme(content = body)
            }
            for (useLegacy in listOf(false, true)) {
                compose.runOnIdle { legacy = useLegacy }
                awaitEffects(FwTier.Full, false)
                shell("settings put secure $contrastKey 1")
                awaitEffects(FwTier.Reduced, true)
                shell("settings put secure $contrastKey 0.49")
                awaitEffects(FwTier.Full, false)
                shell("settings put secure $contrastKey 0.5")
                awaitEffects(FwTier.Reduced, true)
                setPowerSave(true)
                awaitEffects(FwTier.Reduced, true)
                shell("settings put secure $contrastKey 0")
                awaitEffects(FwTier.Lite, false)
                setPowerSave(false)
                awaitEffects(FwTier.Full, false)
            }
        } finally {
            try {
                if (originalContrast == "null") shell("settings delete secure $contrastKey")
                else shell("settings put secure $contrastKey $originalContrast")
                assertTrue(raw.edit().clear().apply {
                    for ((key, value) in originalPreferences) {
                        when (value) {
                            is String -> putString(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }.commit())
                setPowerSave(originalPowerSave)
            } finally {
                automation.dropShellPermissionIdentity()
            }
        }
        assertEquals(originalPreferences, raw.all)
        assertEquals(originalContrast, shell("settings get secure $contrastKey"))
        assertEquals(originalPowerSave, power.isPowerSaveMode)
    }

    @Test fun bothHostsObserveDisplayPreferencesAndSystemMotionAndKeepCalmAcrossNestedHosts() {
        val context = compose.activity
        val appearance = FwAppearancePreferences(context)
        val raw = context.getSharedPreferences(FwAppearancePreferences.STORE, 0)
        val originalPreferences = raw.all.toMap()
        val resolver = context.contentResolver
        val originalScale = Settings.Global.getString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // The owned device contract requires standard system contrast; in-app contrast is toggled below.
        if (Build.VERSION.SDK_INT >= 34) assertTrue(context.getSystemService(UiModeManager::class.java).contrast < 0.5f)
        var legacy by mutableStateOf(false)
        var calm by mutableStateOf(false)
        val observed = AtomicReference<Observation>()
        fun awaitEffects(predicate: (Observation) -> Boolean) {
            compose.waitUntil(10_000) { observed.get()?.let(predicate) == true }
            compose.runOnIdle {
                assertEquals(NightThemeColors.BgPage.toArgb(), observed.get().page)
                assertFalse(observed.get().effects.liveBlur)
            }
        }
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        try {
            assertTrue(appearance.save(FwAppearance(FwTierChoice.Lite)))
            assertTrue(Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
            compose.setContent {
                val body: @Composable () -> Unit = {
                    val colors = LocalFwColors.current
                    val effects = LocalFwEffects.current
                    observed.set(Observation(effects, colors.textSecondary.toArgb(), MaterialTheme.customColors.bgPage.toArgb()))
                    Text("Effects: ${effects.tier}; contrast: ${effects.highContrast}; motion: ${effects.reduceMotion}")
                }
                val host: @Composable () -> Unit = {
                    if (legacy) FearlessTheme(content = body) else FearlessAppTheme(content = body)
                }
                if (calm) FwCalm(content = host) else host()
            }
            for (useLegacy in listOf(false, true)) {
                compose.runOnIdle { legacy = useLegacy }
                assertTrue(appearance.save(FwAppearance(FwTierChoice.Lite)))
                awaitEffects { it.effects.tier == FwTier.Lite && !it.effects.highContrast && !it.effects.reduceMotion }
                assertEquals(FwStandard.textSecondary.toArgb(), observed.get().secondary)
                assertTrue(appearance.save(FwAppearance(FwTierChoice.Automatic, true)))
                awaitEffects { it.effects.tier == FwTier.Reduced && it.effects.highContrast }
                assertEquals(FwHighContrast.textSecondary.toArgb(), observed.get().secondary)
                compose.runOnIdle { calm = true }
                awaitEffects { it.effects.calm && it.effects.highContrast && !it.effects.glass }
                assertTrue(Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f))
                awaitEffects { it.effects.calm && it.effects.reduceMotion }
                assertTrue(Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
                awaitEffects { it.effects.calm && !it.effects.reduceMotion }
                compose.runOnIdle { calm = false }
                awaitEffects { !it.effects.calm }
            }
        } finally {
            Settings.Global.putString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, originalScale)
            raw.edit().clear().apply {
                for ((key, value) in originalPreferences) {
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }.commit()
            automation.dropShellPermissionIdentity()
        }
        assertEquals(originalPreferences, raw.all)
        assertEquals(originalScale, Settings.Global.getString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE))
    }
}
