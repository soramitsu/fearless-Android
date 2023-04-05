package jp.co.soramitsu.wallet.impl.presentation.cross_chain

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.scan.ScanTextContract
import jp.co.soramitsu.common.scan.ScannerActivity
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.common.askPermissionsSafely
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CrossChainFragment : BaseComposeBottomSheetDialogFragment<CrossChainViewModel>() {

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

    override val viewModel: CrossChainViewModel by viewModels()

    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> = registerForActivityResult(ScanTextContract()) { result ->
        result?.let {
            viewModel.qrCodeScanned(it)
        }
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        CrossChainContent(
            state = state,
            callback = viewModel
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.openScannerEvent
                    .onEach { requestCameraPermission() }
                    .launchIn(this)
            }
        }
        viewModel.openValidationWarningEvent.observeEvent { (result, warning) ->
            ErrorDialog(
                title = warning.message,
                message = warning.explanation,
                positiveButtonText = warning.positiveButtonText,
                negativeButtonText = warning.negativeButtonText,
                positiveClick = { viewModel.warningConfirmed(result) },
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
