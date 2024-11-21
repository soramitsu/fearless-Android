package jp.co.soramitsu.tonconnect.impl.presentation.tonsignpreview

import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import co.jp.soramitsu.tonconnect.model.DappModel
import co.jp.soramitsu.tonconnect.model.SignRequestEntity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class TonSignPreviewFragment : BaseComposeBottomSheetDialogFragment<TonSignPreviewViewModel>() {

    override val viewModel: TonSignPreviewViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        TonSignPreviewContent(
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
        const val PAYLOAD_DAPP_KEY = "payload_dapp_key"
        const val METHOD_KEY = "method_key"
        const val TON_SIGN_REQUEST_KEY = "ton_sign_request_key"
        fun getBundle(dapp: DappModel, method: String, request: SignRequestEntity) = bundleOf(
            PAYLOAD_DAPP_KEY to dapp,
            METHOD_KEY to method,
            TON_SIGN_REQUEST_KEY to request
        )
    }
}
