package jp.co.soramitsu.account.impl.presentation.options_switch_node

import android.os.Bundle
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
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

@AndroidEntryPoint
class OptionsSwitchNodeFragment : BaseComposeBottomSheetDialogFragment<OptionsSwitchNodeViewModel>() {

    companion object {
        const val KEY_META_ID = "metaId"
        const val KEY_CHAIN_ID = "chainId"
        const val KEY_CHAIN_NAME = "chainName"

        fun getBundle(
            metaId: Long,
            chainId: ChainId,
            chainName: String
        ): Bundle {
            return bundleOf(
                KEY_META_ID to metaId,
                KEY_CHAIN_ID to chainId,
                KEY_CHAIN_NAME to chainName
            )
        }
    }

    override val viewModel: OptionsSwitchNodeViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        OptionsSwitchNodeContent(
            state = state,
            onSwitch = viewModel::onSwitch,
            dontShowAgain = viewModel::dontShowAgainClicked,
            onBackClicked = viewModel::onBackClicked
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
