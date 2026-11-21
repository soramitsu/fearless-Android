package jp.co.soramitsu.common.data.storage.pin

import android.os.SystemClock

/** The two clocks a wrong-PIN delay is measured with, both in milliseconds. */
class PinAttemptClock(
    /** Time since 1970. The user can change it. */
    val wallClock: () -> Long,
    /** Time since the device started, deep sleep included. The user cannot change it; it starts again from zero at boot. */
    val sinceBoot: () -> Long
) {
    companion object {
        val system = PinAttemptClock(wallClock = System::currentTimeMillis, sinceBoot = SystemClock::elapsedRealtime)
    }
}
