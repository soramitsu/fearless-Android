package jp.co.soramitsu.wallet.impl.presentation.addressbook

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
class CreateContactFragment : BaseComposeBottomSheetDialogFragment<CreateContactViewModel>() {

    companion object {
        const val KEY_CHAIN_ID = "chain_id"
        const val KEY_PAYLOAD = "payload"

        fun getBundle(chainId: ChainId?, address: String?) = bundleOf(
            KEY_CHAIN_ID to chainId,
            KEY_PAYLOAD to address
        )
    }

    override val viewModel: CreateContactViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        CreateContactContent(
            state = state,
            callback = viewModel
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }
}
