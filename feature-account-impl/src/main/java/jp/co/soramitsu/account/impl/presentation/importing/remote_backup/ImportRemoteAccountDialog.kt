package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class ImportRemoteAccountDialog : BaseComposeBottomSheetDialogFragment<ImportRemoteAccountViewModel>() {

    override val viewModel: ImportRemoteAccountViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        ImportRemoteAccountContent(callback = viewModel)
    }

    companion object {
        fun getBundle() = bundleOf()
    }
}
