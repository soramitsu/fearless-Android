package jp.co.soramitsu.polkaswap.impl.presentation.select_market

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen

@AndroidEntryPoint
class SelectMarketFragment : BaseComposeFragment<SelectMarketViewModel>() {

    companion object {
        const val MARKET_KEY = "marketKey"
    }

    override val viewModel: SelectMarketViewModel by viewModels()

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        BottomSheetScreen {
            val state by viewModel.state.collectAsState()
            SelectMarketContent(state, marketSelected = viewModel::marketSelected)
        }
    }

}
