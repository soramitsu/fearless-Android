package jp.co.soramitsu.staking.impl.presentation.validators.compose

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class SelectValidatorsFragment : BaseComposeBottomSheetDialogFragment<SelectValidatorsViewModel>() {
    override val viewModel: SelectValidatorsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        SelectValidatorsScreen(
            state = state,
            viewModel
        )
    }

    override fun onPause() {
        Log.d("&&&", "SelectValidatorsFragment onPause")
        super.onPause()
    }

    override fun onDestroy() {
        Log.d("&&&", "SelectValidatorsFragment onDestroy")
        super.onDestroy()
    }
}
