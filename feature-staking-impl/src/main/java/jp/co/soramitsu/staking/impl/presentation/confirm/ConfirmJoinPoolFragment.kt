package jp.co.soramitsu.staking.impl.presentation.confirm

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.staking.impl.presentation.confirm.compose.ConfirmJoinPoolScreen

@AndroidEntryPoint
class ConfirmJoinPoolFragment : BaseComposeFragment<ConfirmJoinPoolViewModel>() {
    override val viewModel: ConfirmJoinPoolViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        val state = viewModel.viewState.collectAsState()
        ConfirmJoinPoolScreen(state = state.value, onNavigationClick = viewModel::onBackClick, onConfirm = viewModel::onConfirm)
    }
}
