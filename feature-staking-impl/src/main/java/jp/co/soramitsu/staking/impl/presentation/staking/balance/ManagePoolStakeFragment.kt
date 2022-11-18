package jp.co.soramitsu.staking.impl.presentation.staking.balance

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.staking.impl.presentation.staking.balance.compose.ManagePoolStakeScreen

@AndroidEntryPoint
class ManagePoolStakeFragment : BaseComposeBottomSheetDialogFragment<ManagePoolStakeViewModel>() {

    override val viewModel: ManagePoolStakeViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state = viewModel.state.collectAsState()
        ManagePoolStakeScreen(state.value, viewModel)
    }
}
