package jp.co.soramitsu.wallet.impl.presentation.balance.networkissues

import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class NetworkIssuesFragment : BaseComposeBottomSheetDialogFragment<NetworkIssuesViewModel>() {
    override val viewModel: NetworkIssuesViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        NetworkIssuesScreen(
            state = state,
            onIssueClicked = viewModel::onIssueClicked,
            onBackClicked = viewModel::onBackClicked
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
