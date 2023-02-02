package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import android.os.Bundle
import android.view.View
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
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.presentation.InfoDialog
import jp.co.soramitsu.feature_polkaswap_impl.R

@AndroidEntryPoint
class SwapTokensFragment : BaseComposeBottomSheetDialogFragment<SwapTokensViewModel>() {

    companion object {

        const val KEY_SELECTED_ASSET_ID = "KEY_SELECTED_ASSET_ID"
        const val KEY_SELECTED_CHAIN_ID = "KEY_SELECTED_CHAIN_ID"

        fun getBundle(selectedAssetId: String, selectedChainId: String) = bundleOf(
            KEY_SELECTED_ASSET_ID to selectedAssetId,
            KEY_SELECTED_CHAIN_ID to selectedChainId
        )
    }

    override val viewModel: SwapTokensViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        BottomSheetScreen {
            val state by viewModel.state.collectAsState()
            SwapTokensContent(
                state = state,
                callbacks = viewModel
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.showMarketsWarningEvent.observeEvent {
            val res = requireContext()
            ErrorDialog(
                title = res.getString(R.string.polkaswap_market_alert_title),
                message = res.getString(R.string.polkaswap_market_alert_message),
                positiveButtonText = res.getString(R.string.polkaswap_market_alert_positive_button),
                positiveClick = { viewModel.marketAlertConfirmed() },
                negativeButtonText = res.getString(R.string.common_cancel)
            ).show(childFragmentManager)
        }

        viewModel.showTooltipEvent.observeEvent {
            InfoDialog(
                title = it.first,
                message = it.second
            ).show(childFragmentManager)
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }
}
