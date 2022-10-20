package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class SelectRecommendedValidatorsFragment : BaseComposeBottomSheetDialogFragment<SelectRecommendedValidatorsViewModel>() {
    override val viewModel: SelectRecommendedValidatorsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        TODO("Not yet implemented")
    }
}
