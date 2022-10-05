package jp.co.soramitsu.staking.impl.presentation.setup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class CreatePoolSetupFragment : BaseComposeBottomSheetDialogFragment<CreatePoolSetupViewModel>() {
    override val viewModel: CreatePoolSetupViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        TODO("Not yet implemented")
    }
}
