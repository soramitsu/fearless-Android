package jp.co.soramitsu.account.impl.presentation.exporting.json.confirm

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.impl.presentation.exporting.ExportFragment
import jp.co.soramitsu.account.impl.testutil.clickRow
import jp.co.soramitsu.account.impl.testutil.closeFragmentHosts
import jp.co.soramitsu.account.impl.testutil.hostFragment
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentExportJsonConfirmBinding
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class ExportJsonConfirmFragmentTest {

    private val vm = mockk<ExportJsonConfirmViewModel>(relaxed = true)
    private val isEthereum = MutableLiveData<Boolean>()
    private val importType = MutableLiveData<Event<Boolean>>()
    private val chain = mockk<Chain>(relaxed = true) {
        every { name } returns "Kusama"
        every { icon } returns "https://example.org/ksm.svg"
    }
    private lateinit var fragment: ExportJsonConfirmFragment
    private lateinit var binding: FragmentExportJsonConfirmBinding

    @After
    fun cleanUpHosts() {
        closeFragmentHosts()
    }

    private fun host(fromWallet: Boolean, substrateJson: String?, ethereumJson: String?) {
        every { vm.exportSource } returns ExportSource.Json
        every { vm.isExportFromWallet } returns fromWallet
        every { vm.substrateJson } returns substrateJson
        every { vm.ethereumJson } returns ethereumJson
        every { vm.isEthereum } returns isEthereum
        every { vm.chainLiveData } returns MutableLiveData(chain)
        every { vm.cryptoTypeLiveData } returns MutableLiveData(CryptoType.SR25519)
        every { vm.showJsonImportTypeEvent } returns importType
        fragment = ExportJsonConfirmFragment().apply { imageLoader = mockk(relaxed = true) }
        hostFragment(fragment, vm)
        binding = FragmentExportJsonConfirmBinding.bind(fragment.requireView())
    }

    private fun View.message() = findViewById<TextView>(jp.co.soramitsu.common.R.id.labeledTextText).text.toString()

    @Test
    fun walletExportShowsBothKeystoresAndForwardsEveryAction() {
        host(fromWallet = true, substrateJson = "{\"address\":\"substrate\"}", ethereumJson = "{\"address\":\"eth\"}")
        isEthereum.value = false

        assertEquals(View.GONE, binding.exportJsonConfirmNetworkInput.visibility)
        assertEquals(View.GONE, binding.exportJsonConfirmAdvanced.visibility)
        assertEquals(View.VISIBLE, binding.exportEthereumJsonConfirmExport.visibility)
        assertEquals("{\"address\":\"substrate\"}", binding.exportSubstrateJsonConfirmValue.message())
        assertEquals("{\"address\":\"eth\"}", binding.exportEthereumJsonConfirmValue.message())
        assertFalse(binding.exportJsonConfirmNetworkInput.isEnabled)

        binding.exportSubstrateJsonConfirmExport.performClick()
        binding.exportEthereumJsonConfirmExport.performClick()
        binding.exportJsonConfirmChangePassword.performClick()
        binding.exportJsonConfirmToolbar.findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()

        verify { vm.confirmSubstrateClicked() }
        verify { vm.confirmEthereumClicked() }
        verify { vm.changePasswordClicked() }
        verify { vm.back() }
    }

    @Test
    fun walletWithoutAnEthereumKeystoreOffersOnlyTheSubstrateExport() {
        host(fromWallet = true, substrateJson = "{}", ethereumJson = " ")
        isEthereum.value = true

        assertEquals(View.VISIBLE, binding.exportSubstrateJsonConfirmValue.visibility)
        assertEquals(View.VISIBLE, binding.exportSubstrateJsonConfirmExport.visibility)
        assertEquals(View.GONE, binding.exportEthereumJsonConfirmValue.visibility)
        assertEquals(View.GONE, binding.exportEthereumJsonConfirmExport.visibility)
    }

    @Test
    fun chainExportsShowTheNetworkAndOnlyTheMatchingEcosystem() {
        host(fromWallet = false, substrateJson = "{}", ethereumJson = null)
        assertEquals(View.VISIBLE, binding.exportJsonConfirmNetworkInput.visibility)
        assertEquals("Kusama", binding.exportJsonConfirmNetworkInput.message())

        isEthereum.value = false
        assertEquals(View.VISIBLE, binding.exportJsonConfirmAdvanced.visibility)
        assertEquals(View.VISIBLE, binding.exportSubstrateJsonConfirmExport.visibility)
        assertEquals(View.GONE, binding.exportEthereumJsonConfirmExport.visibility)

        isEthereum.value = true
        assertEquals(View.GONE, binding.exportJsonConfirmAdvanced.visibility)
        assertEquals(View.GONE, binding.exportSubstrateJsonConfirmValue.visibility)
        assertEquals(View.VISIBLE, binding.exportEthereumJsonConfirmValue.visibility)
        assertEquals(View.VISIBLE, binding.exportEthereumJsonConfirmExport.visibility)
    }

    @Test
    fun exportTypeSheetSendsTheChosenFormatForTheChosenEcosystem() {
        host(fromWallet = true, substrateJson = "{}", ethereumJson = "{}")

        importType.value = Event(true)
        (ShadowDialog.getLatestDialog() as JsonExportTypeSheet).clickRow(string(R.string.json_export_text_title))
        importType.value = Event(false)
        (ShadowDialog.getLatestDialog() as JsonExportTypeSheet).clickRow(string(R.string.json_export_file_title))

        verify { vm.onExportByText(true) }
        verify { vm.onExportByFile(false) }
    }

    @Suppress("DEPRECATION")
    @Test
    fun onlyACompletedChooserCountsAsAShare() {
        host(fromWallet = true, substrateJson = "{}", ethereumJson = null)

        fragment.onActivityResult(ExportFragment.CHOOSER_REQUEST_CODE, Activity.RESULT_CANCELED, null)
        fragment.onActivityResult(ExportFragment.CHOOSER_REQUEST_CODE + 1, Activity.RESULT_OK, null)
        verify(exactly = 0) { vm.shareCompleted() }

        fragment.onActivityResult(ExportFragment.CHOOSER_REQUEST_CODE, Activity.RESULT_OK, null)
        verify(exactly = 1) { vm.shareCompleted() }
    }
}
