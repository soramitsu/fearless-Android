package jp.co.soramitsu.wallet.impl.presentation.balance.networkissues.unavailable

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
class NetworkUnavailableFragment : BaseComposeBottomSheetDialogFragment<NetworkUnavailableViewModel>() {

    companion object {
        const val KEY_PAYLOAD = "payload"

        fun getBundle(chainName: String?) = bundleOf(KEY_PAYLOAD to chainName)
    }

    override val viewModel: NetworkUnavailableViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        NetworkUnavailableContent(
            state = state,
            onBackClicked = viewModel::back,
            onTopUpClicked = viewModel::topUp
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
