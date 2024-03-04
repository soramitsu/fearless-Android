package jp.co.soramitsu.wallet.impl.presentation.balance.detail.claimreward

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

@AndroidEntryPoint
class ClaimRewardsFragment : BaseComposeBottomSheetDialogFragment<ClaimRewardsViewModel>() {

    companion object {
        const val KEY_CHAIN_ID = "KEY_CHAIN_ID"

        fun getBundle(chainId: ChainId) = bundleOf(
            KEY_CHAIN_ID to chainId
        )
    }

    override val viewModel: ClaimRewardsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        ClaimRewardsContent(
            state = state,
            callback = viewModel
        )
    }
}
