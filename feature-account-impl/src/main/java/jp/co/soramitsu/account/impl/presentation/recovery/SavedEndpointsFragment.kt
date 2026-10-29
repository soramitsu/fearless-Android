package jp.co.soramitsu.account.impl.presentation.recovery

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

/** No stored record is read before the PIN round-trip or retained after a pause. */
@AndroidEntryPoint
class SavedEndpointsFragment : BaseComposeFragment<SavedEndpointsViewModel>() {
    override val viewModel: SavedEndpointsViewModel by viewModels()

    // Saved endpoint records can carry credentials; keep them out of screen captures,
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

    override fun onResume() {
        super.onResume()
        viewModel.resumed()
    }

    override fun onPause() {
        viewModel.paused()
        super.onPause()
    }

    override fun onStop() {
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
