package jp.co.soramitsu.account.impl.presentation.mnemonic_agreements

import android.os.Bundle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class MnemonicAgreementsDialog : BaseComposeBottomSheetDialogFragment<MnemonicAgreementsViewModel>() {

    override val viewModel: MnemonicAgreementsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        MnemonicAgreementsContent(
            state = state,
            callback = viewModel
        )
    }

    companion object {

        const val IS_FROM_GOOGLE_BACKUP_KEY = "IS_FROM_GOOGLE_BACKUP_KEY"
        const val WALLET_NAME_KEY = "ACCOUNT_NAME_KEY"

        fun getBundle(
            isFromGoogleBackupKey: Boolean,
            accountName: String
        ): Bundle {
            return bundleOf(
                IS_FROM_GOOGLE_BACKUP_KEY to isFromGoogleBackupKey,
                WALLET_NAME_KEY to accountName
            )
        }
    }
}
