package jp.co.soramitsu.common.compose.theme.fw

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Keep settings live while a fragment/sheet is visible, and refresh after returning from Settings. */
@Composable
fun rememberFwEffects(): State<FwEffects> {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preferences = remember(context) { FwAppearancePreferences(context) }
    val state = remember(context) { mutableStateOf(readFwEffects(context, preferences)) }
    DisposableEffect(context, lifecycle, preferences) {
        val refresh = { state.value = readFwEffects(context, preferences) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = refresh()
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
        preferences.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        val lifecycleListener = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.addObserver(lifecycleListener)
        val contrast = if (Build.VERSION.SDK_INT >= 34) observeSystemContrast(context, refresh) else null
        refresh()
        onDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
            preferences.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            lifecycle.removeObserver(lifecycleListener)
            contrast?.close()
        }
    }
    return state
}

private fun readFwEffects(context: Context, preferences: FwAppearancePreferences): FwEffects = resolveFwEffects(
    sdkInt = Build.VERSION.SDK_INT,
    lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice ?: true,
    powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode ?: true,
    systemContrast = if (Build.VERSION.SDK_INT >= 34) context.getSystemService(UiModeManager::class.java)?.contrast ?: 0f else 0f,
    // The settings observer can run before ValueAnimator's cached scale is refreshed.
    animatorsEnabled = Settings.Global.getString(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        ?.toFloatOrNull()?.let { it > 0f } ?: ValueAnimator.areAnimatorsEnabled(),
    appearance = preferences.read()
)

@RequiresApi(34)
private fun observeSystemContrast(context: Context, refresh: () -> Unit): AutoCloseable {
    val manager = context.getSystemService(UiModeManager::class.java)
    val listener = UiModeManager.ContrastChangeListener { refresh() }
    manager?.addContrastChangeListener(ContextCompat.getMainExecutor(context), listener)
    return AutoCloseable { manager?.removeContrastChangeListener(listener) }
}
