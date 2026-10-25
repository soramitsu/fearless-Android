package jp.co.soramitsu.common.vibration

import android.annotation.SuppressLint
import android.os.Vibrator

class DeviceVibrator(
    private val vibrator: Vibrator
) {

    companion object {
        private const val SHORT_VIBRATION_DURATION = 200L
    }

    @SuppressLint("MissingPermission") // Permission declared in the manifest; VIBRATE is a normal permission.
    fun makeShortVibration() {
        vibrator.vibrate(SHORT_VIBRATION_DURATION)
    }
}
