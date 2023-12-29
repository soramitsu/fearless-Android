package jp.co.soramitsu.walletconnect.impl.presentation.sessionproposal

import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class SessionProposalFragment : BaseComposeBottomSheetDialogFragment<SessionProposalViewModel>() {

    override val viewModel: SessionProposalViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        SessionProposalContent(
            state = state,
            callback = viewModel
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }

    companion object {
        const val PAIRING_TOPIC_KEY = "pairing_topic_key"
        fun getBundle(pairingTopic: String?) = bundleOf(PAIRING_TOPIC_KEY to pairingTopic)
    }
}
