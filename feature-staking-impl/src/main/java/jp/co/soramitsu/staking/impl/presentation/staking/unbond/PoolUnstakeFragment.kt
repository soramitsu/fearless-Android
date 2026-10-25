package jp.co.soramitsu.staking.impl.presentation.staking.unbond

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
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.EnterAmountScreen

@AndroidEntryPoint
class PoolUnstakeFragment : BaseComposeBottomSheetDialogFragment<PoolUnstakeViewModel>() {
    override val viewModel: PoolUnstakeViewModel by viewModels()

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        super.setupBehavior(behavior)
        behavior.isHideable = true
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed()
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
        val state = viewModel.state.collectAsState()
        val isSoftKeyboardOpen by viewModel.isSoftKeyboardOpenFlow.collectAsState()
        BottomSheetScreen {
            EnterAmountScreen(
                state = state.value,
                isSoftKeyboardOpen = isSoftKeyboardOpen,
                onNavigationClick = viewModel::onBackClick,
                onAmountInput = viewModel::onAmountInput,
                onQuickAmountInput = viewModel::onQuickAmountInput,
                onNextClick = viewModel::onNextClick
            )
        }
    }
}
