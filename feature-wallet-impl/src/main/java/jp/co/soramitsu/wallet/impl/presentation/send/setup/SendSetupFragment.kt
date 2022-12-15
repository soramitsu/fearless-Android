package jp.co.soramitsu.wallet.impl.presentation.send.setup

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.scan.ScanTextContract
import jp.co.soramitsu.common.scan.ScannerActivity
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.common.askPermissionsSafely
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SendSetupFragment : BaseComposeBottomSheetDialogFragment<SendSetupViewModel>() {

    companion object {
        const val KEY_PAYLOAD = "payload"
        const val KEY_INITIAL_ADDRESS = "KEY_INITIAL_ADDRESS"
        const val KEY_TOKEN_ID = "KEY_TOKEN_ID"
        fun getBundle(payload: AssetPayload?, initSendToAddress: String?, currencyId: String?) = bundleOf(
            KEY_PAYLOAD to payload,
            KEY_INITIAL_ADDRESS to initSendToAddress,
            KEY_TOKEN_ID to currencyId
        )
    }

    override val viewModel: SendSetupViewModel by viewModels()

    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> = registerForActivityResult(ScanTextContract()) { result ->
        result?.let {
            viewModel.qrCodeScanned(it)
        }
    }

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
        viewModel.openScannerEvent.observeEvent {
            requestCameraPermission()
        }
        viewModel.openValidationWarningEvent.observeEvent {
            ErrorDialog(
                title = it.message,
                message = it.explanation,
                positiveButtonText = it.positiveButtonText,
                negativeButtonText = it.negativeButtonText,
                positiveClick = viewModel::existentialDepositWarningConfirmed,
                isHideable = false
            ).show(childFragmentManager)
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

    private fun initiateCameraScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            .setPrompt("")
            .setBeepEnabled(false)
            .setCaptureActivity(ScannerActivity::class.java)
        barcodeLauncher.launch(options)
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
