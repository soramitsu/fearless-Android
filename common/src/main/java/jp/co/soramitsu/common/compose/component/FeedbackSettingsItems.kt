package jp.co.soramitsu.common.compose.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.feedback.FeedbackCue
import jp.co.soramitsu.common.feedback.FeedbackPreferences
import jp.co.soramitsu.common.feedback.FeedbackSettings
import jp.co.soramitsu.common.feedback.TransferFeedback

/**
 * Settings rows "Sound effects" and "Haptic feedback" (brand kit SPEC part 2): both on by default,
 * kept in ordinary display preferences. [divider] draws the list's separator after each row.
 */
@Composable
fun FeedbackSettingsItems(divider: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { FeedbackPreferences(context) }
    val settings by remember(preferences) { preferences.observe() }.collectAsState(initial = remember(preferences) { preferences.read() })
    FeedbackSettingsRows(
        settings = settings,
        onSound = { preferences.setSoundEffects(!settings.soundEffects) },
        onHaptics = {
            val on = !settings.hapticFeedback
            if (preferences.setHapticFeedback(on) && on) TransferFeedback.play(context, FeedbackCue.Commit)
        },
        divider = divider
    )
}

@Composable
internal fun FeedbackSettingsRows(settings: FeedbackSettings, onSound: () -> Unit, onHaptics: () -> Unit, divider: @Composable () -> Unit) {
    SettingsItem(
        modifier = Modifier.testTag("settings_sound_effects"),
        icon = painterResource(R.drawable.ic_fw_sound_24),
        text = stringResource(R.string.fw_feedback_sound_effects),
        action = SettingsItemAction.Switch(settings.soundEffects),
        onClick = onSound
    )
    divider()
    SettingsItem(
        modifier = Modifier.testTag("settings_haptic_feedback"),
        icon = painterResource(R.drawable.ic_fw_haptics_24),
        text = stringResource(R.string.fw_feedback_haptic_feedback),
        action = SettingsItemAction.Switch(settings.hapticFeedback),
        onClick = onHaptics
    )
    divider()
}
