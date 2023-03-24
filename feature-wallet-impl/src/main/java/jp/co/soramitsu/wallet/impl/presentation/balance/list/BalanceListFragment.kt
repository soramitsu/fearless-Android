package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.PLAY_MARKET_APP_URI
import jp.co.soramitsu.common.PLAY_MARKET_BROWSER_URI
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.MainToolbar
import jp.co.soramitsu.common.compose.component.MainToolbarShimmer
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.presentation.FiatCurrenciesChooserBottomSheetDialog
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.scan.ScanTextContract
import jp.co.soramitsu.common.scan.ScannerActivity
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardResult
import jp.co.soramitsu.oauth.base.sdk.signin.SoraCardSignInContract
import jp.co.soramitsu.wallet.impl.presentation.common.askPermissionsSafely
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BalanceListFragment : BaseComposeFragment<BalanceListViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    override val viewModel: BalanceListViewModel by viewModels()

    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> = registerForActivityResult(ScanTextContract()) { result ->
        result?.let {
            viewModel.qrCodeScanned(it)
        }
    }

    private val soraCardSignIn = registerForActivityResult(
        SoraCardSignInContract()
    ) { result ->
        when (result) {
            is SoraCardResult.Failure -> {}
            is SoraCardResult.Canceled -> {}
            is SoraCardResult.Success -> {
                viewModel.updateSoraCardInfo(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    accessTokenExpirationTime = result.accessTokenExpirationTime,
                    kycStatus = result.status.toString()
                )
            }
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
        val state by viewModel.state.collectAsState()

        WalletScreen(state, viewModel)
    }

    @ExperimentalMaterialApi
    @Composable
    override fun Toolbar(modalBottomSheetState: ModalBottomSheetState) {
        val toolbarState by viewModel.toolbarState.collectAsState()

        when (toolbarState) {
            is LoadingState.Loading<MainToolbarViewState> -> {
                MainToolbarShimmer(
                    homeIconState = ToolbarHomeIconState(navigationIcon = R.drawable.ic_wallet),
                    menuItems = listOf(
                        MenuIconItem(icon = R.drawable.ic_scan) {},
                        MenuIconItem(icon = R.drawable.ic_search) {}
                    )
                )
            }
            is LoadingState.Loaded<MainToolbarViewState> -> {
                MainToolbar(
                    state = (toolbarState as LoadingState.Loaded<MainToolbarViewState>).data,
                    menuItems = listOf(
                        MenuIconItem(icon = R.drawable.ic_scan, onClick = ::requestCameraPermission),
                        MenuIconItem(icon = R.drawable.ic_search, onClick = viewModel::openSearchAssets)
                    ),
                    onChangeChainClick = viewModel::openSelectChain,
                    onNavigationClick = viewModel::openWalletSelector
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideKeyboard()

        viewModel.showFiatChooser.observeEvent(::showFiatChooser)
        viewModel.showUnsupportedChainAlert.observeEvent { showUnsupportedChainAlert() }
        viewModel.openPlayMarket.observeEvent { openPlayMarket() }
        viewModel.launchSoraCardSignIn.observeEvent { contractData ->
            soraCardSignIn.launch(contractData)
        }
    }

    fun initViews() {
//        with(binding) {
//            walletContainer.setOnRefreshListener {
//                viewModel.sync()
//            }
//        }
    }

    private fun showFiatChooser(payload: DynamicListBottomSheet.Payload<FiatCurrency>) {
        FiatCurrenciesChooserBottomSheetDialog(requireContext(), imageLoader, payload, viewModel::onFiatSelected).show()
    }

    private fun showUnsupportedChainAlert() {
        AlertBottomSheet.Builder(requireContext())
            .setTitle(R.string.update_needed_text)
            .setMessage(R.string.chain_unsupported_text)
            .setButtonText(R.string.common_update)
            .callback { viewModel.updateAppClicked() }
            .build()
            .show()
    }

    private fun openPlayMarket() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_MARKET_APP_URI)))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_MARKET_BROWSER_URI)))
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
