package jp.co.soramitsu.soracard.impl.presentation.buycrypto

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment

@AndroidEntryPoint
class BuyCryptoFragment : BaseComposeFragment<BuyCryptoViewModel>() {

    override val viewModel: BuyCryptoViewModel by viewModels()

    @ExperimentalMaterialApi
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
        BuyCryptoScreen(
            state = viewModel.state,
            onPageFinished = viewModel::onPageFinished
        )
    }
}
