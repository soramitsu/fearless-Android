package jp.co.soramitsu.staking.impl.presentation.pools

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolInfoOptionsScreen

@AndroidEntryPoint
class PoolInfoOptionsFragment : BaseComposeBottomSheetDialogFragment<PoolInfoOptionsViewModel>() {

    companion object {
        const val POOL_INFO_KEY = "poolInfoKey"
        fun getBundle(poolInfo: PoolInfo) = bundleOf(POOL_INFO_KEY to poolInfo)
    }

    override val viewModel: PoolInfoOptionsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        PoolInfoOptionsScreen(state = viewModel.state, onSelected = viewModel::onOptionSelected)
    }
}
