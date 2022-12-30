package jp.co.soramitsu.polkaswap.impl.presentation.swap_preview

import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetails

@AndroidEntryPoint
class SwapPreviewFragment : BaseComposeBottomSheetDialogFragment<SwapPreviewViewModel>() {

    companion object {

        const val KEY_SWAP_DETAILS = "KEY_SWAP_DETAILS"

        fun getBundle(swapDetails: SwapDetails) = bundleOf(
            KEY_SWAP_DETAILS to swapDetails
        )
    }

    override val viewModel: SwapPreviewViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        BottomSheetScreen {
            val state by viewModel.state.collectAsState()
            SwapPreviewContent(
                state = state,
                callbacks = viewModel
            )
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }
}
