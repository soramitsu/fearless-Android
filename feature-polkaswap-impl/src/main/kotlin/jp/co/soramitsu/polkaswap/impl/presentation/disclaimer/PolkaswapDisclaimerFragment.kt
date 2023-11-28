package jp.co.soramitsu.polkaswap.impl.presentation.disclaimer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.polkaswap.api.models.DisclaimerAppearanceSource

@AndroidEntryPoint
class PolkaswapDisclaimerFragment : BaseComposeBottomSheetDialogFragment<PolkaswapDisclaimerViewModel>() {

    companion object {

        const val KEY_NAVIGATION_SOURCE= "KEY_NAVIGATION_SOURCE"

        fun getBundle(disclaimerAppearanceSource: DisclaimerAppearanceSource) = bundleOf(
            KEY_NAVIGATION_SOURCE to disclaimerAppearanceSource
        )
    }

    override val viewModel: PolkaswapDisclaimerViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        PolkaswapDisclaimerScreen(state = state, callbacks = viewModel)
    }
}
