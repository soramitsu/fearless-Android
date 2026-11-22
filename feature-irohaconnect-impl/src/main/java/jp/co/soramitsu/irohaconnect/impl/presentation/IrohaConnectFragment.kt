package jp.co.soramitsu.irohaconnect.impl.presentation

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
class IrohaConnectFragment : BaseComposeBottomSheetDialogFragment<IrohaConnectViewModel>() {

    override val viewModel: IrohaConnectViewModel by viewModels()

    override val secureSurface = true

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        IrohaConnectContent(state = state, callbacks = viewModel)
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }

    companion object {
        const val KEY_LINK = "iroha_connect_link"

        fun getBundle(link: String?) = bundleOf(KEY_LINK to link)
    }
}
