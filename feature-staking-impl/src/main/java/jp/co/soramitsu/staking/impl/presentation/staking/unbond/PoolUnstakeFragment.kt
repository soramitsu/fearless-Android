package jp.co.soramitsu.staking.impl.presentation.staking.unbond

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
