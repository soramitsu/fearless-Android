package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

import android.content.DialogInterface
import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class PoolJoinRecoveryFragment : BaseComposeBottomSheetDialogFragment<PoolJoinRecoveryViewModel>() {
    override val viewModel: PoolJoinRecoveryViewModel by viewModels()

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        super.setupBehavior(behavior)
        // Material's system Back handler hides the sheet before cancellation. The inherited
        // non-hideable policy otherwise consumes Back without leaving this read-only review.
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
        val state = viewModel.viewState.collectAsState()
        PoolJoinRecoveryScreen(state.value, viewModel::onBackClick, viewModel::onCheckPending, viewModel::onReturnToPool)
    }
}
