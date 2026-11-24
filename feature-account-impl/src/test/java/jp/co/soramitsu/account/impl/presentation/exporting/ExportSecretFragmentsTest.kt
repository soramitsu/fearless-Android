package jp.co.soramitsu.account.impl.presentation.exporting

import android.view.View
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.impl.presentation.exporting.mnemonic.ExportMnemonicFragment
import jp.co.soramitsu.account.impl.presentation.exporting.mnemonic.ExportMnemonicViewModel
import jp.co.soramitsu.account.impl.presentation.exporting.seed.ExportSeedFragment
import jp.co.soramitsu.account.impl.presentation.exporting.seed.ExportSeedViewModel
import jp.co.soramitsu.account.impl.testutil.hostFragment
import jp.co.soramitsu.account.impl.testutil.closeFragmentHost
import jp.co.soramitsu.account.impl.testutil.closeFragmentHosts
import jp.co.soramitsu.common.compose.component.MnemonicWordModel
import jp.co.soramitsu.common.utils.ComponentHolder
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.databinding.FragmentExportMnemonicBinding
import jp.co.soramitsu.feature_account_impl.databinding.FragmentExportSeedBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportSecretFragmentsTest {
    @After
    fun cleanUpHosts() {
        closeFragmentHosts()
    }

    @Test
    fun seedScreenDisplaysAvailableEcosystemsAndDispatchesExportAndCopyActions() {
        val vm = mockk<ExportSeedViewModel>(relaxed = true)
        every { vm.exportSource } returns ExportSource.Seed
        every { vm.isExportFromWallet } returns true
        val seeds = MutableLiveData(ComponentHolder(listOf("0xsubstrate", "0xethereum")))
        val paths = MutableLiveData(ComponentHolder(listOf("//3", "m/44'/60'/0'/0/0")))
        every { vm.exportSeedLiveData } returns seeds
        every { vm.derivationPathLiveData } returns paths
        every { vm.cryptoTypeLiveData } returns MutableLiveData(CryptoType.ED25519)
        val fragment = ExportSeedFragment()
        val host = hostFragment(fragment, vm)
        val binding = FragmentExportSeedBinding.bind(fragment.requireView())
        assertEquals(View.VISIBLE, binding.exportSeedSubstrateLayout.visibility)
        assertEquals(View.VISIBLE, binding.exportSeedEthereumLayout.visibility)
        assertEquals("//3", binding.exportSeedAdvanced.getSubstrateDerivationPath())
        assertEquals("m/44'/60'/0'/0/0", binding.exportSeedAdvanced.getEthereumDerivationPath())
        binding.exportSeedExport.performClick()
        binding.exportSubstrateSeedCopyButton.performClick()
        binding.exportEthereumSeedCopyButton.performClick()
        binding.exportSeedToolbar.findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()
        verify { vm.exportClicked() }
        verify { vm.substrateSeedClicked() }
        verify { vm.ethereumSeedClicked() }
        verify { vm.back() }
        seeds.value = ComponentHolder(listOf(null, "0xethereum"))
        paths.value = ComponentHolder(listOf(null, null))
        assertEquals(View.GONE, binding.exportSeedSubstrateLayout.visibility)
        assertEquals(View.VISIBLE, binding.exportSeedEthereumLayout.visibility)
        assertEquals(View.GONE, binding.exportSeedAdvanced.visibility)
        host.closeFragmentHost()
    }

    @Test
    fun mnemonicScreenShowsReadOnlyPathsAndForwardsConfirmationExportAndBack() {
        val vm = mockk<ExportMnemonicViewModel>(relaxed = true)
        every { vm.exportSource } returns ExportSource.Mnemonic
        every { vm.isExportFromWallet } returns false
        every { vm.mnemonicWordsLiveData } returns MutableLiveData(listOf(MnemonicWordModel("1", "abandon")))
        val paths = MutableLiveData(ComponentHolder(listOf("//3", null)))
        every { vm.derivationPathLiveData } returns paths
        every { vm.cryptoTypeLiveData } returns MutableLiveData(CryptoType.SR25519)
        val fragment = ExportMnemonicFragment()
        val host = hostFragment(fragment, vm)
        val binding = FragmentExportMnemonicBinding.bind(fragment.requireView())
        assertEquals(View.VISIBLE, binding.exportMnemonicAdvanced.visibility)
        assertEquals("//3", binding.exportMnemonicAdvanced.getSubstrateDerivationPath())
        assertFalse(binding.exportMnemonicAdvanced.substrateDerivationPathEditText.isEnabled)
        assertEquals(View.GONE, binding.exportMnemonicAdvanced.ethereumDerivationPathField.visibility)
        binding.exportMnemonicConfirm.performClick()
        binding.exportMnemonicExport.performClick()
        binding.exportMnemonicToolbar.findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()
        verify { vm.openConfirmMnemonic() }
        verify { vm.exportClicked() }
        verify { vm.back() }
        paths.value = ComponentHolder(listOf(null, "m/44'/60'/0'/0/0"))
        assertEquals(View.GONE, binding.exportMnemonicAdvanced.substrateDerivationPathField.visibility)
        assertEquals(View.VISIBLE, binding.exportMnemonicAdvanced.ethereumDerivationPathField.visibility)
        paths.value = ComponentHolder(listOf("", null))
        assertEquals(View.GONE, binding.exportMnemonicAdvanced.visibility)
        host.closeFragmentHost()
    }
}
