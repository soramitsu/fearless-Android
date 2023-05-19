package jp.co.soramitsu.soracard.impl.presentation.getmorexor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class GetMoreXorFragment : BaseComposeBottomSheetDialogFragment<GetMoreXorViewModel>() {

    override val viewModel: GetMoreXorViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        GetMoreXorContent(
            callback = viewModel
        )
    }
}
