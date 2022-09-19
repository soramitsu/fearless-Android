package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import com.google.zxing.integration.android.IntentIntegrator
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
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.common.askPermissionsSafely
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BalanceListFragment : BaseComposeFragment<BalanceListViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    override val viewModel: BalanceListViewModel by viewModels()

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
        WalletScreen(viewModel, modalBottomSheetState)
    }

    @ExperimentalMaterialApi
    @Composable
    override fun Toolbar(modalBottomSheetState: ModalBottomSheetState) {
        val toolbarState by viewModel.toolbarState.collectAsState()
        val coroutineScope = rememberCoroutineScope()

        when (toolbarState) {
            is LoadingState.Loading<MainToolbarViewState> -> {
                MainToolbarShimmer(
                    homeIconState = ToolbarHomeIconState(navigationIcon = jp.co.soramitsu.common.R.drawable.ic_wallet),
                    menuItems = listOf(
                        MenuIconItem(icon = jp.co.soramitsu.common.R.drawable.ic_scan, {}),
                        MenuIconItem(icon = jp.co.soramitsu.common.R.drawable.ic_search, {})
                    )
                )
            }
            is LoadingState.Loaded<MainToolbarViewState> -> {
                MainToolbar(
                    state = (toolbarState as LoadingState.Loaded<MainToolbarViewState>).data,
                    menuItems = listOf(
                        MenuIconItem(icon = R.drawable.ic_scan) { requestCameraPermission() },
                        MenuIconItem(icon = R.drawable.ic_search) {}
                    ),
                    onChangeChainClick = {
                        coroutineScope.launch {
                            modalBottomSheetState.show()
                        }
                    },
                    onNavigationClick = {
                        viewModel.openWalletSelector()
                    }
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
        viewModel.decodeAddressResult.observeEvent {
            viewModel.showMessage("SCANNED: $it")
            // TODO use. old scenario was: place to search field
        }
    }

    fun initViews() {
//        with(binding) {
//            walletContainer.setOnRefreshListener {
//                viewModel.sync()
//            }
//
//            manageAssets.setWholeClickListener {
//                viewModel.manageAssetsClicked()
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

        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        result?.contents?.let {
            viewModel.qrCodeScanned(it)
        }
    }
}
