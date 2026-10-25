package jp.co.soramitsu.staking.impl.presentation.staking.claim

import android.content.DialogInterface
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.ConfirmScreen

@AndroidEntryPoint
class ConfirmPoolClaimFragment : BaseComposeBottomSheetDialogFragment<ConfirmPoolClaimViewModel>() {
    override val viewModel: ConfirmPoolClaimViewModel by viewModels()

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        super.setupBehavior(behavior)
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
        ConfirmScreen(state = state.value, onNavigationClick = viewModel::onBackClick, onConfirm = viewModel::onConfirm)
    }
}
