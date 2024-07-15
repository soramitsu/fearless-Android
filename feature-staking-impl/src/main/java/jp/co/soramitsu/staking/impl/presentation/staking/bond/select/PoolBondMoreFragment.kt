package jp.co.soramitsu.staking.impl.presentation.staking.bond.select

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
class PoolBondMoreFragment : BaseComposeBottomSheetDialogFragment<PoolBondMoreViewModel>() {
    override val viewModel: PoolBondMoreViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        BottomSheetScreen {
            EnterAmountScreen(
                state = state,
                onNavigationClick = viewModel::onBackClick,
                onAmountInput = viewModel::onAmountInput,
                onNextClick = viewModel::onNextClick
            )
        }
    }
}
