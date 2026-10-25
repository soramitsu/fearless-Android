package jp.co.soramitsu.account.impl.presentation.recovery

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

/** No stored record is read before the PIN round-trip or retained after a pause. */
@AndroidEntryPoint
class SavedEndpointsFragment : BaseComposeFragment<SavedEndpointsViewModel>() {
    override val viewModel: SavedEndpointsViewModel by viewModels()
    private var hadSecureFlag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hadSecureFlag = requireActivity().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumed()
    }

    override fun onPause() {
        viewModel.paused()
        super.onPause()
    }

    override fun onDestroy() {
        if (!hadSecureFlag) requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onDestroy()
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
        val state by viewModel.state.collectAsState()
        SavedEndpointsScreen(state, viewModel::back, viewModel::unlock, viewModel::refresh,
            viewModel::retry, viewModel::previous, viewModel::next, viewModel::inspect,
            viewModel::retryInspection, viewModel::previousInspection, viewModel::nextInspection, viewModel::returnToList,
            viewModel::selectSource, viewModel::inspectRecord, viewModel::retryRecordInspection,
            viewModel::previousRecordInspection, viewModel::nextRecordInspection,
            SavedEndpointDecisionActions(viewModel::editRecord, viewModel::retryDecisionLoad,
                viewModel::changeDecisionName, viewModel::changeDecisionUrl, viewModel::chooseDecisionTarget,
                viewModel::pageDecisionTargets, viewModel::retryDecisionTargets,
                viewModel::reloadDecisionAfterFailure, viewModel::saveDecision, viewModel::attachDecision, viewModel::restoreDecision,
                viewModel::selectEndpoint, viewModel::refreshSelection))
    }
}
