package jp.co.soramitsu.polkaswap.impl.presentation.disclaimer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class PolkaswapDisclaimerFragment : BaseComposeBottomSheetDialogFragment<PolkaswapDisclaimerViewModel>() {

    companion object {

        const val KEY_RESULT_DESTINATION= "KEY_RESULT_DESTINATION"
        const val KEY_DISCLAIMER_READ_RESULT= "KEY_DISCLAIMER_READ_RESULT"

        fun getBundle(resultDestinationScreenId: Int) = bundleOf(
                    KEY_RESULT_DESTINATION to resultDestinationScreenId
        )
    }

    override val viewModel: PolkaswapDisclaimerViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        PolkaswapDisclaimerScreen(state = state, callbacks = viewModel)
    }
}
