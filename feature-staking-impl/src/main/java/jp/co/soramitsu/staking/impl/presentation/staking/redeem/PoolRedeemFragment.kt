package jp.co.soramitsu.staking.impl.presentation.staking.redeem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.EnterAmountScreen

@AndroidEntryPoint
class PoolRedeemFragment : BaseComposeBottomSheetDialogFragment<PoolRedeemViewModel>() {
    override val viewModel: PoolRedeemViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state = viewModel.state.collectAsState()
        BottomSheetScreen {
            EnterAmountScreen(
                state = state.value,
                onNavigationClick = viewModel::onBackClick,
                onAmountInput = viewModel::onAmountInput,
                onNextClick = viewModel::onNextClick
            )
        }
    }
}
