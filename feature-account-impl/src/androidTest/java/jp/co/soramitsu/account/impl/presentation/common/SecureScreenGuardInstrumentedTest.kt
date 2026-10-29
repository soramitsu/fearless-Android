package jp.co.soramitsu.account.impl.presentation.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives real FragmentManager transactions in the shapes the wallet uses
 * (PIN -> export with the PIN popped, export -> confirm, confirm -> PIN create)
 * and checks FLAG_SECURE on the Activity window after each one.
 */
@RunWith(AndroidJUnit4::class)
class SecureScreenGuardInstrumentedTest {

    private val FragmentActivity.isSecure: Boolean
        get() = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun exportOpenedFromPinStaysSecureAndLeavingRestoresTheWindow() {
        ActivityScenario.launch(SecureScreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.navigate(PlainTestFragment())
                activity.navigate(RecordAndRestoreTestFragment())
                assertTrue(activity.isSecure)

                // openAfterPinCode: pop the PIN screen and open the export.
                activity.navigate(GuardedTestFragment(), popCurrent = true)
                assertTrue(activity.isSecure)

                activity.back()
                assertFalse(activity.isSecure)
            }
        }
    }

    @Test
    fun handoverBetweenSecretScreensNeverDropsTheFlag() {
        ActivityScenario.launch(SecureScreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.navigate(PlainTestFragment())
                activity.navigate(GuardedTestFragment())
                activity.navigate(GuardedTestFragment())
                assertTrue(activity.isSecure)

                activity.back()
                assertTrue(activity.isSecure)

                activity.back()
                assertFalse(activity.isSecure)
            }
        }
    }

    @Test
    fun pinScreenOpenedFromConfirmationStaysSecure() {
        ActivityScenario.launch(SecureScreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.navigate(PlainTestFragment())
                activity.navigate(GuardedTestFragment())
                activity.navigate(RecordAndRestoreTestFragment())
                assertTrue(activity.isSecure)

                activity.back()
                assertTrue(activity.isSecure)

                activity.navigate(RecordAndRestoreTestFragment())
                // PIN created: open the main screen and drop the whole flow.
                activity.supportFragmentManager.popBackStack(
                    FIRST_ENTRY,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE
                )
                activity.navigate(PlainTestFragment())
                assertFalse(activity.isSecure)
            }
        }
    }

    @Test
    fun stoppedActivityKeepsTheFlagForTheRecentsThumbnail() {
        ActivityScenario.launch(SecureScreenTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.navigate(PlainTestFragment())
                activity.navigate(GuardedTestFragment())
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity -> assertTrue(activity.isSecure) }

            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity -> assertTrue(activity.isSecure) }
        }
    }

    private fun SecureScreenTestActivity.navigate(fragment: Fragment, popCurrent: Boolean = false) {
        val manager = supportFragmentManager
        if (popCurrent) manager.popBackStack()
        manager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(SecureScreenTestActivity.CONTAINER_ID, fragment)
            .addToBackStack(if (manager.backStackEntryCount == 0) FIRST_ENTRY else null)
            .commit()
        manager.executePendingTransactions()
    }

    private fun SecureScreenTestActivity.back() {
        supportFragmentManager.popBackStack()
        supportFragmentManager.executePendingTransactions()
    }

    private companion object {
        const val FIRST_ENTRY = "first"
    }
}

class SecureScreenTestActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        setContentView(FrameLayout(this).apply { id = CONTAINER_ID })
    }

    companion object {
        val CONTAINER_ID = View.generateViewId()
    }
}

/** A screen that shows a secret through [SecureScreenGuard]. */
class GuardedTestFragment : Fragment() {

    private val secureScreen = SecureScreenGuard(this)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = View(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        secureScreen.onViewCreated()
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        secureScreen.onStart()
    }

    override fun onStop() {
        secureScreen.onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        secureScreen.onDestroyView()
        super.onDestroyView()
    }
}

/** The PIN / recovery-import pattern: record the flag, add it, restore it. */
class RecordAndRestoreTestFragment : Fragment() {

    private var hadSecureFlag = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val window = requireActivity().window
        hadSecureFlag = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        return View(requireContext())
    }

    override fun onDestroyView() {
        if (!hadSecureFlag) requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onDestroyView()
    }
}

class PlainTestFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = View(requireContext())
}
