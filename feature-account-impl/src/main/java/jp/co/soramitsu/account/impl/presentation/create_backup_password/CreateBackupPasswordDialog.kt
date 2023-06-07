package jp.co.soramitsu.account.impl.presentation.create_backup_password

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class CreateBackupPasswordDialog : BaseComposeBottomSheetDialogFragment<CreateBackupPasswordViewModel>() {

    override val viewModel: CreateBackupPasswordViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        CreateBackupPasswordContent(
            state = state,
            callback = viewModel
        )
    }

    companion object {
        fun getBundle() = bundleOf()
    }
}
