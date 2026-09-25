package jp.co.soramitsu.polkaswap.impl.presentation.swap_preview

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsParcelModel
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState

@AndroidEntryPoint
class SwapPreviewFragment : BaseComposeFragment<SwapPreviewViewModel>() {

    companion object {

        const val KEY_SWAP_DETAILS = "KEY_SWAP_DETAILS"
        const val KEY_SWAP_DETAILS_PARCEL = "KEY_SWAP_DETAILS_PARCEL"
        const val KEY_SWAP_DETAILS_RESULT = "KEY_SWAP_DETAILS_RESULT"

        fun getBundle(swapDetailsViewState: SwapDetailsViewState, detailsParcelModel: SwapDetailsParcelModel) = bundleOf(
            KEY_SWAP_DETAILS to swapDetailsViewState,
            KEY_SWAP_DETAILS_PARCEL to detailsParcelModel
        )
    }

    override val viewModel: SwapPreviewViewModel by viewModels()

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        BottomSheetScreen {
            val state by viewModel.state.collectAsState()
            SwapPreviewContent(
                state = state,
                callbacks = viewModel
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = viewModel.onDismiss()
            }
        )
    }
}
