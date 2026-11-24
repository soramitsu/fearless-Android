package jp.co.soramitsu.account.impl.presentation.recovery

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.impl.presentation.common.SecureScreenGuard
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.resources.SensitiveClipboard
import jp.co.soramitsu.feature_account_impl.R

@AndroidEntryPoint
class ArchivedWalletRecoveryFragment : BaseComposeFragment<ArchivedWalletRecoveryViewModel>() {

    override val secureSurface = true
    override val viewModel: ArchivedWalletRecoveryViewModel by viewModels()

    // Archived keys can be shown exactly as stored; keep them out of screen captures,
    // including while the PIN screen that opened this one is torn down.
    private val secureScreen = SecureScreenGuard(this, warnAboutScreenReaders = true)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        secureScreen.onViewCreated()
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        secureScreen.onStart()
    }

    override fun onStop() {
        viewModel.stopped()
        secureScreen.onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        secureScreen.onDestroyView()
        super.onDestroyView()
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
        val state by viewModel.state.collectAsState()
        ArchivedWalletRecoveryScreen(state, viewModel::back, viewModel::reload, viewModel::unlock,
            viewModel::restore, viewModel::purge, viewModel::confirm, viewModel::cancelConfirmation,
            viewModel::export, viewModel::closeExport) { text ->
            // Never handed to another app as plain text: copied as sensitive and cleared after a minute.
            SensitiveClipboard.get(requireContext()).copy(getString(R.string.wallet_archive_exact_data), text)
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
