package jp.co.soramitsu.staking.impl.presentation.pools

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectPoolScreen

@AndroidEntryPoint
class SelectPoolFragment : BaseComposeFragment<SelectPoolViewModel>() {
    override val viewModel: SelectPoolViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        val state = viewModel.viewState.collectAsState()
        SelectPoolScreen(
            state = state.value,
            onNavigationClick = viewModel::onBackClick,
            onPoolSelected = viewModel::onPoolSelected,
            onInfoClick = viewModel::onInfoClick,
            onChooseClick = viewModel::onNextClick
        )
    }
}
