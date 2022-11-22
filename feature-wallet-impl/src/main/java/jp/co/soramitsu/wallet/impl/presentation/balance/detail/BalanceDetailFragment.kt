package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.api.presentation.actions.copyAddressClicked
import jp.co.soramitsu.account.api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.MainToolbar
import jp.co.soramitsu.common.compose.component.MainToolbarShimmer
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.setupBuyIntegration
import kotlinx.coroutines.launch

const val KEY_ASSET_PAYLOAD = "KEY_ASSET_PAYLOAD"

@AndroidEntryPoint
class BalanceDetailFragment : BaseComposeFragment<BalanceDetailViewModel>() {

    companion object {
        fun getBundle(assetPayload: AssetPayload) = bundleOf(KEY_ASSET_PAYLOAD to assetPayload)
    }

    override val viewModel: BalanceDetailViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        subscribe(viewModel)

        hideKeyboard()
    }

    private fun subscribe(viewModel: BalanceDetailViewModel) {
        viewModel.sync()

        setupBuyIntegration(viewModel)

        viewModel.showExportSourceChooser.observeEvent(::showExportSourceChooser)

        viewModel.showAccountOptions.observeEvent(::showAccountOptions)
    }

    private fun showAccountOptions(address: String) {
        BalanceDetailOptionsBottomSheet(
            requireContext(),
            address = address,
            onExportAccount = viewModel::exportClicked,
            onSwitchNode = viewModel::switchNode,
            onCopy = viewModel::copyAddressClicked
        ).show()
    }

    private fun showExportSourceChooser(payload: ExportSourceChooserPayload) {
        SourceTypeChooserBottomSheetDialog(
            titleRes = R.string.select_save_type,
            context = requireActivity(),
            payload = DynamicListBottomSheet.Payload(payload.sources),
            onClicked = { viewModel.exportTypeSelected(it, payload.chainId) }
        ).show()
    }

    @ExperimentalMaterialApi
    @Composable
    override fun Toolbar(modalBottomSheetState: ModalBottomSheetState) {
        val toolbarState by viewModel.toolbarState.collectAsState()
        val coroutineScope = rememberCoroutineScope()

        when (toolbarState) {
            is LoadingState.Loading<MainToolbarViewState> -> {
                MainToolbarShimmer(
                    homeIconState = ToolbarHomeIconState(navigationIcon = jp.co.soramitsu.common.R.drawable.ic_arrow_back_24dp),
                    menuItems = listOf(
                        MenuIconItem(icon = jp.co.soramitsu.common.R.drawable.ic_dots_horizontal_24, {})
                    )
                )
            }
            is LoadingState.Loaded<MainToolbarViewState> -> {
                MainToolbar(
                    state = (toolbarState as LoadingState.Loaded<MainToolbarViewState>).data,
                    menuItems = listOf(
                        MenuIconItem(
                            icon = jp.co.soramitsu.common.R.drawable.ic_dots_horizontal_24,
                            viewModel::accountOptionsClicked
                        )
                    ),
                    onChangeChainClick = {
                        coroutineScope.launch {
                            modalBottomSheetState.show()
                        }
                    },
                    onNavigationClick = {
                        viewModel.backClicked()
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
        val state by viewModel.state.collectAsState()
        val chainsState by viewModel.chainsState.collectAsState()
        val isRefreshing by viewModel.isRefreshing.collectAsState()

        BalanceDetailsScreen(
            state = state,
            chainsState = chainsState,
            isRefreshing = isRefreshing,
            modalBottomSheetState = modalBottomSheetState,
            callback = viewModel
        )
    }

    @Composable
    override fun Background() {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                modifier = Modifier.fillMaxSize(),
                painter = painterResource(id = R.drawable.drawable_background_image),
                contentScale = ContentScale.FillBounds,
                contentDescription = null
            )
        }
    }
}
