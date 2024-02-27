package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.select.compose

import android.os.Bundle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.validators.compose.SelectValidatorsScreen

@AndroidEntryPoint
class SelectCustomValidatorsFragment :
    BaseComposeBottomSheetDialogFragment<SelectCustomValidatorsViewModel>() {

    companion object {
        fun getBundle(mode: SelectValidatorFlowState.ValidatorSelectMode): Bundle {
            return bundleOf("validatorsSelectMode" to mode)
        }
    }

    override val viewModel: SelectCustomValidatorsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        SelectValidatorsScreen(
            state = state,
            viewModel
        )
    }
}
