package jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen

class TransactionSettingsFragment: BaseComposeBottomSheetDialogFragment<TransactionSettingsViewModel>() {

    override val viewModel: TransactionSettingsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        BottomSheetScreen {

        }
    }
}
