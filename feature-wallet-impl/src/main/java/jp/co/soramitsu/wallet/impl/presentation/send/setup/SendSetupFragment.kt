package jp.co.soramitsu.wallet.impl.presentation.send.setup

import android.Manifest
import android.graphics.Rect
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
import java.math.BigDecimal
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.presentation.askPermissionsSafely
import jp.co.soramitsu.common.scan.ScanTextContract
import jp.co.soramitsu.common.scan.ScannerActivity
import jp.co.soramitsu.wallet.impl.presentation.model.AssetPayload
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SendSetupFragment : BaseComposeBottomSheetDialogFragment<SendSetupViewModel>() {

    companion object {
        const val KEY_PAYLOAD = "payload"
        const val KEY_INITIAL_ADDRESS = "KEY_INITIAL_ADDRESS"
        const val KEY_INITIAL_AMOUNT = "KEY_INITIAL_AMOUNT"
        const val KEY_LOCK_AMOUNT = "KEY_LOCK_AMOUNT"
        const val KEY_TOKEN_ID = "KEY_TOKEN_ID"

        fun getBundle(payload: AssetPayload?, initSendToAddress: String?, currencyId: String?, amount: BigDecimal?, lockInput: Boolean) = bundleOf(
            KEY_PAYLOAD to payload,
            KEY_INITIAL_ADDRESS to initSendToAddress,
            KEY_TOKEN_ID to currencyId,
            KEY_INITIAL_AMOUNT to amount,
            KEY_LOCK_AMOUNT to lockInput
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

        view.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            // r will be populated with the coordinates of your view that area still visible.
            view.getWindowVisibleDisplayFrame(r)
            val heightDiff: Int = view.rootView.height - (r.bottom - r.top)

            // if more than 100 pixels, its probably a keyboard...
            viewModel.setSoftKeyboardOpen(heightDiff > 500)
        }

        viewModel.openScannerEvent.observeEvent {
            requestCameraPermission()
        }
        viewModel.openValidationWarningEvent.observeEvent { (result, warning) ->
            ErrorDialog(
                title = warning.message,
                message = warning.explanation,
                positiveButtonText = warning.positiveButtonText,
                secondPositiveButtonText = warning.secondPositiveButtonText,
                negativeButtonText = warning.negativeButtonText,
                positiveClick = { viewModel.warningConfirmed(result) },
                secondPositiveClick = { viewModel.warningConfirmedSecond(result) },
                negativeClick = { viewModel.warningCancelled(result) },
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
