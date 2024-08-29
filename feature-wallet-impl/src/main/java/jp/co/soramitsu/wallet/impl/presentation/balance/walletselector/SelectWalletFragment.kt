package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import jp.co.soramitsu.common.utils.isGooglePlayServicesAvailable
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SelectWalletFragment : BaseComposeBottomSheetDialogFragment<SelectWalletViewModel>() {
    override val viewModel: SelectWalletViewModel by viewModels()

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> viewModel.openAddWalletThroughGoogleScreen()
            Activity.RESULT_CANCELED -> { /* no action */ }
            else -> {
                val googleSignInStatus = result.data?.extras?.get("googleSignInStatus")
                viewModel.onGoogleLoginError(googleSignInStatus.toString())
            }
        }
    }

    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> = registerForActivityResult(
        ScanTextContract()
    ) { result ->
        viewModel.onQrScanResult(result)
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        SelectWalletContent(
            state = state,
            onWalletSelected = viewModel::onWalletSelected,
            addNewWallet = viewModel::addNewWallet,
            importWallet = viewModel::importWallet,
            onBackClicked = viewModel::onBackClicked,
            onWalletOptionsClick = viewModel::onWalletOptionsClick,
            onScoreClick = viewModel::onScoreClick,
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isGoogleAvailable = context?.isGooglePlayServicesAvailable() == true
        if (isGoogleAvailable) {
            viewModel.googleAuthorizeLiveData.observeEvent {
                viewModel.authorizeGoogle(launcher = launcher)
            }
        }

        viewModel.importPreInstalledWalletLiveData.observeEvent {
            requestCameraPermission()
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
}
