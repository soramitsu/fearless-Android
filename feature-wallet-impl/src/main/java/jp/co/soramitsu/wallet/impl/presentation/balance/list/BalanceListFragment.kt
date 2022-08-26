package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.PLAY_MARKET_APP_URI
import jp.co.soramitsu.common.PLAY_MARKET_BROWSER_URI
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.MainToolbar
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.presentation.FiatCurrenciesChooserBottomSheetDialog
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R

@AndroidEntryPoint
class BalanceListFragment : BaseComposeFragment<BalanceListViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    override val viewModel: BalanceListViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        WalletScreen(viewModel)
    }

    @Composable
    override fun Toolbar() {
        val toolbarState by viewModel.toolbarState.collectAsState()

        when (toolbarState) {
            is LoadingState.Loading<MainToolbarViewState> -> {}
            is LoadingState.Loaded<MainToolbarViewState> -> {
                MainToolbar(
                    state = (toolbarState as LoadingState.Loaded<MainToolbarViewState>).data,
                    menuItems = listOf(
                        MenuIconItem(icon = R.drawable.ic_scan, {}),
                        MenuIconItem(icon = R.drawable.ic_search, {})
                    ),
                    onChangeChainClick = { },
                    onNavigationClick = { viewModel.avatarClicked() }
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
}
