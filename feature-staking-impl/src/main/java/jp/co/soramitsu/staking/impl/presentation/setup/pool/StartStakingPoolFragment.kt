package jp.co.soramitsu.staking.impl.presentation.setup.pool

import android.content.DialogInterface
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class StartStakingPoolFragment : BaseComposeBottomSheetDialogFragment<StartStakingPoolViewModel>() {

    override val viewModel: StartStakingPoolViewModel by viewModels()

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
        StartStakingPoolScreen(viewModel = viewModel)
    }
}
