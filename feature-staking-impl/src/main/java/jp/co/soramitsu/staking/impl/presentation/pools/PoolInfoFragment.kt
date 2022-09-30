package jp.co.soramitsu.staking.impl.presentation.pools

import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolInfoScreen

@AndroidEntryPoint
class PoolInfoFragment : BaseComposeBottomSheetDialogFragment<PoolInfoViewModel>() {

    companion object {
        const val POOL_INFO_KEY = "poolInfo"
        fun getBundle(poolInfo: PoolInfo) = Bundle().apply {
            putParcelable(POOL_INFO_KEY, poolInfo)
        }
    }

    override val viewModel: PoolInfoViewModel by viewModels()

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = true
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val state = viewModel.state.collectAsState()
        PoolInfoScreen(state = state.value, viewModel::onCloseClick)
    }
}
