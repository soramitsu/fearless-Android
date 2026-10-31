package jp.co.soramitsu.account.impl.presentation.common

import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * Applies [SecureScreenFlag] to the Activity window of a fragment that shows
 * a recovery phrase, seed or keystore JSON, so screenshots, screen recording,
 * casting and the recents thumbnail never capture it.
 *
 * Create one per fragment and forward onViewCreated, onStart, onStop and
 * onDestroyView to it. The other screens it coordinates with are the
 * fragment's siblings in its parent FragmentManager, i.e. the other
 * destinations of the NavHost that shows it.
 */
internal class SecureScreenGuard(private val fragment: Fragment) {

    private val flag = SecureScreenFlag()
    private var observedManager: FragmentManager? = null

    private val siblingCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: View,
            savedInstanceState: Bundle?
        ) {
            if (f !== fragment) flag.onIncomingShown()
        }

        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            if (f !== fragment) flag.onIncomingShown()
        }

        override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
            if (f !== fragment) flag.onOtherViewDestroyed()
        }
    }

    // Ends a handover when the incoming destination has no view of its own.
    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        flag.onIncomingShown()
    }

    fun onViewCreated() {
        observeSiblings()
        flag.show(fragment.requireActivity().window.asSecureFlagWindow())
    }

    fun onStart() {
        flag.show(fragment.requireActivity().window.asSecureFlagWindow())
    }

    fun onStop() {
        // A stopped Activity keeps the flag (and a secure recents thumbnail);
        // only a screen that is being replaced or popped hands it over.
        if (fragment.isRemoving) flag.onLeaving()
    }

    fun onDestroyView() {
        stopObservingSiblings()
        flag.hide()
    }

    private fun observeSiblings() {
        if (observedManager != null) return

        val manager = fragment.parentFragmentManager
        manager.registerFragmentLifecycleCallbacks(siblingCallbacks, false)
        manager.addOnBackStackChangedListener(backStackListener)
        observedManager = manager
    }

    private fun stopObservingSiblings() {
        val manager = observedManager ?: return

        manager.unregisterFragmentLifecycleCallbacks(siblingCallbacks)
        manager.removeOnBackStackChangedListener(backStackListener)
        observedManager = null
    }
}

internal fun Window.asSecureFlagWindow(): SecureFlagWindow = WindowSecureFlag(this)

private data class WindowSecureFlag(private val window: Window) : SecureFlagWindow {

    override val isSecure: Boolean
        get() = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    override fun addSecureFlag() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun clearSecureFlag() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
