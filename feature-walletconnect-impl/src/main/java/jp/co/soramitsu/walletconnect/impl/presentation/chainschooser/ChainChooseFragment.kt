package jp.co.soramitsu.walletconnect.impl.presentation.chainschooser

import android.content.DialogInterface
import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import co.jp.soramitsu.walletconnect.model.ChainChooseState
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class ChainChooseFragment : BaseComposeBottomSheetDialogFragment<ChainChooseViewModel>() {
    companion object {
        const val KEY_STATE_ID = "KEY_STATE_ID"
        const val RESULT = "chain_chooser_result"

        fun getBundle(state: ChainChooseState) = bundleOf(
            KEY_STATE_ID to state
        )
    }

    override val viewModel: ChainChooseViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        ChainSelectContent(
            state = state,
            onChainSelected = viewModel::onChainSelected,
            onSearchInput = viewModel::onSearchInput,
            onSelectAllClicked = viewModel::onSelectAllClicked,
            onDoneClicked = viewModel::onDoneClicked,
        )
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        viewModel.onDialogClose()
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = viewModel.state.value.isViewMode
        behavior.skipCollapsed = true
    }
}
