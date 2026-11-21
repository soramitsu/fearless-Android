package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.view.View
import android.view.WindowManager
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.account.impl.testutil.hostFragment
import jp.co.soramitsu.account.impl.testutil.closeFragmentHost
import jp.co.soramitsu.account.impl.testutil.closeFragmentHosts
import jp.co.soramitsu.common.compose.component.MnemonicWordModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.databinding.FragmentBackupMnemonicBinding
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
class BackupMnemonicFragmentTest {
    @After
    fun cleanUpHosts() {
        closeFragmentHosts()
    }

    @Test
    fun nextAndSkipForwardTheDisplayedDerivationPathsWhileTheWindowIsSecure() {
        val vm = mockk<BackupMnemonicViewModel>(relaxed = true)
        every { vm.walletId } returns 7
        every { vm.isShowAdvancedBlock } returns true
        every { vm.isSubstrateAndEthereumAccount } returns true
        every { vm.isShowSkipButton } returns true
        every { vm.mnemonic } returns MutableStateFlow(listOf(MnemonicWordModel("1", "abandon")))
        every { vm.selectedEncryptionTypeLiveData } returns MutableLiveData(CryptoTypeModel("ED25519", CryptoType.ED25519))
        val fragment = BackupMnemonicFragment()
        val host = hostFragment(fragment, vm)
        val binding = FragmentBackupMnemonicBinding.bind(fragment.requireView())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.VISIBLE, binding.advancedBlockView.visibility)
        assertEquals(View.VISIBLE, binding.confirmMnemonicSkip.visibility)
        assertTrue(host.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        binding.advancedBlockView.setSubstrateDerivationPath("//8")
        binding.advancedBlockView.setEthereumDerivationPath("")
        binding.nextBtn.performClick()
        binding.confirmMnemonicSkip.performClick()
        binding.toolbar.findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()
        verify { vm.onNextClick("//8", BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH, any()) }
        verify { vm.skipClicked() }
        verify { vm.homeButtonClicked() }
        host.closeFragmentHost()
    }

    @Test
    fun singleEcosystemWalletsExposeOnlyTheirOwnAdvancedFields() {
        for (substrate in listOf(true, false)) {
            val vm = mockk<BackupMnemonicViewModel>(relaxed = true)
            every { vm.walletId } returns 7
            every { vm.isShowAdvancedBlock } returns true
            every { vm.isSubstrateAndEthereumAccount } returns false
            every { vm.isSubstrateAccount } returns substrate
            every { vm.isEthereumAccount } returns !substrate
            every { vm.mnemonic } returns MutableStateFlow(emptyList())
            val fragment = BackupMnemonicFragment()
            val host = hostFragment(fragment, vm)
            val binding = FragmentBackupMnemonicBinding.bind(fragment.requireView())
            assertEquals(if (substrate) View.VISIBLE else View.GONE, binding.advancedBlockView.substrateDerivationPathField.visibility)
            assertEquals(if (substrate) View.GONE else View.VISIBLE, binding.advancedBlockView.ethereumDerivationPathField.visibility)
            assertFalse(binding.confirmMnemonicSkip.visibility == View.VISIBLE)
            host.closeFragmentHost()
        }
    }

    @Test
    fun informationEventShowsTheMnemonicExplanation() {
        val vm = mockk<BackupMnemonicViewModel>(relaxed = true)
        every { vm.walletId } returns null
        every { vm.mnemonic } returns MutableStateFlow(emptyList())
        val info = MutableLiveData<Event<Unit>>()
        every { vm.showInfoEvent } returns info
        val fragment = BackupMnemonicFragment()
        val host = hostFragment(fragment, vm)
        info.value = Event(Unit)
        fragment.childFragmentManager.executePendingTransactions()
        assertTrue(fragment.childFragmentManager.fragments.any { it is jp.co.soramitsu.common.presentation.ErrorDialog })
        host.closeFragmentHost()
    }
}
