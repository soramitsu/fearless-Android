package jp.co.soramitsu.account.impl.presentation.optionsaddaccount

import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.model.ImportAccountType
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class OptionsAddAccountFragment : BaseComposeBottomSheetDialogFragment<OptionsAddAccountViewModel>() {

    companion object {
        const val KEY_WALLET_ID = "KEY_WALLET_ID"
        const val KEY_TYPE = "KEY_TYPE"

        fun getBundle(walletId: Long, type: ImportAccountType) = bundleOf(
            KEY_WALLET_ID to walletId,
            KEY_TYPE to type
        )
    }

    override val viewModel: OptionsAddAccountViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        OptionsAddAccountContent(
            onCreate = viewModel::createAccount,
            onImport = viewModel::importAccount,
            onBackClicked = viewModel::onBackClicked
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
