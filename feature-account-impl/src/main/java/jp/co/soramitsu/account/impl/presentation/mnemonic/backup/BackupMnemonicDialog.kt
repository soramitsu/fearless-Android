package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

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
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet

@AndroidEntryPoint
class BackupMnemonicDialog : BaseComposeBottomSheetDialogFragment<BackupMnemonicViewModel>() {

    override val viewModel: BackupMnemonicViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        viewModel.encryptionTypeChooserEvent.observeEvent(::showEncryptionChooser)

        BottomSheetScreen {
            BackupMnemonicContent(
                state = state,
                callback = viewModel
            )
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }

    private fun showEncryptionChooser(payload: DynamicListBottomSheet.Payload<CryptoTypeModel>) {
        EncryptionTypeChooserBottomSheetDialog(
            requireActivity(),
            payload,
            viewModel.selectedEncryptionTypeLiveData::setValue
        ).show()
    }

    companion object {
        fun getBundle(
            isFromGoogleBackup: Boolean,
            accountName: String
        ): Bundle {
            return bundleOf(
                BackupMnemonicScreenKeys.PAYLOAD_KEY to BackupMnemonicPayload(isFromGoogleBackup, accountName, null)
            )
        }
    }
}
