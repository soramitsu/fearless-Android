package jp.co.soramitsu.common.data.storage.pin

import jp.co.soramitsu.common.data.storage.Preferences

/**
 * Wrong-PIN delays shared by every place that checks the wallet PIN, so that
 * no entry point offers more guesses than another: five attempts without a
 * delay, then a wait that starts at 30 seconds and doubles with each further
 * wrong PIN up to five minutes. Callers compare no PIN while a delay runs.
 *
 * The count is kept in preferences, so it survives restarts, and only the right
 * PIN, a biometric unlock or a newly saved PIN clear it. The limiter never
 * reads, changes or deletes the PIN or any wallet key.
 */
class PinAttemptLimiter(
    private val preferences: Preferences,
    private val clock: PinAttemptClock = PinAttemptClock.system
) {

    /** Milliseconds until the PIN may be checked again; zero when it may be checked now. */
    fun remainingDelayMillis(): Long {
        synchronized(lock) {
            val scheduled = delayAfterFailures(storedFailures())
            if (preferences.contains(PREFS_LEGACY_LOCK_UNTIL_MS)) adoptLegacyLock(scheduled)
            if (scheduled == 0L) return 0L
            val start = storedStart() ?: return 0L
            val sinceBoot = clock.sinceBoot()
            val wallClock = clock.wallClock()
            val remaining = remainingMillis(scheduled, start, sinceBoot, wallClock)
            if (remaining == 0L) {
                // This delay is over. Forgetting when it started keeps a later restart from bringing it back.
                forgetStart()
            } else if (sinceBoot < start.sinceBoot) {
                // First look since a restart: move the start onto this boot's clock, so the delay keeps
                // counting down and cannot come back once this boot's time passes the old value.
                val elapsed = scheduled - remaining
                saveStart(DelayStart(saturatedMinus(sinceBoot, elapsed), saturatedMinus(wallClock, elapsed)))
            }
            return remaining
        }
    }

    /** Counts a PIN that was checked and did not match. Returns the delay before the next check. */
    fun registerFailure(): Long {
        synchronized(lock) {
            val previous = storedFailures()
            val failures = if (previous < Int.MAX_VALUE) previous + 1 else previous
            val delay = delayAfterFailures(failures)
            if (delay > 0L) saveStart(DelayStart(clock.sinceBoot(), clock.wallClock())) else forgetStart()
            preferences.putInt(PREFS_PIN_FAILED_ATTEMPTS, failures)
            preferences.removeField(PREFS_LEGACY_LOCK_UNTIL_MS)
            return delay
        }
    }

    /** Clears the count after the right PIN, a biometric unlock or a newly saved PIN. */
    fun reset() {
        synchronized(lock) {
            preferences.removeField(PREFS_PIN_FAILED_ATTEMPTS)
            forgetStart()
            preferences.removeField(PREFS_LEGACY_LOCK_UNTIL_MS)
        }
    }

    /**
     * Releases before this one kept the end of a delay on the wall clock, where a clock set back
     * stretches it. Only what is left of that delay carries over, and never more than this schedule
     * allows for the stored count. The count itself stays.
     */
    private fun adoptLegacyLock(scheduled: Long) {
        val lockUntil = storedLong(PREFS_LEGACY_LOCK_UNTIL_MS)
        val wallClock = clock.wallClock()
        val remaining = if (lockUntil == null) 0L else saturatedMinus(lockUntil, wallClock).coerceIn(0L, scheduled)
        if (remaining > 0L) {
            val elapsed = scheduled - remaining
            saveStart(DelayStart(saturatedMinus(clock.sinceBoot(), elapsed), saturatedMinus(wallClock, elapsed)))
        } else {
            forgetStart()
        }
        preferences.removeField(PREFS_LEGACY_LOCK_UNTIL_MS)
    }

    private fun storedFailures(): Int = try {
        preferences.getInt(PREFS_PIN_FAILED_ATTEMPTS, 0).coerceAtLeast(0)
    } catch (_: ClassCastException) {
        // A value of another type is no count: no delay, and the next wrong PIN replaces it.
        0
    }

    private fun storedStart(): DelayStart? {
        val sinceBoot = storedLong(PREFS_DELAY_START_SINCE_BOOT_MS) ?: return null
        val wallClock = storedLong(PREFS_DELAY_START_WALL_CLOCK_MS) ?: return null
        return DelayStart(sinceBoot, wallClock)
    }

    private fun storedLong(key: String): Long? = try {
        if (preferences.contains(key)) preferences.getLong(key, 0L) else null
    } catch (_: ClassCastException) {
        null
    }

    private fun saveStart(start: DelayStart) {
        preferences.putLong(PREFS_DELAY_START_SINCE_BOOT_MS, start.sinceBoot)
        preferences.putLong(PREFS_DELAY_START_WALL_CLOCK_MS, start.wallClock)
    }

    private fun forgetStart() {
        preferences.removeField(PREFS_DELAY_START_SINCE_BOOT_MS)
        preferences.removeField(PREFS_DELAY_START_WALL_CLOCK_MS)
    }

    /** When the current delay started, on each clock. */
    internal data class DelayStart(val sinceBoot: Long, val wallClock: Long)

    companion object {
        const val ATTEMPTS_WITHOUT_DELAY = 5
        const val FIRST_DELAY_MILLIS = 30_000L
        const val MAXIMUM_DELAY_MILLIS = 5 * 60_000L

        private const val PREFS_PIN_FAILED_ATTEMPTS = "prefs_pin_failed_attempts"
        private const val PREFS_DELAY_START_SINCE_BOOT_MS = "prefs_pin_delay_start_since_boot_ms"
        private const val PREFS_DELAY_START_WALL_CLOCK_MS = "prefs_pin_delay_start_wall_clock_ms"

        /** The end of a delay on the wall clock, as releases before this one stored it. */
        private const val PREFS_LEGACY_LOCK_UNTIL_MS = "prefs_pin_lock_until_ms"
        private const val MAX_DOUBLINGS = 16
        private const val MILLIS_IN_SECOND = 1_000L

        /** Limiters made in different places share one count, so their updates take one lock. */
        private val lock = Any()

        fun delayAfterFailures(failures: Int): Long {
            if (failures < ATTEMPTS_WITHOUT_DELAY) return 0L
            val doublings = minOf(failures - ATTEMPTS_WITHOUT_DELAY, MAX_DOUBLINGS)
            return minOf(FIRST_DELAY_MILLIS shl doublings, MAXIMUM_DELAY_MILLIS)
        }

        /** Whole seconds to show for a running delay, rounded up so that it never reads zero. */
        fun secondsLeft(remainingMillis: Long): Int {
            val bounded = remainingMillis.coerceIn(1L, MAXIMUM_DELAY_MILLIS)
            return ((bounded + MILLIS_IN_SECOND - 1) / MILLIS_IN_SECOND).toInt()
        }

        /**
         * Time since boot is the reference: the user cannot move it, so a changed wall clock neither
         * shortens nor extends a delay while the device stays on. After a restart that clock starts
         * again from zero. The time since boot has then certainly passed, and a wall clock that agrees
         * may show more. The result never exceeds the scheduled delay and keeps counting down in real
         * time, so no clock change can cause a long or permanent lockout. A restart noticed only after
         * this boot's time passed the recorded value counts as the same boot, which can only lengthen
         * the wait, never beyond the scheduled delay.
         */
        internal fun remainingMillis(scheduled: Long, start: DelayStart, sinceBoot: Long, wallClock: Long): Long {
            if (scheduled <= 0L) return 0L
            val elapsed = if (sinceBoot >= start.sinceBoot) {
                saturatedMinus(sinceBoot, start.sinceBoot)
            } else {
                maxOf(sinceBoot, saturatedMinus(wallClock, start.wallClock))
            }
            return saturatedMinus(scheduled, elapsed).coerceIn(0L, scheduled)
        }

        private fun saturatedMinus(a: Long, b: Long): Long = try {
            Math.subtractExact(a, b)
        } catch (_: ArithmeticException) {
            if (b < 0L) Long.MAX_VALUE else Long.MIN_VALUE
        }
    }
}
