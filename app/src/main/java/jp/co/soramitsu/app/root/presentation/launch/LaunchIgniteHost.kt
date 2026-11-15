package jp.co.soramitsu.app.root.presentation.launch

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.os.SystemClock
import android.view.Choreographer
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.presentation.main.MainFragment
import jp.co.soramitsu.common.feedback.FeedbackCue
import jp.co.soramitsu.common.feedback.TransferFeedback
import jp.co.soramitsu.common.compose.theme.fw.FwViews
import jp.co.soramitsu.common.launch.LaunchFlightStage
import jp.co.soramitsu.common.launch.LaunchLanding
import jp.co.soramitsu.common.launch.LaunchTimeline
import jp.co.soramitsu.common.launch.launchClipPlacement
import jp.co.soramitsu.common.launch.launchMotionAllowed
import jp.co.soramitsu.common.shikiban.Shikiban
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeFragment
import jp.co.soramitsu.splash.presentation.SplashFragment

/**
 * Wires the launch clip and the unlock landing into the wallet host without touching what it
 * shows or in which order: the clip only covers the first screen, then zooms into it.
 */
internal object LaunchIgniteHost {

    /** The kit's launch clip is 720 × 1560 (SPEC v3, "New kit files"): where its focus lands in a view with no clip. */
    private const val CLIP_WIDTH = 720f
    private const val CLIP_HEIGHT = 1560f

    private fun timeline(activity: AppCompatActivity): LaunchTimeline? = runCatching {
        activity.resources.openRawResource(R.raw.fw_launch_timeline).bufferedReader().use { LaunchTimeline.parse(it.readText()) }
    }.getOrNull()

    private fun moves(activity: AppCompatActivity): Boolean =
        launchMotionAllowed(ValueAnimator.areAnimatorsEnabled(), FwViews.effects(activity))

    /**
     * On the first wallet host of this process (a cold start), puts the Ignite clip over [content].
     * Reduced motion, Reduced effects and touch exploration get no clip: the screen is simply there.
     */
    fun attachIfColdStart(activity: AppCompatActivity, content: View) {
        if (!LaunchLanding.consumeClip()) return
        if (!moves(activity)) return
        if (activity.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true) return
        val timeline = timeline(activity) ?: return
        val tokens = Shikiban.load(activity) ?: return
        val parent = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val overlay = LaunchIgniteOverlay(
            context = activity,
            timeline = timeline,
            tokens = tokens,
            content = content,
            // Ready: a first screen is on show and nothing of the system (a biometric prompt) is over the app.
            isReady = { LaunchLanding.ready.value && activity.hasWindowFocus() },
            onArrive = { TransferFeedback.play(activity, FeedbackCue.LaunchArrive) },
            onFinished = { }
        )
        parent.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /**
     * Reports the first screen a person can act on: any screen of the wallet host except the
     * splash and the welcome host, which reports itself once it shows its own first page.
     */
    fun reportScreens(fragmentManager: FragmentManager) {
        fragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                if (f is SplashFragment || f is WelcomeFragment) return
                val view = f.view
                if (view == null) LaunchLanding.screenReady() else view.post { LaunchLanding.screenReady() }
            }
        }, false)
    }

    /**
     * The landing after an unlock (SPEC v4 part 1, "Later arrivals"): when the PIN page gives way to the
     * wallet (the main screen), tier k goes from `flight.landingFrom[k]` to 1 about the dive's focus with
     * `easing.settle` over `landingMs`, starting k × 45 ms later and fading in over its first 45 %. It
     * starts once the main screen has started, so a slow fragment transaction never shows the PIN page
     * again. The tiers are state: parts of the wallet that compose a frame later take the current values.
     * Nothing under reduced motion, and nothing while the launch clip still covers the host.
     */
    fun landAfterUnlock(activity: AppCompatActivity, navController: NavController, fragmentManager: FragmentManager?, content: View) {
        var previous: Int? = null
        var pending = false
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val from = previous
            previous = destination.id
            pending = from == R.id.pincodeFragment && destination.id == R.id.mainFragment
        }
        fragmentManager?.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                if (!pending || f !is MainFragment) return
                pending = false
                if (!moves(activity) || content.alpha < 1f || LaunchFlightStage.active) return
                val tokens = Shikiban.load(activity) ?: return
                val focus = timeline(activity)?.let {
                    launchClipPlacement(content.width.toFloat(), content.height.toFloat(), CLIP_WIDTH, CLIP_HEIGHT, it.focusX, it.focusY)
                }
                val at = IntArray(2).also(content::getLocationInWindow)
                val fx = at[0] + (focus?.get(4) ?: (content.width / 2f))
                val fy = at[1] + (focus?.get(5) ?: (content.height / 2f))
                // Debug builds only: the same slow-motion switch as the launch flight, for a walk on a loaded emulator.
                val slow = (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
                    java.io.File(activity.cacheDir, "fw_launch_slow_flight").exists()
                land(tokens, fx, fy, if (slow) 10f else 1f)
            }
        }, false)
    }

    private fun land(tokens: Shikiban, fx: Float, fy: Float, slowdown: Float) {
        val requestedAt = SystemClock.uptimeMillis()
        var startedAt: Long? = null
        val total = tokens.landingTotalMs(LaunchFlightStage.TIERS)
        LaunchFlightStage.begin(fx, fy, { tokens.landingAt(it, 0f).first }, { 0f }, LaunchFlightStage.MAIN_GROUP)
        val choreographer = Choreographer.getInstance()
        choreographer.postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val now = SystemClock.uptimeMillis()
                // The arrival starts with the wallet's first composed frame (a busy device can take a while to draw
                // it), never later than LANDING_WAIT_MS after the PIN page has gone.
                val start = startedAt ?: if (LaunchFlightStage.composedTiers > 0 || now - requestedAt >= LANDING_WAIT_MS) now.also { startedAt = it } else null
                if (start == null) {
                    choreographer.postFrameCallback(this)
                    return
                }
                val elapsed = (now - start) / slowdown
                if (elapsed >= total) {
                    LaunchFlightStage.end()
                    return
                }
                LaunchFlightStage.apply({ tokens.landingAt(it, elapsed).first }, { tokens.landingAt(it, elapsed).second })
                choreographer.postFrameCallback(this)
            }
        })
    }

    private const val LANDING_WAIT_MS = 1000L
}
