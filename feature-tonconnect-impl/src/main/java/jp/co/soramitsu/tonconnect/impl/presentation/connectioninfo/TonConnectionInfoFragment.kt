package jp.co.soramitsu.tonconnect.impl.presentation.connectioninfo

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

@AndroidEntryPoint
class TonConnectionInfoFragment : BaseComposeBottomSheetDialogFragment<TonConnectionInfoViewModel>() {

    override val viewModel: TonConnectionInfoViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        TonConnectionInfoContent(
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
        const val TON_CONNECTION_INFO_KEY = "ton_connection_info_key"

        fun getBundle(dappItem: DappModel) = bundleOf(
            TON_CONNECTION_INFO_KEY to dappItem
        )
    }
}
