package jp.co.soramitsu.tonconnect.impl.presentation.tonconnectiondetails

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
import jp.co.soramitsu.tonconnect.api.model.AppEntity

@AndroidEntryPoint
class TonConnectionDetailsFragment : BaseComposeBottomSheetDialogFragment<TonConnectionDetailsViewModel>() {

    override val viewModel: TonConnectionDetailsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        TonConnectionDetailsContent(
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
        const val TON_CONNECTION_APP_KEY = "ton_connection_app_key"
        const val TON_PROOF_PAYLOAD_KEY = "ton_proof_payload_key"
        const val TON_CONNECT_RESULT_KEY = "ton_connect_result_key"

        fun getBundle(app: AppEntity?, proofPayload: String?) = bundleOf(
            TON_CONNECTION_APP_KEY to app,
            TON_PROOF_PAYLOAD_KEY to proofPayload
        )
    }
}
