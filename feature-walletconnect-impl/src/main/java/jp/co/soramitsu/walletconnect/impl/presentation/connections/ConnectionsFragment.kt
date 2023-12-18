package jp.co.soramitsu.walletconnect.impl.presentation.connections

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.presentation.askPermissionsSafely
import jp.co.soramitsu.common.scan.ScanTextContract
import jp.co.soramitsu.common.scan.ScannerActivity
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConnectionsFragment : BaseComposeBottomSheetDialogFragment<ConnectionsViewModel>() {

    override val viewModel: ConnectionsViewModel by viewModels()

    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> = registerForActivityResult(ScanTextContract()) { result ->
        result?.let {
            viewModel.qrCodeScanned(it)
        }
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        ConnectionsContent(
            state = state,
            onSessionClick = viewModel::onSessionClicked,
            onSearchInput = viewModel::onSearchInput,
            onClose = viewModel::onClose,
            onCreateNewConnection = viewModel::onCreateNewConnection,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.openScannerEvent.observeEvent {
            requestCameraPermission()
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
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
}
