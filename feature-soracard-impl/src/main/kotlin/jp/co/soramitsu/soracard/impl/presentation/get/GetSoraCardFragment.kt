package jp.co.soramitsu.soracard.impl.presentation.get

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme

@AndroidEntryPoint
class GetSoraCardFragment : BaseComposeFragment<GetSoraCardViewModel>() {

    override val viewModel: GetSoraCardViewModel by viewModels()


    @ExperimentalMaterialApi
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
        val state by viewModel.state.collectAsState()

        FearlessAppTheme {
            GetSoraCardScreenWithToolbar(
                state = state,
                scrollState = scrollState,
                callbacks = viewModel
            )
        }
    }
}
