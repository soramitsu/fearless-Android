package jp.co.soramitsu.staking.impl.presentation.pools.edit

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class EditPoolFragment : BaseComposeBottomSheetDialogFragment<EditPoolViewModel>() {
    override val viewModel: EditPoolViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        EditPoolScreen(state = state, screenInterface = viewModel)
    }
}
