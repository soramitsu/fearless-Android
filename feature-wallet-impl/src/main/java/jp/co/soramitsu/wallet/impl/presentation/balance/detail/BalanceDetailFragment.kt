package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.ExternalActionsSheet
import jp.co.soramitsu.account.api.presentation.actions.copyAddressClicked
import jp.co.soramitsu.account.api.presentation.actions.setupExternalActions
import jp.co.soramitsu.account.api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.BottomSheetLayout
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
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.SwapDetailState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransactionDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap.SwapDetailCallbacks
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap.SwapDetailFragment.Companion.CHOOSER_REQUEST_CODE
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.transfer.TransactionDetailsCallbacks
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
        lifecycle.addObserver(viewModel)
        subscribe(viewModel)

        hideKeyboard()
    }

    private fun subscribe(viewModel: BalanceDetailViewModel) {
        viewModel.sync()

        setupBuyIntegration(viewModel)
        setupExternalActions(viewModel)
        viewModel.showExportSourceChooser.observeEvent(::showExportSourceChooser)

        viewModel.showAccountOptions.observeEvent(::showAccountOptions)

        viewModel.externalActionsSelector.onEach {
            if (it != null) {
                showExternalAddressActions(it)
            }
        }.launchIn(lifecycleScope)

        viewModel.shareUrlEvent.observeEvent {
            shareUrl(it)
        }
    }

    private fun showAccountOptions(args: AccountOptionsPayload) {
        if (args.isEthereum) {
            BalanceDetailEthereumOptionsBottomSheet(
                requireContext(),
                address = args.address,
                onExportAccount = viewModel::exportClicked,
                onCopy = viewModel::copyAddressClicked,
            )
        } else {
            BalanceDetailOptionsBottomSheet(
                requireContext(),
                address = args.address,
                onExportAccount = viewModel::exportClicked,
                onSwitchNode = viewModel::switchNode,
                onCopy = viewModel::copyAddressClicked,
                onClaimReward = if (args.supportClaim) {
                    viewModel::claimRewardClicked
                } else {
                    null
                }

            )
        }.show()
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

        when (toolbarState) {
            is LoadingState.Loading<MainToolbarViewState> -> {
                MainToolbarShimmer(
                    homeIconState = ToolbarHomeIconState.Navigation(navigationIcon = R.drawable.ic_arrow_back_24dp),
                    menuItems = listOf(
                        MenuIconItem(icon = R.drawable.ic_dots_horizontal_24, {})
                    )
                )
            }

            is LoadingState.Loaded<MainToolbarViewState> -> {
                MainToolbar(
                    state = (toolbarState as LoadingState.Loaded<MainToolbarViewState>).data,
                    menuItems = listOf(
                        MenuIconItem(
                            icon = R.drawable.ic_dots_horizontal_24,
                            onClick = viewModel::accountOptionsClicked
                        )
                    ),
                    onChangeChainClick = null,
                    onNavigationClick = viewModel::backClicked
                )
            }
        }
    }

    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val detailsBottomSheetState by viewModel.transactionDetailsBottomSheet.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            snapshotFlow { modalBottomSheetState.currentValue }
                .collect {
                    if (it == ModalBottomSheetValue.Hidden) {
                        viewModel.onDetailsClose()
                    }
                }
        }
        LaunchedEffect(Unit) {
            viewModel.transactionDetailsBottomSheet.collect {
                if(it != null) {
                    modalBottomSheetState.show()
                } else {
                    modalBottomSheetState.hide()
                }
            }
        }

        BottomSheetLayout(
            bottomSheetState = modalBottomSheetState,
            content = {
                BalanceDetailsScreenWithRefreshBox(
                    state = state,
                    callback = viewModel
                )
            },
            sheetContent = { bottomSheetState ->
                if (detailsBottomSheetState != null) {
                    val callback = remember { TransactionDetailsBottomSheetCallback(viewModel, detailsBottomSheetState!!, bottomSheetState) }
                    TransactionDetailsBottomSheet(detailsBottomSheetState!!, callback)
                }
            }
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

    private fun showExternalAddressActions(payload: ExternalAccountActions.Payload) {
        val sheetPayload = ExternalActionsSheet.Payload(
            copyLabel = R.string.common_copy_address,
            content = payload
        )

        ExternalActionsSheet(
            context = requireContext(),
            payload = sheetPayload,
            onCopy = viewModel::copyStringClicked,
            onViewExternal = viewModel::openUrl
        )
            .show()
    }

    private fun shareUrl(url: String) {
        val title = getString(R.string.common_share)

        val intent = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, url)
            .setType("text/plain")

        val chooser = Intent.createChooser(intent, title)

        startActivityForResult(chooser, CHOOSER_REQUEST_CODE)
    }
}

@OptIn(ExperimentalMaterialApi::class)
class TransactionDetailsBottomSheetCallback(
    private val viewModel: BalanceDetailViewModel,
    private val detailsBottomSheetState: TransactionDetailsState,
    private val bottomSheetState: ModalBottomSheetState
): TransactionDetailsCallbacks, SwapDetailCallbacks {

    override fun onNavigationClick() {
        viewModel.launch {
            viewModel.onDetailsClose()
        }
    }

    override fun onHashClick() {
        viewModel.transactionHashClicked(detailsBottomSheetState)
    }

    override fun onFromClick() {
        viewModel.fromClicked(detailsBottomSheetState)
    }

    override fun onToClick() {
        viewModel.toClicked(detailsBottomSheetState)
    }

    override fun onBackClick() {
        viewModel.launch {
            viewModel.onDetailsClose()
        }
    }

    override fun onCloseClick() {
        viewModel.launch {
            viewModel.onDetailsClose()
        }
    }

    override fun onItemClick(code: Int) {
        val swapDetailsHashItemCode = 1
        if(code == swapDetailsHashItemCode) {
            require(detailsBottomSheetState is SwapDetailState)
            viewModel.onSwapDetailsHashClick(detailsBottomSheetState.hash)
        }
    }

    override fun onSubscanClick() {
        require(detailsBottomSheetState is SwapDetailState)

        viewModel.onSwapDetailsSubscanClicked(detailsBottomSheetState.hash)
    }

    override fun onShareClick() {
        require(detailsBottomSheetState is SwapDetailState)
        viewModel.onShareSwapClicked(detailsBottomSheetState.hash)
    }

}