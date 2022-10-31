package jp.co.soramitsu.staking.impl.presentation.common.filters

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class ValidatorsSettingsFragment : BaseComposeBottomSheetDialogFragment<ValidatorsSettingsViewModel>() {
    override val viewModel: ValidatorsSettingsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.viewState.collectAsState()
        ValidatorsSettingsScreen(state = state, viewModel)
    }
}
