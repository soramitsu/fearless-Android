package jp.co.soramitsu.staking.impl.presentation.confirm.pool.create

import android.content.DialogInterface
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.ConfirmScreen
import jp.co.soramitsu.staking.impl.presentation.confirm.pool.join.PoolJoinRecoveryScreen

@AndroidEntryPoint
class ConfirmCreatePoolFragment : BaseComposeBottomSheetDialogFragment<ConfirmCreatePoolViewModel>() {
    override val viewModel: ConfirmCreatePoolViewModel by viewModels()

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        super.setupBehavior(behavior)
        // Material system Back must be able to cancel both confirmation and retained review.
        behavior.isHideable = true
    }

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
        val recovery by viewModel.recoveryViewState.collectAsState()
        recovery?.let {
            PoolJoinRecoveryScreen(it, viewModel::onBackClick, viewModel::onCheckPending, viewModel::onReturnToPool)
        } ?: run {
            val state by viewModel.viewState.collectAsState()
            ConfirmScreen(state = state, onNavigationClick = viewModel::onBackClick, viewModel::onConfirm)
        }
    }
}
