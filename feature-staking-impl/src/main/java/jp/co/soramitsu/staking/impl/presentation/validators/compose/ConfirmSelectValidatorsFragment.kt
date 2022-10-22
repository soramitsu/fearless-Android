package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.ConfirmScreen

@AndroidEntryPoint
class ConfirmSelectValidatorsFragment : BaseComposeBottomSheetDialogFragment<ConfirmSelectValidatorsViewModel>() {
    override val viewModel: ConfirmSelectValidatorsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.viewState.collectAsState()
        ConfirmScreen(state = state, onNavigationClick = viewModel::onNavigationClick, onConfirm = viewModel::onConfirm)
    }
}
