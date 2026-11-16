package jp.co.soramitsu.account.impl.presentation.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureScreenGuardTest {

    class SecretFragment : Fragment() {
        private val guard = SecureScreenGuard(this, warnAboutScreenReaders = true)

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FrameLayout(inflater.context)

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            guard.onViewCreated()
        }

        override fun onStart() {
            super.onStart()
            guard.onStart()
        }

        override fun onStop() {
            guard.onStop()
            super.onStop()
        }

        override fun onDestroyView() {
            guard.onDestroyView()
            super.onDestroyView()
        }
    }

    class PlainFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FrameLayout(inflater.context)
    }

    private lateinit var activity: FragmentActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    }

    private val secure get() = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private fun show(fragment: Fragment, backStack: Boolean) {
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .apply { if (backStack) addToBackStack(null) }
            .commit()
        activity.supportFragmentManager.executePendingTransactions()
    }

    @Test
    fun `a secret screen secures the window and protects its view against overlays`() {
        val secret = SecretFragment()
        show(secret, backStack = false)

        assertTrue(secure)
        assertTrue(secret.requireView().filterTouchesWhenObscured)
    }

    @Test
    fun `leaving for a plain screen restores the window and coming back secures it again`() {
        show(PlainFragment(), backStack = false)
        assertFalse(secure)
        show(SecretFragment(), backStack = true)
        assertTrue(secure)

        show(PlainFragment(), backStack = true)
        assertFalse(secure)

        activity.supportFragmentManager.popBackStackImmediate()
        assertTrue(secure)
        activity.supportFragmentManager.popBackStackImmediate()
        assertFalse(secure)
    }

    @Test
    fun `a stopped activity keeps the flag for the recents thumbnail`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        activity = controller.get()
        show(SecretFragment(), backStack = false)

        controller.pause().stop()
        assertTrue(secure)
        controller.start().resume()
        assertTrue(secure)
        controller.pause().stop().destroy()
        assertFalse(secure)
    }

    @Test
    fun `the window adapter reads, adds and clears the secure flag`() {
        val window = activity.window.asSecureFlagWindow()
        assertFalse(window.isSecure)
        window.addSecureFlag()
        assertTrue(window.isSecure)
        window.clearSecureFlag()
        assertFalse(window.isSecure)
    }
}
