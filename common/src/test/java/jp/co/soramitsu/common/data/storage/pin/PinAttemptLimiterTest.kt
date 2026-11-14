package jp.co.soramitsu.common.data.storage.pin

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import jp.co.soramitsu.common.data.storage.InitialValueProducer
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.model.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wrong-PIN delays: the schedule, the stored count, clock changes, restarts and the upgrade from older releases. */
class PinAttemptLimiterTest {

    /** Wall clock and time since boot, moved by the test. */
    private class TestClock(var wall: Long = 1_800_000_000_000L, var boot: Long = 50_000_000L) {
        val clock = PinAttemptClock(wallClock = { wall }, sinceBoot = { boot })

        /** Real time passes: both clocks move together. */
        fun pass(millis: Long) {
            wall += millis
            boot += millis
        }

        /** The device restarts: time since boot starts again. */
        fun restart(after: Long, uptime: Long) {
            wall += after
            boot = uptime
        }
    }

    /**
     * Typed like SharedPreferences: reading a value as another type throws. Records removals, and
     * [restarted] reads the same stored values into a new object, as a new process would.
     */
    private class StoredPreferences(initial: Map<String, Any> = emptyMap()) : Preferences {
        val values = initial.toMutableMap()
        val removed = mutableListOf<String>()

        fun restarted() = StoredPreferences(values)

        override fun contains(field: String) = field in values
        override fun putString(field: String, value: String?) = put(field, value)
        override fun getString(field: String, defaultValue: String) = (values[field] ?: defaultValue) as String
        override fun getString(field: String) = values[field] as String?
        override fun putStringSet(field: String, value: Set<String>?) = put(field, value)
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(field: String, defaultValue: Set<String>) = (values[field] ?: defaultValue) as Set<String>
        override fun putBoolean(field: String, value: Boolean) = put(field, value)
        override fun getBoolean(field: String, defaultValue: Boolean) = (values[field] ?: defaultValue) as Boolean
        override fun putInt(field: String, value: Int) = put(field, value)
        override fun getInt(field: String, defaultValue: Int) = (values[field] ?: defaultValue) as Int
        override fun putLong(field: String, value: Long) = put(field, value)
        override fun getLong(field: String, defaultValue: Long) = (values[field] ?: defaultValue) as Long
        override fun getCurrentLanguage(): Language? = null
        override fun saveCurrentLanguage(languageIsoCode: String) = Unit
        override fun removeField(field: String) {
            removed += field
            values.remove(field)
        }
        override fun replaceStringsDurably(valuesToPut: Map<String, String>, keysToRemove: Set<String>): Boolean {
            values.putAll(valuesToPut)
            keysToRemove.forEach(::removeField)
            return true
        }
        override fun stringFlow(field: String, initialValueProducer: InitialValueProducer<String>?): Flow<String?> =
            flowOf(getString(field))
        override fun stringSetFlow(field: String, initialValueProducer: InitialValueProducer<Set<String>>?): Flow<Set<String>> =
            flowOf(getStringSet(field, emptySet()))
        override fun intFlow(field: String, initialValue: Int): Flow<Int> = flowOf(getInt(field, initialValue))
        override fun booleanFlow(field: String, initialValue: Boolean): Flow<Boolean> = flowOf(getBoolean(field, initialValue))

        private fun put(field: String, value: Any?) {
            if (value == null) values.remove(field) else values[field] = value
        }
    }

    private fun limiter(preferences: Preferences, clock: TestClock) = PinAttemptLimiter(preferences, clock.clock)

    /** What releases before this one stored: the count as an Int and the end of a lock on the wall clock as a Long. */
    private fun oldRelease(count: Int, lockUntil: Long) = StoredPreferences(mapOf(FAILED_ATTEMPTS to count, LEGACY_LOCK_UNTIL to lockUntil))

    private fun limiterAfter(failures: Int, preferences: Preferences, clock: TestClock): PinAttemptLimiter =
        limiter(preferences, clock).also { limiter -> repeat(failures) { limiter.registerFailure() } }

