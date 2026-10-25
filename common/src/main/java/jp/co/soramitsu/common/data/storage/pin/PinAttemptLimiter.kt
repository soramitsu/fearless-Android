package jp.co.soramitsu.common.data.storage.pin

import jp.co.soramitsu.common.data.storage.Preferences

/**
 * The wallet PIN's failed-attempt lockout, shared by every place that checks
 * the PIN so that no entry point offers more guesses than another: five free
 * attempts, then a lock that doubles from 30 seconds up to 15 minutes.
 */
class PinAttemptLimiter(
    private val preferences: Preferences,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    /** The end of the current lock in epoch milliseconds, or null when a guess is allowed. */
    fun lockedUntil(): Long? {
        val lockUntil = preferences.getLong(PREFS_PIN_LOCK_UNTIL_MS, 0L)
        if (lockUntil > nowProvider()) {
            return lockUntil
        }
        if (lockUntil != 0L) {
            reset()
        }
        return null
    }

    fun registerFailure() {
        val attempts = preferences.getInt(PREFS_PIN_FAILED_ATTEMPTS, 0) + 1
        preferences.putInt(PREFS_PIN_FAILED_ATTEMPTS, attempts)

        if (attempts >= PIN_FREE_ATTEMPTS) {
            val exponent = (attempts - PIN_FREE_ATTEMPTS).coerceAtLeast(0).coerceAtMost(MAX_LOCK_DOUBLINGS)
            val lockSeconds = (PIN_LOCK_BASE_SECONDS * (1L shl exponent)).coerceAtMost(PIN_LOCK_MAX_SECONDS)
            preferences.putLong(PREFS_PIN_LOCK_UNTIL_MS, nowProvider() + lockSeconds * MILLIS_IN_SECOND)
        }
    }

    fun reset() {
        preferences.removeField(PREFS_PIN_FAILED_ATTEMPTS)
        preferences.removeField(PREFS_PIN_LOCK_UNTIL_MS)
    }

    private companion object {
        const val PREFS_PIN_FAILED_ATTEMPTS = "prefs_pin_failed_attempts"
        const val PREFS_PIN_LOCK_UNTIL_MS = "prefs_pin_lock_until_ms"
        const val PIN_FREE_ATTEMPTS = 5
        const val PIN_LOCK_BASE_SECONDS = 30L
        const val PIN_LOCK_MAX_SECONDS = 15 * 60L
        const val MAX_LOCK_DOUBLINGS = 10
        const val MILLIS_IN_SECOND = 1_000L
    }
}
