package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.navigation.getNavigationResult
import jp.co.soramitsu.feature_staking_impl.R

@AndroidEntryPoint
class StartSelectValidatorsFragment : BaseComposeBottomSheetDialogFragment<StartSelectValidatorsViewModel>() {
    override val viewModel: StartSelectValidatorsViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        getNavigationResult<Result<Unit>>(R.id.startSelectValidatorsFragment,"result") {
            viewModel.onAlertResult(it)
        }
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        StartSelectValidatorsScreen(
            state = state,
            onRecommendedClick = viewModel::onRecommendedClick,
            onManualClick = viewModel::onManualClick,
            onBackClick = viewModel::onBackClick
        )
    }
}