    @Test
    fun `schedule allows five attempts then doubles from thirty seconds up to five minutes`() {
        val expectedSeconds = listOf<Long>(0, 0, 0, 0, 0, 30, 60, 120, 240, 300, 300, 300)
        assertEquals(expectedSeconds.map { it * SECOND }, expectedSeconds.indices.map { PinAttemptLimiter.delayAfterFailures(it) })
        assertEquals(0L, PinAttemptLimiter.delayAfterFailures(-3))
        assertEquals(5 * MINUTE, PinAttemptLimiter.delayAfterFailures(10_000))
        assertEquals(5 * MINUTE, PinAttemptLimiter.delayAfterFailures(Int.MAX_VALUE))
        assertEquals(5 * MINUTE, PinAttemptLimiter.MAXIMUM_DELAY_MILLIS)
    }

    @Test
    fun `every wrong PIN is counted and the fifth starts the delay`() {
        val preferences = StoredPreferences(mapOf(PIN_KEY to PIN_CIPHERTEXT))
        val clock = TestClock()
        val limiter = limiter(preferences, clock)
        for (attempt in 1..4) {
            assertEquals("attempt $attempt", 0L, limiter.registerFailure())
            assertEquals("attempt $attempt", 0L, limiter.remainingDelayMillis())
        }
        assertEquals(30 * SECOND, limiter.registerFailure())
        assertEquals(30 * SECOND, limiter.remainingDelayMillis())
        clock.pass(29 * SECOND)
        assertEquals(SECOND, limiter.remainingDelayMillis())
        clock.pass(SECOND)
        assertEquals(0L, limiter.remainingDelayMillis())
        assertEquals(60 * SECOND, limiter.registerFailure())
        clock.pass(60 * SECOND)
        assertEquals(120 * SECOND, limiter.registerFailure())
        assertEquals(7, preferences.values[FAILED_ATTEMPTS])
        assertEquals(PIN_CIPHERTEXT, preferences.values[PIN_KEY])
    }

    @Test
    fun `looking after a delay ends does not reset the count`() {
        val preferences = StoredPreferences()
        val clock = TestClock()
        val limiter = limiterAfter(5, preferences, clock)
        clock.pass(30 * SECOND)
        repeat(3) { assertEquals(0L, limiter.remainingDelayMillis()) }
        // Releases before this one started over here: five guesses every 30 seconds, indefinitely.
        assertEquals(60 * SECOND, limiter.registerFailure())
        assertEquals(6, preferences.values[FAILED_ATTEMPTS])
    }

    @Test
    fun `count and delay survive a process restart`() {
        val preferences = StoredPreferences()
        val clock = TestClock()
        assertEquals(60 * SECOND, limiterAfter(6, preferences, clock).remainingDelayMillis())
        clock.pass(15 * SECOND)
        // A new process reads the stored values; time since boot keeps running across processes.
        val restarted = limiter(preferences.restarted(), clock)
        assertEquals(45 * SECOND, restarted.remainingDelayMillis())
        clock.pass(45 * SECOND)
        assertEquals(0L, restarted.remainingDelayMillis())
        assertEquals("A restart grants no fresh attempts", 120 * SECOND, restarted.registerFailure())
    }

    @Test
    fun `wall clock changes while the device stays on neither shorten nor extend the delay`() {
        val clock = TestClock()
        val limiter = limiterAfter(9, StoredPreferences(), clock)
        assertEquals(5 * MINUTE, limiter.remainingDelayMillis())
        clock.wall += YEAR
        assertEquals("Setting the clock forward does not end the delay", 5 * MINUTE, limiter.remainingDelayMillis())
        clock.wall -= 10 * YEAR
        assertEquals("Setting the clock back does not extend the delay", 5 * MINUTE, limiter.remainingDelayMillis())
        clock.boot += 2 * MINUTE
        assertEquals(3 * MINUTE, limiter.remainingDelayMillis())
        clock.boot += 3 * MINUTE
        assertEquals(0L, limiter.remainingDelayMillis())
    }

