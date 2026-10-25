package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment

@AndroidEntryPoint
class RecoveryCenterFragment : BaseComposeFragment<RecoveryCenterViewModel>() {
    override val viewModel: RecoveryCenterViewModel by viewModels()

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
        val state by viewModel.state.collectAsState()
        val pinHealth by jp.co.soramitsu.common.data.storage.encrypt.WalletPinHealth.health.collectAsState()
        RecoveryCenterScreen(
            state, viewModel::back, viewModel::restore, viewModel::importCorrected,
            viewModel::exportStoredData, viewModel::backupWallet,
            archivedData = viewModel::archivedData,
            pinUnreadable = pinHealth == jp.co.soramitsu.common.data.storage.encrypt.PinHealth.UNREADABLE,
            resetPin = viewModel::resetPin,
            previousSecureStorage = viewModel::retryPreviousStorage,
            savedEndpoints = viewModel::savedEndpoints
        )
    }
}
