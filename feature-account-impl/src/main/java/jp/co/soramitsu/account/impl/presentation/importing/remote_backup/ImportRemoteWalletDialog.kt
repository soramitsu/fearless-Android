package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

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
import jp.co.soramitsu.oauth.base.extension.onBackPressed

@AndroidEntryPoint
class ImportRemoteWalletDialog : BaseComposeBottomSheetDialogFragment<ImportRemoteWalletViewModel>() {

    override val viewModel: ImportRemoteWalletViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        ImportRemoteWalletContent(
//            activity = requireActivity(),
            state = state,
            callback = viewModel
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }

    companion object {
        fun getBundle() = bundleOf()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onBackPressed { viewModel.backClicked() }
    }
}
