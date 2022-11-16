package jp.co.soramitsu.wallet.impl.presentation.send.setup

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.zxing.integration.android.IntentIntegrator
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.common.askPermissionsSafely
import jp.co.soramitsu.wallet.impl.presentation.send.recipient.QrCodeSourceChooserBottomSheet
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SendSetupFragment : BaseComposeBottomSheetDialogFragment<SendSetupViewModel>() {

    companion object {
        private const val PICK_IMAGE_REQUEST = 267
        private const val QR_CODE_IMAGE_TYPE = "image/*"

        const val KEY_PAYLOAD = "payload"
        const val KEY_INITIAL_ADDRESS = "KEY_INITIAL_ADDRESS"
        fun getBundle(payload: AssetPayload?, initSendToAddress: String?) = bundleOf(
            KEY_PAYLOAD to payload,
            KEY_INITIAL_ADDRESS to initSendToAddress
        )
    }

    override val viewModel: SendSetupViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        SendSetupContent(
            state = state,
            callback = viewModel
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.showChooserEvent.observeEvent {
            QrCodeSourceChooserBottomSheet(requireContext(), ::requestCameraPermission, ::selectQrFromGallery)
                .show()
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
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
