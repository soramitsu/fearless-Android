package jp.co.soramitsu.account.impl.presentation.optionsaddaccount

import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class OptionsAddAccountFragment : BaseComposeBottomSheetDialogFragment<OptionsAddAccountViewModel>() {

    companion object {
        const val KEY_PAYLOAD = "payload"

        fun getBundle(payload: AddAccountBottomSheet.Payload) = bundleOf(KEY_PAYLOAD to payload)
    }

    override val viewModel: OptionsAddAccountViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        OptionsAddAccountContent(
            state = state,
            onCreate = viewModel::createAccount,
            onImport = viewModel::importAccount,
            onNoNeed = viewModel::noNeedAccount
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
