package jp.co.soramitsu.common.feedback

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The two feedback switches. Both are on until the user turns them off. */
data class FeedbackSettings(val soundEffects: Boolean = true, val hapticFeedback: Boolean = true)

/**
 * "Sound effects" and "Haptic feedback" (Settings). Ordinary display preferences in their own
 * store, isolated from wallet, key and authentication storage.
 */
class FeedbackPreferences internal constructor(private val store: Store) {

    /** The few calls of SharedPreferences this needs, so the rules can be tested without Android. */
    interface Store {
        fun getBoolean(key: String, default: Boolean): Boolean
        fun putBoolean(key: String, value: Boolean): Boolean
        fun listen(onChange: () -> Unit): AutoCloseable
    }

    constructor(context: Context) : this(SharedPreferencesStore(context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)))

    fun read(): FeedbackSettings = FeedbackSettings(
        soundEffects = store.getBoolean(SOUND, true),
        hapticFeedback = store.getBoolean(HAPTICS, true)
    )

    /** Returns false if the value could not be stored. */
    fun setSoundEffects(on: Boolean): Boolean = store.putBoolean(SOUND, on)

    /** Returns false if the value could not be stored. */
    fun setHapticFeedback(on: Boolean): Boolean = store.putBoolean(HAPTICS, on)

    fun observe(): Flow<FeedbackSettings> = callbackFlow {
        trySend(read())
        val subscription = store.listen { trySend(read()) }
        awaitClose { subscription.close() }
    }.distinctUntilChanged()

    private class SharedPreferencesStore(private val preferences: SharedPreferences) : Store {
        override fun getBoolean(key: String, default: Boolean) = preferences.getBoolean(key, default)
        override fun putBoolean(key: String, value: Boolean) = preferences.edit().putBoolean(key, value).commit()
        override fun listen(onChange: () -> Unit): AutoCloseable {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            return AutoCloseable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    companion object {
        const val STORE = "fw_feedback"
        internal const val SOUND = "sound_effects"
        internal const val HAPTICS = "haptic_feedback"
    }
}
