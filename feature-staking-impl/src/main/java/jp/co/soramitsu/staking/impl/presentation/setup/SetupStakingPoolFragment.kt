package jp.co.soramitsu.staking.impl.presentation.setup

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.staking.impl.presentation.setup.compose.SetupStakingPoolScreen

@AndroidEntryPoint
class SetupStakingPoolFragment : BaseComposeFragment<SetupStakingPoolViewModel>() {

    override val viewModel: SetupStakingPoolViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        SetupStakingPoolScreen(viewModel = viewModel)
    }
}
