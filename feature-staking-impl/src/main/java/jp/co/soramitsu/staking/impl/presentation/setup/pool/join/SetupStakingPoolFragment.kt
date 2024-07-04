package jp.co.soramitsu.staking.impl.presentation.setup.pool.join

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class SetupStakingPoolFragment : BaseComposeBottomSheetDialogFragment<SetupStakingPoolViewModel>() {
    override val viewModel: SetupStakingPoolViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state = viewModel.viewState.collectAsState()
        val isSoftKeyboardOpen by viewModel.isSoftKeyboardOpenFlow.collectAsState()
        SetupStakingScreen(
            state.value,
            isSoftKeyboardOpen = isSoftKeyboardOpen,
            onNavigationClick = viewModel::onNavigationClick,
            onAmountInput = viewModel::onAmountEntered,
            onQuickAmountInput = viewModel::onQuickAmountInput,
            onNextClick = viewModel::onNextClick
        )
    }
}
