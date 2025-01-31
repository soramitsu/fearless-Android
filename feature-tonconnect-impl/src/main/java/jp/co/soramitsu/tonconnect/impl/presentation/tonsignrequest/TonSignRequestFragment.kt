package jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest

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
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest

@AndroidEntryPoint
class TonSignRequestFragment : BaseComposeBottomSheetDialogFragment<TonSignRequestViewModel>() {

    override val viewModel: TonSignRequestViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        TonSignRequestFlow(
            state = state,
            callback = viewModel,
            previewCallback = viewModel
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = false
        behavior.skipCollapsed = true
    }

    companion object {
        const val DAPP_KEY = "dapp_key"
        const val METHOD_KEY = "method_key"
        const val TON_SIGN_REQUEST_KEY = "ton_sign_request_key"

        fun getBundle(
            dapp: DappModel,
            method: String,
            request: TonConnectSignRequest
        ) = bundleOf(
            DAPP_KEY to dapp,
            METHOD_KEY to method,
            TON_SIGN_REQUEST_KEY to request
        )
    }
}
