package jp.co.soramitsu.account.impl.presentation.mnemonic_agreements

import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.domain.model.AccountType
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

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }

    companion object {

        const val IS_FROM_GOOGLE_BACKUP_KEY = "IS_FROM_GOOGLE_BACKUP_KEY"
        const val WALLET_NAME_KEY = "ACCOUNT_NAME_KEY"
        const val ACCOUNT_TYPE_KEY = "ACCOUNT_TYPE_KEY"

        fun getBundle(
            isFromGoogleBackupKey: Boolean,
            accountName: String,
            accountType: AccountType
        ): Bundle {
            return bundleOf(
                IS_FROM_GOOGLE_BACKUP_KEY to isFromGoogleBackupKey,
                WALLET_NAME_KEY to accountName,
                ACCOUNT_TYPE_KEY to accountType
            )
        }
    }
}
