package jp.co.soramitsu.polkaswap.impl.presentation.disclaimer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class PolkaswapDisclaimerFragment : BaseComposeBottomSheetDialogFragment<PolkaswapDisclaimerViewModel>() {
    override val viewModel: PolkaswapDisclaimerViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        PolkaswapDisclaimerScreen(state = state, callbacks = viewModel)
    }
}
