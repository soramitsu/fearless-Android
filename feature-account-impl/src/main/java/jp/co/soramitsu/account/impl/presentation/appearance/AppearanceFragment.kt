package jp.co.soramitsu.account.impl.presentation.appearance

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

@AndroidEntryPoint
class AppearanceFragment : BaseComposeFragment<AppearanceViewModel>() {

    override val viewModel: AppearanceViewModel by viewModels()

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
        val state by viewModel.state.collectAsState()
        AppearanceScreen(
            state = state,
            scrollState = scrollState,
            onBack = viewModel::back,
            onReducedChanged = viewModel::onReducedChanged,
            onContrastChanged = viewModel::onContrastChanged,
            onRetry = viewModel::retry
        )
    }
}
