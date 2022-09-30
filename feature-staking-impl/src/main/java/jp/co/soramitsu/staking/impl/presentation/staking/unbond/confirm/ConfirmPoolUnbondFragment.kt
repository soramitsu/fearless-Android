package jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.ConfirmScreen

@AndroidEntryPoint
class ConfirmPoolUnbondFragment : BaseComposeBottomSheetDialogFragment<ConfirmPoolUnbondViewModel>() {
    override val viewModel: ConfirmPoolUnbondViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state = viewModel.viewState.collectAsState()
        ConfirmScreen(state = state.value, onNavigationClick = viewModel::onBackClick, onConfirm = viewModel::onConfirm)
    }
}
