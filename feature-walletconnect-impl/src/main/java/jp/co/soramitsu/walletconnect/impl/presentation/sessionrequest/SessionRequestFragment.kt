package jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest

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
class SessionRequestFragment : BaseComposeBottomSheetDialogFragment<SessionRequestViewModel>() {

    override val viewModel: SessionRequestViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        SessionRequestContent(
            state = state,
            callback = viewModel
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = false
        behavior.skipCollapsed = true
    }


    companion object {
        const val SESSION_REQUEST_TOPIC_KEY = "session_request_topic_key"
        fun getBundle(topic: String) = bundleOf(SESSION_REQUEST_TOPIC_KEY to topic)
    }
}
