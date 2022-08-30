package jp.co.soramitsu.staking.impl.presentation.setup

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.staking.impl.presentation.setup.compose.StartStakingPoolScreen

@AndroidEntryPoint
class StartStakingPoolFragment : BaseComposeFragment<StartStakingPoolViewModel>() {

    override val viewModel: StartStakingPoolViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        StartStakingPoolScreen(viewModel = viewModel)
    }
}
