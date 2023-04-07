package jp.co.soramitsu.wallet.impl.presentation.cross_chain.wallet_type

import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class SelectWalletTypeFragment : BaseComposeBottomSheetDialogFragment<SelectWalletTypeViewModel>() {

    companion object {
        const val KEY_WALLET_TYPE = "wallet_type"
    }

    override val viewModel: SelectWalletTypeViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        SelectWalletTypeContent(
            callback = viewModel
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
