package jp.co.soramitsu.common.vibration

import android.os.Vibrator

class DeviceVibrator(
    private val vibrator: Vibrator
) {

    companion object {
        private const val SHORT_VIBRATION_DURATION = 200L
    }

    fun makeShortVibration() {
        vibrator.vibrate(SHORT_VIBRATION_DURATION)
    }
}
