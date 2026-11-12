package jp.co.soramitsu.app.root.presentation.launch

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.presentation.main.MainFragment
import jp.co.soramitsu.common.feedback.FeedbackCue
import jp.co.soramitsu.common.feedback.TransferFeedback
import jp.co.soramitsu.common.launch.LaunchHandOff
import jp.co.soramitsu.common.launch.LaunchLanding
import jp.co.soramitsu.common.launch.LaunchTimeline
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeFragment
import jp.co.soramitsu.splash.presentation.SplashFragment

/**
 * Wires the launch clip and the unlock landing into the wallet host without touching what it
 * shows or in which order: the clip only covers the first screen, then zooms into it.
 */
internal object LaunchIgniteHost {

    /**
     * On the first wallet host of this process (a cold start), puts the Ignite clip over [content].
     * Reduced motion (animator scale 0) and touch exploration get no clip: the screen is simply there.
     */
    fun attachIfColdStart(activity: AppCompatActivity, content: View) {
        if (!LaunchLanding.consumeClip()) return
        if (!ValueAnimator.areAnimatorsEnabled()) return
        if (activity.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true) return
        val timeline = runCatching {
            activity.resources.openRawResource(R.raw.fw_launch_timeline).bufferedReader().use { LaunchTimeline.parse(it.readText()) }
        }.getOrNull() ?: return
        val parent = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val overlay = LaunchIgniteOverlay(
            context = activity,
            timeline = timeline,
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
     * The landing after an unlock: when the PIN page gives way to the wallet (the main screen), the
     * wallet scales from 1.06 to 1 and fades in from ink over 0.35 s. It starts once the main
     * screen has started, so a slow fragment transaction never shows the PIN page again behind the
     * fade. Nothing under reduced motion, and nothing while the launch clip still covers the host.
     */
    fun landAfterUnlock(navController: NavController, fragmentManager: FragmentManager?, content: View) {
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
                if (!ValueAnimator.areAnimatorsEnabled() || content.alpha < 1f) return
                content.pivotX = content.width / 2f
                content.pivotY = content.height / 2f
                content.scaleX = LaunchHandOff.LANDING_FROM_SCALE
                content.scaleY = LaunchHandOff.LANDING_FROM_SCALE
                content.alpha = 0f
                content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(LaunchHandOff.LANDING_MS)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }, false)
    }
}
