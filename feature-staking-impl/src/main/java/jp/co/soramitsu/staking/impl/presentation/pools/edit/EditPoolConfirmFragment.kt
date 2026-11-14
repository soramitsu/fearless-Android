package jp.co.soramitsu.staking.impl.presentation.pools.edit

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.ConfirmScreen
import jp.co.soramitsu.common.navigation.KeepSheetOpenOnBack

@AndroidEntryPoint
class EditPoolConfirmFragment : BaseComposeBottomSheetDialogFragment<EditPoolConfirmViewModel>() {
    override val viewModel: EditPoolConfirmViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.viewState.collectAsState()
        KeepSheetOpenOnBack(submitting = state.isLoading)
        ConfirmScreen(state = state, onNavigationClick = viewModel::onBackClick, viewModel::onConfirm)
    }
}
