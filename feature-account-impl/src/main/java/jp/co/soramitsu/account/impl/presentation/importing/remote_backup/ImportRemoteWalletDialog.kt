package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class ImportRemoteWalletDialog : BaseComposeBottomSheetDialogFragment<ImportRemoteWalletViewModel>() {

    override val viewModel: ImportRemoteWalletViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        ImportRemoteWalletContent(
            state = state,
            callback = viewModel
        )
    }

    companion object {
        fun getBundle() = bundleOf()
    }
}
