package jp.co.soramitsu.tonconnect.impl.presentation.discoverdapp

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.MainToolbar
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.presentation.FiatCurrenciesChooserBottomSheetDialog
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R

@AndroidEntryPoint
class DiscoverDappFragment : BaseComposeFragment<DiscoverDappViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    override val viewModel: DiscoverDappViewModel by viewModels()

    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        val state by viewModel.state.collectAsState()

        DiscoverDappScreen(state, viewModel)
    }

    @ExperimentalMaterialApi
    @Composable
    override fun Toolbar(modalBottomSheetState: ModalBottomSheetState) {
        val toolbarState by viewModel.toolbarState.collectAsState()

        Column {
            MainToolbar(
                state = toolbarState,
                menuItems = listOf(
                    MenuIconItem(
                        icon = R.drawable.ic_search,
                        onClick = viewModel::openSearch
                    )
                ),
                onChangeChainClick = viewModel::openSelectChain,
                onNavigationClick = viewModel::openWalletSelector,
                onScoreClick = viewModel::onScoreClick
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideKeyboard()
    }
}
