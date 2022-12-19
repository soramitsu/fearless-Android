package jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen

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

const val FROZEN_ASSET_PAYLOAD = "FROZEN_ASSET_PAYLOAD"

@AndroidEntryPoint
class FrozenTokensFragment : BaseComposeBottomSheetDialogFragment<FrozenTokensViewModel>() {

    companion object {
        fun getBundle(
            frozenAssetPayload: FrozenAssetPayload
        ) = bundleOf(FROZEN_ASSET_PAYLOAD to frozenAssetPayload)
    }

    override val viewModel: FrozenTokensViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        FrozenTokensContent(state = state)
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
