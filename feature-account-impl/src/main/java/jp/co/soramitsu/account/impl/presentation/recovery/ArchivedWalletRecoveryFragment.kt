package jp.co.soramitsu.account.impl.presentation.recovery

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
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
import jp.co.soramitsu.feature_account_impl.R

@AndroidEntryPoint
class ArchivedWalletRecoveryFragment : BaseComposeFragment<ArchivedWalletRecoveryViewModel>() {
    override val viewModel: ArchivedWalletRecoveryViewModel by viewModels()
    private var hadSecureFlag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hadSecureFlag = requireActivity().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() {
        viewModel.stopped()
        super.onStop()
    }

    override fun onDestroy() {
        if (!hadSecureFlag) requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onDestroy()
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
