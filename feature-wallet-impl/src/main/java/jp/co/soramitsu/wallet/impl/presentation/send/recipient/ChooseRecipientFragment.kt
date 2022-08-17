package jp.co.soramitsu.wallet.impl.presentation.send.recipient

import android.Manifest
import android.app.Activity
import android.content.Intent
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.zxing.integration.android.IntentIntegrator
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.onDoneClicked
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentChooseRecipientBinding
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.common.askPermissionsSafely
import jp.co.soramitsu.wallet.impl.presentation.send.phishing.observePhishingCheck
import kotlinx.coroutines.launch

private const val INDEX_WELCOME = 0
private const val INDEX_CONTENT = 1
private const val INDEX_EMPTY = 2
const val KEY_ASSET_PAYLOAD = "KEY_ASSET_PAYLOAD"

@AndroidEntryPoint
class ChooseRecipientFragment : BaseFragment<ChooseRecipientViewModel>(R.layout.fragment_choose_recipient), ChooseRecipientAdapter.RecipientItemHandler {

    companion object {
        private const val PICK_IMAGE_REQUEST = 101
        private const val QR_CODE_IMAGE_TYPE = "image/*"

        fun getBundle(assetPayload: AssetPayload) = bundleOf(KEY_ASSET_PAYLOAD to assetPayload)
    }

    private lateinit var adapter: ChooseRecipientAdapter

    private val binding by viewBinding(FragmentChooseRecipientBinding::bind)

    override val viewModel: ChooseRecipientViewModel by viewModels()

    override fun initViews() {
        adapter = ChooseRecipientAdapter(this)

        binding.searchRecipientList.setHasFixedSize(true)
        binding.searchRecipientList.adapter = adapter

        binding.searchRecipientToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }

        binding.searchRecipientToolbar.setRightActionClickListener {
            viewModel.scanClicked()
        }

        binding.searchRecipientField.onDoneClicked {
            viewModel.enterClicked()
        }
    }

    override fun subscribe(viewModel: ChooseRecipientViewModel) {
        viewModel.screenStateLiveData.observe {
            val index = when (it) {
                State.WELCOME -> INDEX_WELCOME
                State.CONTENT -> INDEX_CONTENT
                State.EMPTY -> INDEX_EMPTY
            }

            binding.searchRecipientFlipper.displayedChild = index
        }

        viewModel.searchResultLiveData.observe(adapter::submitList)

        viewModel.showChooserEvent.observeEvent {
            QrCodeSourceChooserBottomSheet(requireContext(), ::requestCameraPermission, ::selectQrFromGallery)
                .show()
        }

        viewModel.decodeAddressResult.observeEvent {
            binding.searchRecipientField.setText(it)
        }

        viewModel.declinePhishingAddress.observeEvent {
            binding.searchRecipientField.setText("")
        }

        observePhishingCheck(viewModel)

        binding.searchRecipientField.onTextChanged(viewModel::queryChanged)

        viewModel.assetSymbolLiveData.observe {
            binding.searchRecipientToolbar.setTitle(getString(R.string.wallet_send_navigation_title, it))
        }
    }

    private fun requestCameraPermission() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = askPermissionsSafely(Manifest.permission.CAMERA)

            if (result.isSuccess) {
                initiateCameraScanner()
            }
        }
    }

    private fun selectQrFromGallery() {
        val intent = Intent().apply {
            type = QR_CODE_IMAGE_TYPE
            action = Intent.ACTION_GET_CONTENT
        }

        startActivityForResult(Intent.createChooser(intent, getString(R.string.common_options_title)), PICK_IMAGE_REQUEST)
    }

    private fun initiateCameraScanner() {
        val integrator = IntentIntegrator.forSupportFragment(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES)
            setPrompt("")
            setBeepEnabled(false)
        }
        integrator.initiateScan()
    }

    override fun contactClicked(address: String) {
        viewModel.recipientSelected(address)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data?.data != null) {
            viewModel.qrFileChosen(data.data!!)
        } else {
            val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
            result?.contents?.let {
                viewModel.qrCodeScanned(it)
            }
        }
    }
}
