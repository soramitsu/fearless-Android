package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap

import android.content.Intent
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
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel

@AndroidEntryPoint
class SwapDetailFragment : BaseComposeBottomSheetDialogFragment<SwapDetailViewModel>() {

    companion object {
        const val CHOOSER_REQUEST_CODE = 103
        const val KEY_SWAP = "KEY_SWAP"

        fun getBundle(swap: OperationParcelizeModel.Swap) = bundleOf(KEY_SWAP to swap)
    }

    override val viewModel: SwapDetailViewModel by viewModels()

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeBrowserEvents(viewModel)

        viewModel.shareUrlEvent.observeEvent {
            shareUrl(it)
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }

    private fun shareUrl(url: String) {
        val title = getString(R.string.common_share)

        val intent = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, url)
            .setType("text/plain")

        val chooser = Intent.createChooser(intent, title)

        startActivityForResult(chooser, CHOOSER_REQUEST_CODE)
    }
}
