package jp.co.soramitsu.polkaswap.impl.presentation.select_market

import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.polkaswap.api.models.Market

@AndroidEntryPoint
class SelectMarketFragment : BaseComposeBottomSheetDialogFragment<SelectMarketViewModel>() {

    companion object {
        const val MARKET_KEY = "marketKey"
    }

    override val viewModel: SelectMarketViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        BottomSheetScreen {
            SelectMarketContent(marketSelected = viewModel::marketSelected)
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }
}
