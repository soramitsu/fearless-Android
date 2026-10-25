package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

import android.content.DialogInterface
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class ConfirmJoinPoolFragment : BaseComposeBottomSheetDialogFragment<ConfirmJoinPoolViewModel>() {
    override val viewModel: ConfirmJoinPoolViewModel by viewModels()

    override fun onCancel(dialog: DialogInterface) {
        viewModel.onDismissed()
        super.onCancel(dialog)
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (activity?.isChangingConfigurations != true) viewModel.onDismissed()
        super.onDismiss(dialog)
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val state = viewModel.viewState.collectAsState()
        ConfirmJoinPoolScreen(state = state.value, onNavigationClick = viewModel::onBackClick, onConfirm = viewModel::onConfirm,
            onCheckPending = viewModel::onCheckPending, onReturnToPool = viewModel::onReturnToPool)
    }
}