    @Test
    fun `restart with the clock set back still ends the delay in real time`() {
        val clock = TestClock()
        val limiter = limiterAfter(9, StoredPreferences(), clock)
        clock.restart(after = 20 * SECOND, uptime = 20 * SECOND)
        clock.wall -= 10 * YEAR
        assertEquals("The time since boot has certainly passed", 280 * SECOND, limiter.remainingDelayMillis())
        clock.pass(280 * SECOND)
        assertEquals(0L, limiter.remainingDelayMillis())
    }

    @Test
    fun `restart with the clock set forward counts the wall clock`() {
        val ahead = TestClock()
        val aheadLimiter = limiterAfter(9, StoredPreferences(), ahead)
        ahead.restart(after = 10 * MINUTE, uptime = MINUTE)
        assertEquals("Ten minutes on the wall clock and a restart since", 0L, aheadLimiter.remainingDelayMillis())

        val forward = TestClock()
        val forwardLimiter = limiterAfter(9, StoredPreferences(), forward)
        forward.restart(after = 10 * SECOND, uptime = 10 * SECOND)
        forward.wall += YEAR
        assertEquals("A restart and a clock set forward end one delay early", 0L, forwardLimiter.remainingDelayMillis())
        assertEquals("The next wrong PIN still waits longer", 5 * MINUTE, forwardLimiter.registerFailure())
        forward.wall += YEAR
        assertEquals("Within one boot the clock does nothing", 5 * MINUTE, forwardLimiter.remainingDelayMillis())

        val behind = TestClock()
        val behindLimiter = limiterAfter(9, StoredPreferences(), behind)
        behind.restart(after = 5 * SECOND, uptime = 100 * SECOND)
        assertEquals("The wall clock cannot show less than the time since boot", 200 * SECOND, behindLimiter.remainingDelayMillis())
    }

    @Test
    fun `restart carries the remaining delay over to the new boot`() {
        val preferences = StoredPreferences(mapOf(PIN_KEY to PIN_CIPHERTEXT))
        val clock = TestClock()
        val limiter = limiterAfter(9, preferences, clock)
        clock.restart(after = 40 * SECOND, uptime = 40 * SECOND)
        assertEquals(260 * SECOND, limiter.remainingDelayMillis())
        assertEquals("Carrying the delay over keeps the count", 9, preferences.values[FAILED_ATTEMPTS])
        clock.pass(60 * SECOND)
        assertEquals(200 * SECOND, limiter.remainingDelayMillis())
        assertEquals(200 * SECOND, limiter(preferences.restarted(), clock).remainingDelayMillis())
        assertEquals(PIN_CIPHERTEXT, preferences.values[PIN_KEY])
        assertFalse(PIN_KEY in preferences.removed)
    }

    @Test
    fun `delay ended by a restart does not come back later`() {
        val clock = TestClock()
        val limiter = limiterAfter(9, StoredPreferences(), clock)
        // The new boot has run almost as long as the old one had when the delay started.
        clock.restart(after = HOUR, uptime = 49_999_000L)
        assertEquals(0L, limiter.remainingDelayMillis())
        repeat(4) {
            clock.pass(SECOND)
            assertEquals("Passing the old time since boot does not start the delay again", 0L, limiter.remainingDelayMillis())
        }
    }

    @Test
    fun `ended delay stays ended through later restarts`() {
        val clock = TestClock()
        val limiter = limiterAfter(9, StoredPreferences(), clock)
        clock.restart(after = 20 * SECOND, uptime = 20 * SECOND)
        assertEquals(280 * SECOND, limiter.remainingDelayMillis())
        clock.pass(280 * SECOND)
        assertEquals(0L, limiter.remainingDelayMillis())
        clock.restart(after = 10 * SECOND, uptime = 30 * SECOND)
        assertEquals(0L, limiter.remainingDelayMillis())
        assertEquals("The count stays until a success", 5 * MINUTE, limiter.registerFailure())
    }

