package jp.co.soramitsu.common.feedback

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import jp.co.soramitsu.common.R

/**
 * Sound and touch for transfers and the launch (brand kit SPEC, "Launch, feel and sound",
 * part 2), played with the platform's SoundPool and Vibrator only. One per process.
 *
 * Sound uses the sonification usage, so it follows the system's sound volume; silent and vibrate
 * ringer modes play nothing. Touch uses the touch usage (Android 13+), so the system's touch
 * feedback setting and intensity apply; before Android 13 that setting is read directly.
 * Nothing here touches wallet data: callers say which moment happened, nothing more.
 */
class TransferFeedback private constructor(context: Context) {

    private val app = context.applicationContext
    private val preferences = FeedbackPreferences(app)
    private val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val patterns: HapticPatterns? by lazy {
        runCatching { app.resources.openRawResource(R.raw.fw_haptics).bufferedReader().use { HapticPatterns.parse(it.readText()) } }
            .onFailure { Log.w(TAG, "Haptic patterns unavailable", it) }
            .getOrNull()
    }
    private var pool: SoundPool? = null
    private val soundIds = HashMap<FeedbackSound, Int>()
    private val ready = HashSet<Int>()
    private val waiting = HashMap<Int, Long>()

    /** Starts decoding the two sounds (SoundPool decodes on its own thread), so the first one plays on time. */
    @Synchronized
    fun prepare() {
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val created = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attributes).build()
        created.setOnLoadCompleteListener { soundPool, sampleId, status ->
            synchronized(this) {
                if (status != 0) return@synchronized
                ready += sampleId
                // A cue that arrived while its sound was still decoding plays if it is still fresh.
                val askedAt = waiting.remove(sampleId)
                if (askedAt != null && SystemClock.elapsedRealtime() - askedAt <= LATE_SOUND_MS) soundPool.play(sampleId, 1f, 1f, 1, 0, 1f)
            }
        }
        soundIds[FeedbackSound.Send] = created.load(app, R.raw.fw_send, 1)
        soundIds[FeedbackSound.Receive] = created.load(app, R.raw.fw_receive, 1)
        pool = created
    }

    /** Plays [cue] as the user's switches and the device allow. [view] lets a failure use the platform's reject feedback. */
    fun play(cue: FeedbackCue, view: View? = null): FeedbackPlan {
        val settings = preferences.read()
        val ringerMode = app.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        val systemTouch = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
            Settings.System.getInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        val plan = TransferFeedbackRules.plan(cue, settings.soundEffects, settings.hapticFeedback,
            ringerNormal = ringerMode == AudioManager.RINGER_MODE_NORMAL, systemTouchFeedback = systemTouch)
        val soundPlayed = plan.sound?.let(::playSound) ?: false
        val hapticPlayed = plan.haptic?.let { vibrate(it, view) } ?: false
        // Debug builds log each cue so a device walk can show the calls were made.
        if (debuggable) Log.i(TAG, "cue=$cue sound=${plan.sound} soundStarted=$soundPlayed haptic=${plan.haptic} hapticStarted=$hapticPlayed")
        return plan
    }

    @Synchronized
    private fun playSound(sound: FeedbackSound): Boolean {
        prepare()
        val id = soundIds[sound] ?: return false
        if (id !in ready) {
            waiting[id] = SystemClock.elapsedRealtime()
            return false
        }
        return (pool?.play(id, 1f, 1f, 1, 0, 1f) ?: 0) != 0
    }

    private fun vibrate(name: String, view: View?): Boolean {
        if (name == TransferFeedbackRules.PATTERN_FAILURE) {
            if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && view.performHapticFeedback(HapticFeedbackConstants.REJECT)) return true
            return vibrateEffect(HapticWaveform(longArrayOf(0, 40, 60, 40), intArrayOf(0, 200, 0, 200)), primitives = emptyList())
        }
        val pattern = patterns?.get(name) ?: return false
        val waveform = pattern.waveform ?: return false
        return vibrateEffect(waveform, pattern.primitives)
    }

    private fun vibrateEffect(waveform: HapticWaveform, primitives: List<HapticPrimitive>): Boolean {
        val vibrator = vibrator()?.takeIf { it.hasVibrator() } ?: return false
        val effect = composition(vibrator, primitives) ?: if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(waveform.timingsMs, waveform.amplitudes, NO_REPEAT)
        } else {
            VibrationEffect.createWaveform(waveform.onOffTimings(), NO_REPEAT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
        }
        return true
    }

    private fun composition(vibrator: Vibrator, primitives: List<HapticPrimitive>): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || primitives.isEmpty()) return null
        val ids = primitives.map { primitiveId(it.id) ?: return null }
        if (!vibrator.areAllPrimitivesSupported(*ids.toIntArray())) return null
        val composition = VibrationEffect.startComposition()
        primitives.forEachIndexed { index, primitive -> composition.addPrimitive(ids[index], primitive.scale, primitive.delayMs) }
        return composition.compose()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun primitiveId(name: String): Int? = when (name) {
        "CLICK" -> VibrationEffect.Composition.PRIMITIVE_CLICK
        "QUICK_RISE" -> VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        "SLOW_RISE" -> VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
        "QUICK_FALL" -> VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
        "TICK" -> VibrationEffect.Composition.PRIMITIVE_TICK
        "THUD" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) VibrationEffect.Composition.PRIMITIVE_THUD else null
        "SPIN" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) VibrationEffect.Composition.PRIMITIVE_SPIN else null
        "LOW_TICK" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else null
        else -> null
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        app.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        app.getSystemService(Vibrator::class.java)
    }

    companion object {
        private const val TAG = "FwFeedback"
        private const val NO_REPEAT = -1
        private const val LATE_SOUND_MS = 400L

        @Volatile
        private var instance: TransferFeedback? = null

        fun get(context: Context): TransferFeedback =
            instance ?: synchronized(this) { instance ?: TransferFeedback(context).also { instance = it } }

        /** Called by the application once, so callers without a context (view models) can [cue]. */
        fun install(context: Context) {
            get(context)
        }

        /** Shorthand for `get(context).play(cue, view)`. */
        fun play(context: Context, cue: FeedbackCue, view: View? = null): FeedbackPlan = get(context).play(cue, view)

        /** For callers without a context. Plays nothing before [install] (and in unit tests). */
        fun cue(cue: FeedbackCue): FeedbackPlan? = instance?.play(cue)
    }
}
