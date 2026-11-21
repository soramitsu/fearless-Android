package jp.co.soramitsu.common.feedback

/** The moments that give feedback (brand kit SPEC, "Launch, feel and sound", part 2). */
enum class FeedbackCue { Commit, Sent, Failed, Received, LaunchArrive, Copied }

/** The two transfer sounds. */
enum class FeedbackSound { Send, Receive }

/** What one cue plays on this device right now. A null field plays nothing. */
data class FeedbackPlan(val sound: FeedbackSound?, val haptic: String?)

/**
 * Which sound and which haptic pattern a cue plays, and whether the device lets it. Sound needs the
 * user's "Sound effects" switch and a ringer in normal mode (silent and vibrate play nothing).
 * Touch needs the user's "Haptic feedback" switch and the system's touch feedback setting.
 * Failures and confirmations never make a sound; a launch never makes a sound.
 */
object TransferFeedbackRules {

    fun plan(cue: FeedbackCue, soundOn: Boolean, hapticsOn: Boolean, ringerNormal: Boolean, systemTouchFeedback: Boolean): FeedbackPlan {
        val sound = when (cue) {
            FeedbackCue.Sent -> FeedbackSound.Send
            FeedbackCue.Received -> FeedbackSound.Receive
            else -> null
        }?.takeIf { soundOn && ringerNormal }
        val haptic = when (cue) {
            FeedbackCue.Commit, FeedbackCue.Copied -> PATTERN_COMMIT
            FeedbackCue.Sent -> PATTERN_SEND
            FeedbackCue.Failed -> PATTERN_FAILURE
            FeedbackCue.Received -> PATTERN_RECEIVE
            FeedbackCue.LaunchArrive -> PATTERN_LAUNCH_ARRIVE
        }.takeIf { hapticsOn && systemTouchFeedback }
        return FeedbackPlan(sound, haptic)
    }

    const val PATTERN_COMMIT = "commit"
    const val PATTERN_SEND = "send"
    const val PATTERN_RECEIVE = "receive"
    const val PATTERN_FAILURE = "failure"
    const val PATTERN_LAUNCH_ARRIVE = "launchArrive"
}