    @Test
    fun `delay running through two restarts stays within the schedule`() {
        val clock = TestClock()
        val limiter = limiterAfter(9, StoredPreferences(), clock)
        clock.restart(after = 20 * SECOND, uptime = 20 * SECOND)
        assertEquals(280 * SECOND, limiter.remainingDelayMillis())
        clock.pass(100 * SECOND)
        assertEquals(180 * SECOND, limiter.remainingDelayMillis())
        // A second restart that this boot's clock cannot reveal counts as the same boot.
        clock.restart(after = 10 * SECOND, uptime = 30 * SECOND)
        val remaining = limiter.remainingDelayMillis()
        assertTrue(remaining in 0..PinAttemptLimiter.MAXIMUM_DELAY_MILLIS)
        clock.pass(remaining)
        assertEquals(0L, limiter.remainingDelayMillis())
    }

    @Test
    fun `no clock change can cause a long or permanent lockout`() {
        val jumps = listOf(-10 * YEAR, -DAY, -HOUR, -1L, 0L, 1L, HOUR, DAY, 10 * YEAR)
        val restarts = listOf(null, 0L, 1L, 299_999L, 300_001L, 49_999_000L, 50_100_000L, DAY)
        for (jump in jumps) {
            for (uptime in restarts) {
                val clock = TestClock()
                val limiter = limiterAfter(40, StoredPreferences(), clock)
                uptime?.let { clock.restart(after = 0L, uptime = it) }
                clock.wall += jump
                val context = "jump $jump, restart uptime $uptime"
                var previous = limiter.remainingDelayMillis()
                assertTrue(context, previous in 0..PinAttemptLimiter.MAXIMUM_DELAY_MILLIS)
                repeat(5) {
                    clock.pass(MINUTE)
                    val remaining = limiter.remainingDelayMillis()
                    assertTrue("$context counts down", remaining <= maxOf(0L, previous - MINUTE))
                    previous = remaining
                }
                assertEquals(context, 0L, limiter.remainingDelayMillis())
            }
        }
    }

    @Test
    fun `stored values far out of range cannot cause a long lockout`() {
        val extremes = listOf(Long.MIN_VALUE, -YEAR, -1L, 0L, 1L, YEAR, Long.MAX_VALUE)
        for (uptime in listOf(10 * SECOND, 50_000_000L)) {
            for (startBoot in extremes) {
                for (startWall in extremes) {
                    val clock = TestClock(boot = uptime)
                    val preferences = StoredPreferences(
                        mapOf(FAILED_ATTEMPTS to Int.MAX_VALUE, START_SINCE_BOOT to startBoot, START_WALL_CLOCK to startWall)
                    )
                    val limiter = limiter(preferences, clock)
                    val context = "uptime $uptime, start $startBoot / $startWall"
                    assertTrue(context, limiter.remainingDelayMillis() in 0..PinAttemptLimiter.MAXIMUM_DELAY_MILLIS)
                    clock.pass(PinAttemptLimiter.MAXIMUM_DELAY_MILLIS)
                    assertEquals(context, 0L, limiter.remainingDelayMillis())
                }
            }
        }
    }

    @Test
    fun `success clears the count and touches nothing else`() {
        val preferences = StoredPreferences(mapOf(PIN_KEY to PIN_CIPHERTEXT, OTHER_KEY to true))
        val clock = TestClock()
        val limiter = limiterAfter(7, preferences, clock)
        assertEquals(120 * SECOND, limiter.remainingDelayMillis())
        // Biometrics can succeed while a delay runs.
        limiter.reset()
        assertEquals(0L, limiter.remainingDelayMillis())
        assertEquals(mapOf(PIN_KEY to PIN_CIPHERTEXT, OTHER_KEY to true), preferences.values)
        assertEquals(LIMITER_KEYS, preferences.removed.toSet())
        assertEquals("After a success the next typo is not delayed", 0L, limiter.registerFailure())
        assertEquals(1, preferences.values[FAILED_ATTEMPTS])
    }

