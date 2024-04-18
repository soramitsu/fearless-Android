package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.sort

import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen

@AndroidEntryPoint
class AssetDetailsSortFragment : BaseComposeBottomSheetDialogFragment<AssetDetailsSortViewModel>() {

    override val viewModel: AssetDetailsSortViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state = viewModel.contentFlow.collectAsState()

        BottomSheetScreen {
            AssetDetailsSortContent(
                state = state.value,
                callback = viewModel
            )
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }

}