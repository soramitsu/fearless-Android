package jp.co.soramitsu.account.impl.presentation.exporting

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Lifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.impl.testutil.hostFragment
import jp.co.soramitsu.account.impl.testutil.closeFragmentHost
import jp.co.soramitsu.account.impl.testutil.closeFragmentHosts
import jp.co.soramitsu.common.utils.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportFragmentLifecycleTest {
    @After
    fun cleanUpHosts() {
        closeFragmentHosts()
    }

    class TestFragment : ExportFragment<ExportViewModel>() {
        override val viewModel = mockk<ExportViewModel>(relaxed = true)
        val warnings = MutableLiveData<Event<Unit>>()
        val exports = MutableLiveData<Event<String>>()
        init {
            every { viewModel.showSecurityWarningEvent } returns warnings
            every { viewModel.exportEvent } returns exports
        }
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FrameLayout(inflater.context)
        override fun initViews() = Unit
    }

    @Test
    fun closingTheHostDestroysItsFragmentAndWarningComposition() {
        val fragment = TestFragment()
        val host = hostFragment(fragment)
        fragment.warnings.value = Event(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        val warning = fragment.childFragmentManager.findFragmentByTag(SecurityWarningBottomSheet.TAG)!!
        val warningViewLifecycle = warning.viewLifecycleOwner.lifecycle

        host.closeFragmentHost()

        assertEquals(Lifecycle.State.DESTROYED, host.get().lifecycle.currentState)
        assertEquals(Lifecycle.State.DESTROYED, warningViewLifecycle.currentState)
        assertFalse(warning.isAdded)
        assertFalse(fragment.isAdded)
    }

    @Test
    fun exportStartsAPlainTextChooserAndKeepsTheWindowSecureWhileStopped() {
        val fragment = TestFragment()
        val host = hostFragment(fragment)
        assertTrue(host.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        fragment.exports.value = Event("synthetic export")
        val chooser = shadowOf(host.get()).nextStartedActivityForResult
        // AndroidX registers a platform request code for the fragment's chooser request.
        assertTrue(chooser.requestCode > 0)
        assertEquals(Intent.ACTION_CHOOSER, chooser.intent.action)
        val send = chooser.intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("synthetic export", send.getStringExtra(Intent.EXTRA_TEXT))
        val state = Bundle()
        host.saveInstanceState(state).pause().stop()
        assertTrue(host.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        host.destroy()
    }

    @Test
    fun aVisibleWarningIsReusedAndCancellationIsDeliveredOnlyOnce() {
        val fragment = TestFragment()
        val host = hostFragment(fragment)
        fragment.warnings.value = Event(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        val manager = fragment.childFragmentManager
        val sheet = manager.findFragmentByTag(SecurityWarningBottomSheet.TAG) as SecurityWarningBottomSheet
        assertTrue(sheet.requireDialog().isShowing)
        val behavior = (sheet.requireDialog() as BottomSheetDialog).behavior
        assertFalse(behavior.isDraggable)
        assertTrue(behavior.isHideable)
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, behavior.state)
        fragment.warnings.value = Event(Unit)
        assertSame(sheet, manager.findFragmentByTag(SecurityWarningBottomSheet.TAG))
        sheet.requireDialog().cancel()
        shadowOf(Looper.getMainLooper()).idle()
        manager.executePendingTransactions()
        manager.setFragmentResult(SecurityWarningBottomSheet.REQUEST_KEY,
            bundleOf(SecurityWarningBottomSheet.RESULT_ACTION_KEY to SecurityWarningBottomSheet.ACTION_CANCEL))
        verify(exactly = 1) { fragment.viewModel.securityWarningCancel() }
        host.closeFragmentHost()
    }

    @Test
    fun unexpectedExistingFragmentFailsClosedWithoutReplacingIt() {
        val fragment = TestFragment()
        val host = hostFragment(fragment)
        val blocker = Fragment()
        fragment.childFragmentManager.beginTransaction().add(blocker, SecurityWarningBottomSheet.TAG).commitNow()
        repeat(2) { fragment.warnings.value = Event(Unit) }
        assertSame(blocker, fragment.childFragmentManager.findFragmentByTag(SecurityWarningBottomSheet.TAG))
        verify(exactly = 1) { fragment.viewModel.securityWarningCancel() }
        host.closeFragmentHost()
    }

    @Test
    fun confirmationDoesNotCancelButMissingActionFailsClosed() {
        val fragment = TestFragment()
        val host = hostFragment(fragment)
        val manager = fragment.childFragmentManager
        manager.setFragmentResult(SecurityWarningBottomSheet.REQUEST_KEY,
            bundleOf(SecurityWarningBottomSheet.RESULT_ACTION_KEY to SecurityWarningBottomSheet.ACTION_CONFIRM))
        verify(exactly = 0) { fragment.viewModel.securityWarningCancel() }
        manager.setFragmentResult(SecurityWarningBottomSheet.REQUEST_KEY, Bundle())
        verify(exactly = 1) { fragment.viewModel.securityWarningCancel() }
        host.closeFragmentHost()
    }

    @Test
    fun savedAndDestroyedManagersRefuseToOpenANewWarning() {
        val fragment = TestFragment()
        val host = hostFragment(fragment)
        val manager = fragment.childFragmentManager
        host.saveInstanceState(Bundle())
        assertEquals(SecurityWarningShowResult.FAILED_CLOSED, manager.showSecurityWarningSheet())
        host.closeFragmentHost()
        assertEquals(SecurityWarningShowResult.FAILED_CLOSED, manager.showSecurityWarningSheet())
    }

    @Test
    fun backKeyCancelsTheWarningAndOtherKeysLeaveItVisible() {
        val fragment = TestFragment()
        val host = hostFragment(fragment)
        fragment.warnings.value = Event(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        val sheet = fragment.childFragmentManager.findFragmentByTag(SecurityWarningBottomSheet.TAG) as SecurityWarningBottomSheet
        val dialog = sheet.requireDialog()
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A))
        assertTrue(dialog.isShowing)
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        shadowOf(Looper.getMainLooper()).idle()
        fragment.childFragmentManager.executePendingTransactions()
        verify(exactly = 1) { fragment.viewModel.securityWarningCancel() }
        assertFalse(dialog.isShowing)
        host.closeFragmentHost()
    }
}