    @Test
    fun `wrong PINs before the delay store only the count`() {
        val preferences = StoredPreferences(mapOf(OTHER_KEY to true))
        val clock = TestClock()
        val limiter = limiter(preferences, clock)
        assertEquals("A look with nothing counted writes nothing", 0L, limiter.remainingDelayMillis())
        assertEquals(mapOf(OTHER_KEY to true), preferences.values)
        repeat(4) { limiter.registerFailure() }
        assertEquals(mapOf(OTHER_KEY to true, FAILED_ATTEMPTS to 4), preferences.values)
        limiter.registerFailure()
        assertEquals(setOf(OTHER_KEY, FAILED_ATTEMPTS, START_SINCE_BOOT, START_WALL_CLOCK), preferences.values.keys)
        clock.pass(30 * SECOND)
        assertEquals(0L, limiter.remainingDelayMillis())
        assertEquals("An ended delay leaves only the count", mapOf(OTHER_KEY to true, FAILED_ATTEMPTS to 5), preferences.values)
    }

    @Test
    fun `old running lock keeps only what is left of it`() {
        val clock = TestClock()
        val preferences = oldRelease(count = 5, lockUntil = clock.wall + 20 * SECOND)
        val limiter = limiter(preferences, clock)
        assertEquals(20 * SECOND, limiter.remainingDelayMillis())
        assertFalse(LEGACY_LOCK_UNTIL in preferences.values)
        assertEquals(5, preferences.values[FAILED_ATTEMPTS])
        clock.wall -= YEAR
        assertEquals("The carried delay is timed by the time since boot", 20 * SECOND, limiter.remainingDelayMillis())
        clock.pass(20 * SECOND)
        assertEquals(0L, limiter.remainingDelayMillis())
        assertEquals(60 * SECOND, limiter.registerFailure())
    }

    @Test
    fun `old lock stretched by a clock set back is cut to the schedule`() {
        val clock = TestClock()
        // The clock went back a day after the old release locked for 30 seconds.
        val preferences = oldRelease(count = 5, lockUntil = clock.wall + DAY + 30 * SECOND)
        val limiter = limiter(preferences, clock)
        assertEquals(30 * SECOND, limiter.remainingDelayMillis())
        clock.pass(30 * SECOND)
        assertEquals(0L, limiter.remainingDelayMillis())
    }

    @Test
    fun `old fifteen minute lock is cut to the five minute cap`() {
        val clock = TestClock()
        val preferences = oldRelease(count = 10, lockUntil = clock.wall + 15 * MINUTE)
        val limiter = limiter(preferences, clock)
        assertEquals(5 * MINUTE, limiter.remainingDelayMillis())
        clock.pass(5 * MINUTE)
        assertEquals(0L, limiter.remainingDelayMillis())
    }

    @Test
    fun `old lock that already ended starts no delay and keeps the count`() {
        val clock = TestClock()
        val preferences = oldRelease(count = 5, lockUntil = clock.wall - SECOND)
        val limiter = limiter(preferences, clock)
        assertEquals(0L, limiter.remainingDelayMillis())
        assertEquals(mapOf<String, Any>(FAILED_ATTEMPTS to 5), preferences.values)
        assertEquals(60 * SECOND, limiter.registerFailure())
    }

    @Test
    fun `fixtures store counts with the old release's types`() {
        val preferences = oldRelease(count = 5, lockUntil = 1L)
        assertTrue(preferences.values[FAILED_ATTEMPTS] is Int)
        assertTrue(preferences.values[LEGACY_LOCK_UNTIL] is Long)
    }

    @Test
    fun `old counts below five carry over without a delay`() {
        val clock = TestClock()
        val preferences = StoredPreferences(mapOf(FAILED_ATTEMPTS to 3))
        val limiter = limiter(preferences, clock)
        assertEquals(0L, limiter.remainingDelayMillis())
        assertEquals(0L, limiter.registerFailure())
        assertEquals(30 * SECOND, limiter.registerFailure())
    }

    @Test
    fun `old deadline without a count is dropped`() {
        val clock = TestClock()
        val preferences = StoredPreferences(mapOf(LEGACY_LOCK_UNTIL to clock.wall + 10 * MINUTE, OTHER_KEY to true))
        assertEquals(0L, limiter(preferences, clock).remainingDelayMillis())
        assertEquals(mapOf<String, Any>(OTHER_KEY to true), preferences.values)
    }

