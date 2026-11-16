package jp.co.soramitsu.account.impl.presentation.mnemonic.confirm

import android.os.Looper
import android.view.View
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.view.MnemonicWordView
import jp.co.soramitsu.account.impl.testutil.hostFragment
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_impl.databinding.FragmentConfirmMnemonicBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ConfirmMnemonicFragmentTest {
    @Test
    fun wordSelectionAndRemovalMoveTheDisplayedWordAndNotifyTheViewModel() {
        val vm = mockk<ConfirmMnemonicViewModel>(relaxed = true)
        every { vm.shuffledMnemonic } returns listOf("abandon", "ability", "able")
        val remove = MutableLiveData<Event<Unit>>()
        val reset = MutableLiveData<Event<Unit>>()
        every { vm.removeLastWordFromConfirmationEvent } returns remove
        every { vm.resetConfirmationEvent } returns reset
        val fragment = ConfirmMnemonicFragment()
        val host = hostFragment(fragment, vm)
        val binding = FragmentConfirmMnemonicBinding.bind(fragment.requireView())
        binding.root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        binding.root.layout(0, 0, 1080, 1920)
        binding.root.viewTreeObserver.dispatchOnGlobalLayout()
        shadowOf(Looper.getMainLooper()).idle()
        val word = (0 until binding.wordsMnemonicView.childCount).map(binding.wordsMnemonicView::getChildAt)
            .filterIsInstance<MnemonicWordView>().first()
        word.performClick()
        verify { vm.addWordToConfirmMnemonic("abandon") }
        assertTrue((0 until binding.confirmationMnemonicView.childCount)
            .map(binding.confirmationMnemonicView::getChildAt).any { it is MnemonicWordView })
        remove.value = Event(Unit)
        assertTrue(word.parent === binding.wordsMnemonicView)
        word.performClick()
        reset.value = Event(Unit)
        assertTrue(word.parent === binding.wordsMnemonicView)
        assertFalse((0 until binding.confirmationMnemonicView.childCount)
            .map(binding.confirmationMnemonicView::getChildAt).any { it is MnemonicWordView })
        host.pause().stop().destroy()
    }

    @Test
    fun buttonsReflectProgressAndSendTheirDistinctActions() {
        val vm = mockk<ConfirmMnemonicViewModel>(relaxed = true)
        every { vm.shuffledMnemonic } returns emptyList()
        every { vm.skipVisible } returns true
        val next = MutableLiveData(false)
        val skip = MutableLiveData(true)
        every { vm.nextButtonEnableLiveData } returns next
        every { vm.skipButtonEnableLiveData } returns skip
        val fragment = ConfirmMnemonicFragment()
        val host = hostFragment(fragment, vm)
        val binding = FragmentConfirmMnemonicBinding.bind(fragment.requireView())
        assertFalse(binding.nextBtn.isEnabled)
        assertTrue(binding.confirmMnemonicSkip.isEnabled)
        assertEquals(View.VISIBLE, binding.confirmMnemonicSkip.visibility)
        next.value = true
        binding.nextBtn.performClick()
        binding.confirmMnemonicSkip.performClick()
        binding.confirmationMnemonicView.performClick()
        binding.toolbar.findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()
        binding.toolbar.findViewById<View>(jp.co.soramitsu.common.R.id.rightImg).performClick()
        verify { vm.nextButtonClicked() }
        verify { vm.skipClicked() }
        verify { vm.removeLastWordFromConfirmation() }
        verify { vm.homeButtonClicked() }
        verify { vm.resetConfirmationClicked() }
        skip.value = false
        assertFalse(binding.confirmMnemonicSkip.isEnabled)
        host.pause().stop().destroy()
    }
}
