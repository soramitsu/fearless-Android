package jp.co.soramitsu.staking.impl.presentation.setup.pool

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class StartStakingPoolFragment : BaseComposeBottomSheetDialogFragment<StartStakingPoolViewModel>() {

    override val viewModel: StartStakingPoolViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        StartStakingPoolScreen(viewModel = viewModel)
    }
}