    @Test
    fun `no old lock waits longer than the new schedule allows`() {
        val offsets = listOf(-10 * YEAR, -DAY, -1L, 0L, 1L, 30 * SECOND, 15 * MINUTE, DAY, 10 * YEAR, Long.MAX_VALUE / 2)
        val counts = listOf(0, 1, 4, 5, 6, 9, 10, 20, Int.MAX_VALUE)
        for (offset in offsets) {
            for (count in counts) {
                val clock = TestClock()
                val preferences = oldRelease(count, lockUntil = clock.wall + offset)
                val limiter = limiter(preferences, clock)
                val context = "offset $offset, count $count"
                val remaining = limiter.remainingDelayMillis()
                assertTrue(context, remaining in 0..PinAttemptLimiter.delayAfterFailures(count))
                assertFalse(context, LEGACY_LOCK_UNTIL in preferences.values)
                clock.pass(PinAttemptLimiter.MAXIMUM_DELAY_MILLIS)
                assertEquals(context, 0L, limiter.remainingDelayMillis())
            }
        }
    }

    @Test
    fun `values of another type delay nothing and the next count replaces them`() {
        val clock = TestClock()
        val preferences = StoredPreferences(
            mapOf<String, Any>(FAILED_ATTEMPTS to "nine", START_SINCE_BOOT to "x", START_WALL_CLOCK to 1, LEGACY_LOCK_UNTIL to "y")
        )
        val limiter = limiter(preferences, clock)
        assertEquals(0L, limiter.remainingDelayMillis())
        assertEquals(0L, limiter.registerFailure())
        assertEquals(mapOf<String, Any>(FAILED_ATTEMPTS to 1), preferences.values)

        val mistypedStart = StoredPreferences(mapOf<String, Any>(FAILED_ATTEMPTS to 9, START_SINCE_BOOT to clock.boot, START_WALL_CLOCK to "z"))
        assertEquals(9, mistypedStart.values[FAILED_ATTEMPTS])
        assertEquals("A delay without a readable start is not enforced", 0L, limiter(mistypedStart, clock).remainingDelayMillis())
    }

    @Test
    fun `limiters made in different places keep one exact count`() {
        val preferences = StoredPreferences()
        val clock = TestClock()
        val threads = 8
        val perThread = 25
        val executor = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        try {
            val tasks = (1..threads).map {
                executor.submit(
                    Runnable {
                        start.await()
                        val limiter = limiter(preferences, clock)
                        repeat(perThread) {
                            limiter.registerFailure()
                            limiter.remainingDelayMillis()
                        }
                    }
                )
            }
            start.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        assertEquals(threads * perThread, preferences.values[FAILED_ATTEMPTS])
    }

    @Test
    fun `seconds left are rounded up`() {
        assertEquals(1, PinAttemptLimiter.secondsLeft(1L))
        assertEquals(1, PinAttemptLimiter.secondsLeft(SECOND))
        assertEquals(2, PinAttemptLimiter.secondsLeft(SECOND + 1))
        assertEquals(30, PinAttemptLimiter.secondsLeft(29 * SECOND + 1))
        assertEquals(300, PinAttemptLimiter.secondsLeft(5 * MINUTE))
        assertEquals(300, PinAttemptLimiter.secondsLeft(Long.MAX_VALUE))
    }

    private companion object {
        const val SECOND = 1_000L
        const val MINUTE = 60 * SECOND
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        const val YEAR = 365 * DAY

        const val FAILED_ATTEMPTS = "prefs_pin_failed_attempts"
        const val START_SINCE_BOOT = "prefs_pin_delay_start_since_boot_ms"
        const val START_WALL_CLOCK = "prefs_pin_delay_start_wall_clock_ms"
        const val LEGACY_LOCK_UNTIL = "prefs_pin_lock_until_ms"
        val LIMITER_KEYS = setOf(FAILED_ATTEMPTS, START_SINCE_BOOT, START_WALL_CLOCK, LEGACY_LOCK_UNTIL)

        const val PIN_KEY = "pin_code"
        const val PIN_CIPHERTEXT = "stored-pin-ciphertext"
        const val OTHER_KEY = "prefs_unrelated"
    }
}
