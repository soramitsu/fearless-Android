package jp.co.soramitsu.account.impl.presentation.recovery

import android.content.Intent
import android.os.Bundle
import android.view.View
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
import jp.co.soramitsu.feature_account_impl.R

@AndroidEntryPoint
class ArchivedWalletRecoveryFragment : BaseComposeFragment<ArchivedWalletRecoveryViewModel>() {
    override val viewModel: ArchivedWalletRecoveryViewModel by viewModels()

    // Archived keys can be shown exactly as stored; keep them out of screen captures,
    // including while the PIN screen that opened this one is torn down.
    private val secureScreen = SecureScreenGuard(this)

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
            val intent = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, text)
            startActivity(Intent.createChooser(intent, getString(R.string.wallet_emergency_open)))
        }
    }
}
