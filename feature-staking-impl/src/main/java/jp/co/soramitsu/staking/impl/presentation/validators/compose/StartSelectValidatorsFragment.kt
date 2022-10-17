package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class StartSelectValidatorsFragment : BaseComposeBottomSheetDialogFragment<StartSelectValidatorsViewModel>() {
    override val viewModel: StartSelectValidatorsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
//        StartSelectValidatorsScreen(state =, onRecommendedClick = { /*TODO*/ }, onManualClick = { /*TODO*/ }) {
//
//        }
    }
}
